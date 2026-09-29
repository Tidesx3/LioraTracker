package app.liora.core.data.workout

import app.liora.core.common.IdGenerator
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.database.model.WorkoutSetEntity
import app.liora.core.model.ActiveWorkout
import app.liora.core.model.LoggedSet
import app.liora.core.model.Routine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Owns the single in-progress workout. It lives in the database from the first tap, so it survives
 * the app being killed, the phone rebooting or the battery dying mid-session. Editing its exercises
 * and logging sets go through [WorkoutEditor] and [SetLogger].
 */
interface ActiveWorkoutRepository {
    /** The workout in progress with its exercises and sets, or null. */
    val activeWorkout: Flow<ActiveWorkout?>

    /** Last session's completed sets for each exercise of the workout in progress, by exercise id. */
    val previousSets: Flow<Map<String, List<LoggedSet>>>

    /** Starts an empty workout, or returns the one already in progress. */
    suspend fun startEmptyWorkout(): ActiveWorkout

    /**
     * Starts a workout from a routine: its exercises, supersets, rest times and planned sets, with the
     * routine's targets prefilled. Returns the workout already in progress instead, if there is one.
     */
    suspend fun startFromRoutine(routineId: String): ActiveWorkout

    /** Names the workout; blank goes back to the default name. */
    suspend fun rename(name: String)

    suspend fun finish()

    suspend fun discard()
}

internal class OfflineActiveWorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val routines: RoutineRepository,
    private val restTimer: RestTimerRepository,
    private val transactions: TransactionRunner,
    private val ids: IdGenerator,
    private val stamper: SyncStamper,
) : ActiveWorkoutRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val activeWorkout: Flow<ActiveWorkout?> =
        workoutDao
            .observeActive()
            .flatMapLatest { workout ->
                if (workout == null) {
                    flowOf(null)
                } else {
                    combine(
                        workoutDao.observeExercises(workout.id),
                        workoutDao.observeSets(workout.id),
                    ) { exercises, sets ->
                        workout.toActiveWorkout(exercises, sets)
                    }
                }
            }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val previousSets: Flow<Map<String, List<LoggedSet>>> =
        activeWorkout
            .map { workout ->
                workout
                    ?.exercises
                    ?.map { it.exerciseId }
                    ?.distinct()
                    ?.sorted()
                    .orEmpty()
            }.distinctUntilChanged()
            .flatMapLatest { exerciseIds ->
                if (exerciseIds.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    workoutDao.observeLastSessionSets(exerciseIds).map { rows ->
                        rows.groupBy({ it.exerciseId }, { it.set.toLoggedSet() })
                    }
                }
            }.distinctUntilChanged()

    override suspend fun startEmptyWorkout(): ActiveWorkout =
        transactions.inTransaction {
            workoutDao.activeRows()?.toModel() ?: newWorkout(name = null, routineId = null).toActiveWorkout()
        }

    override suspend fun startFromRoutine(routineId: String): ActiveWorkout {
        val routine = requireNotNull(routines.get(routineId)) { "No routine $routineId" }
        return transactions.inTransaction {
            workoutDao.activeRows()?.toModel() ?: run {
                val workout = newWorkout(name = routine.name, routineId = routine.id)
                val (exercises, sets) = plannedRows(workout.id, routine)
                workoutDao.upsertExercises(exercises)
                workoutDao.upsertSets(sets)
                workout.toActiveWorkout(exercises, sets)
            }
        }
    }

    override suspend fun rename(name: String) {
        transactions.inTransaction {
            val workout = workoutDao.getActive() ?: return@inTransaction
            val trimmed = name.trim().ifEmpty { null }
            if (trimmed !=
                workout.name
            ) {
                workoutDao.upsert(workout.copy(name = trimmed, sync = stamper.touch(workout.sync)))
            }
        }
    }

    override suspend fun finish() {
        transactions.inTransaction {
            val workout = workoutDao.getActive() ?: return@inTransaction
            workoutDao.upsert(workout.copy(endedAt = stamper.nowMillis(), sync = stamper.touch(workout.sync)))
            restTimer.stop()
        }
    }

    override suspend fun discard() {
        transactions.inTransaction {
            val workout = workoutDao.getActive() ?: return@inTransaction
            workoutDao.upsert(workout.copy(sync = stamper.tombstone(workout.sync)))
            restTimer.stop()
        }
    }

    private suspend fun newWorkout(
        name: String?,
        routineId: String?,
    ): WorkoutEntity {
        val sync = stamper.newRow()
        val workout =
            WorkoutEntity(
                id = ids.newId(),
                name = name,
                startedAt = sync.createdAt,
                endedAt = null,
                routineId = routineId,
                notes = null,
                bodyweightKg = null,
                sync = sync,
            )
        workoutDao.upsert(workout)
        return workout
    }

    /** The routine as a plan: sets not yet ticked off, carrying the routine's targets. */
    private suspend fun plannedRows(
        workoutId: String,
        routine: Routine,
    ): Pair<List<WorkoutExerciseEntity>, List<WorkoutSetEntity>> {
        val exercises = mutableListOf<WorkoutExerciseEntity>()
        val sets = mutableListOf<WorkoutSetEntity>()
        routine.exercises.forEachIndexed { position, planned ->
            val exercise =
                WorkoutExerciseEntity(
                    id = ids.newId(),
                    workoutId = workoutId,
                    exerciseId = planned.exerciseId,
                    position = position,
                    supersetGroup = planned.supersetGroup,
                    restSeconds = planned.restSeconds,
                    notes = planned.notes,
                    sync = stamper.newRow(),
                )
            exercises += exercise
            planned.sets.forEachIndexed { index, set ->
                sets +=
                    WorkoutSetEntity(
                        id = ids.newId(),
                        workoutExerciseId = exercise.id,
                        position = index,
                        setType = set.type,
                        weightKg = set.weight?.kilograms,
                        // An exact target is the plan; a range stays a hint until the set is logged.
                        reps = set.reps?.takeUnless { it.isRange }?.min,
                        durationSeconds = set.duration?.inWholeSeconds?.toInt(),
                        distanceMeters = set.distanceMeters,
                        rpe = null,
                        completedAt = null,
                        targetRepsMin = set.reps?.min,
                        targetRepsMax = set.reps?.max,
                        sync = stamper.newRow(),
                    )
            }
        }
        return exercises to sets
    }
}
