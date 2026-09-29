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
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.WorkoutExercise
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Owns the single in-progress workout. It lives in the database from the first tap, so it survives
 * the app being killed, the phone rebooting or the battery dying mid-session.
 */
interface ActiveWorkoutRepository {
    /** The workout in progress with its exercises and sets, or null. */
    val activeWorkout: Flow<ActiveWorkout?>

    /** Starts an empty workout, or returns the one already in progress. */
    suspend fun startEmptyWorkout(): ActiveWorkout

    /**
     * Starts a workout from a routine: its exercises, supersets, rest times and planned sets, with the
     * routine's targets prefilled. Returns the workout already in progress instead, if there is one.
     */
    suspend fun startFromRoutine(routineId: String): ActiveWorkout

    suspend fun finish()

    suspend fun discard()
}

internal class OfflineActiveWorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val routines: RoutineRepository,
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

    override suspend fun startEmptyWorkout(): ActiveWorkout =
        transactions.inTransaction {
            workoutDao.getActive()?.toActiveWorkout() ?: newWorkout(name = null, routineId = null).toActiveWorkout()
        }

    override suspend fun startFromRoutine(routineId: String): ActiveWorkout {
        val routine = requireNotNull(routines.get(routineId)) { "No routine $routineId" }
        return transactions.inTransaction {
            workoutDao.getActive()?.toActiveWorkout() ?: run {
                val workout = newWorkout(name = routine.name, routineId = routine.id)
                val (exercises, sets) = plannedRows(workout.id, routine)
                workoutDao.upsertExercises(exercises)
                workoutDao.upsertSets(sets)
                workout.toActiveWorkout(exercises, sets)
            }
        }
    }

    override suspend fun finish() {
        transactions.inTransaction {
            val workout = workoutDao.getActive() ?: return@inTransaction
            workoutDao.upsert(workout.copy(endedAt = stamper.nowMillis(), sync = stamper.touch(workout.sync)))
        }
    }

    override suspend fun discard() {
        transactions.inTransaction {
            val workout = workoutDao.getActive() ?: return@inTransaction
            workoutDao.upsert(workout.copy(sync = stamper.tombstone(workout.sync)))
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

private fun WorkoutEntity.toActiveWorkout(
    exercises: List<WorkoutExerciseEntity> = emptyList(),
    sets: List<WorkoutSetEntity> = emptyList(),
): ActiveWorkout {
    val setsByExercise = sets.groupBy { it.workoutExerciseId }
    return ActiveWorkout(
        id = id,
        name = name,
        startedAt = Instant.fromEpochMilliseconds(startedAt),
        routineId = routineId,
        exercises =
            exercises.sortedBy { it.position }.map { exercise ->
                WorkoutExercise(
                    id = exercise.id,
                    exerciseId = exercise.exerciseId,
                    supersetGroup = exercise.supersetGroup,
                    restSeconds = exercise.restSeconds,
                    notes = exercise.notes,
                    sets = setsByExercise[exercise.id].orEmpty().sortedBy { it.position }.map { it.toLoggedSet() },
                )
            },
    )
}

private fun WorkoutSetEntity.toLoggedSet() =
    LoggedSet(
        id = id,
        type = setType,
        weight = weightKg?.let(::Mass),
        reps = reps,
        duration = durationSeconds?.seconds,
        distanceMeters = distanceMeters,
        rpe = rpe,
        completedAt = completedAt?.let(Instant::fromEpochMilliseconds),
        targetReps = RepRange.of(targetRepsMin, targetRepsMax),
    )
