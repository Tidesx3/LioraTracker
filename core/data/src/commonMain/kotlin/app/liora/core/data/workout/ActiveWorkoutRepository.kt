package app.liora.core.data.workout

import app.liora.core.common.IdGenerator
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.ExerciseSetRow
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.database.model.WorkoutSetEntity
import app.liora.core.domain.RoutineUpdate
import app.liora.core.domain.Supersets
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

    /** Every earlier completed set of the exercises in the workout in progress, by exercise id. */
    val exerciseHistory: Flow<Map<String, List<LoggedSet>>>

    /** Starts an empty workout, or returns the one already in progress. */
    suspend fun startEmptyWorkout(): ActiveWorkout

    /**
     * Starts a workout from a routine: its exercises, supersets, rest times and planned sets, with the
     * routine's targets prefilled. Returns the workout already in progress instead, if there is one.
     */
    suspend fun startFromRoutine(routineId: String): ActiveWorkout

    /** Names the workout; blank goes back to the default name. */
    suspend fun rename(name: String)

    /**
     * Ends the workout. Planned sets that weren't done, and exercises left without any, are dropped:
     * history keeps what happened. With [updateRoutine], the routine it was started from takes on
     * today's exercises, sets and weights.
     */
    suspend fun finish(updateRoutine: Boolean = false)

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

    override val previousSets: Flow<Map<String, List<LoggedSet>>> =
        setsOfActiveExercises(workoutDao::observeLastSessionSets)

    override val exerciseHistory: Flow<Map<String, List<LoggedSet>>> =
        setsOfActiveExercises(workoutDao::observeHistorySets)

    /** Sets from [query] for the exercises of the workout in progress, re-queried as exercises come and go. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setsOfActiveExercises(
        query: (List<String>) -> Flow<List<ExerciseSetRow>>,
    ): Flow<Map<String, List<LoggedSet>>> =
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
                    query(exerciseIds).map { rows -> rows.groupBy({ it.exerciseId }, { it.set.toLoggedSet() }) }
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

    override suspend fun finish(updateRoutine: Boolean) {
        val workout = workoutDao.activeRows()?.toModel() ?: return
        val routine = workout.routineId?.takeIf { updateRoutine }?.let { routines.get(it) }
        transactions.inTransaction {
            val rows = workoutDao.activeRows() ?: return@inTransaction
            dropWhatWasntDone(rows)
            workoutDao.upsert(rows.workout.copy(endedAt = stamper.nowMillis(), sync = stamper.touch(rows.workout.sync)))
            restTimer.stop()
        }
        routine?.let { routines.save(RoutineUpdate.fromWorkout(it, workout, ids::newId)) }
    }

    /** Tombstones planned sets that weren't done and exercises left empty, repairing supersets they split. */
    private suspend fun dropWhatWasntDone(rows: ActiveRows) {
        val open = rows.sets.filter { it.completedAt == null }
        if (open.isNotEmpty()) workoutDao.upsertSets(open.map { it.copy(sync = stamper.tombstone(it.sync)) })
        val withSets =
            rows.sets
                .filter { it.completedAt != null }
                .map { it.workoutExerciseId }
                .toSet()
        val kept = rows.exercises.sortedBy { it.position }.filter { it.id in withSets }
        val groups = Supersets.normalize(kept.map { it.supersetGroup })
        workoutDao.saveExercises(
            rows.exercises,
            kept.zip(groups) { exercise, group -> exercise.copy(supersetGroup = group) },
            stamper,
        )
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
