package app.liora.core.data.workout

import app.liora.core.common.IdGenerator
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.database.model.WorkoutSetEntity
import app.liora.core.domain.RoutineUpdate
import app.liora.core.model.FinishedWorkout
import app.liora.core.model.Routine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Instant

/**
 * Finished workouts: what history lists and shows. Starting one again goes through
 * [ActiveWorkoutRepository.repeat].
 */
interface WorkoutHistoryRepository {
    /** Every finished workout with its exercises and sets, newest first. */
    val workouts: Flow<List<FinishedWorkout>>

    /** Removes a finished workout from history, with its exercises and sets. */
    suspend fun delete(workoutId: String)

    /**
     * A new routine from what was done in the workout: its exercises, supersets, rest times and sets,
     * with that day's weights. Returns the routine's id, or null if the workout is gone.
     */
    suspend fun saveAsRoutine(
        workoutId: String,
        name: String,
    ): String?
}

internal class OfflineWorkoutHistoryRepository(
    private val workoutDao: WorkoutDao,
    private val routines: RoutineRepository,
    private val transactions: TransactionRunner,
    private val ids: IdGenerator,
    private val stamper: SyncStamper,
) : WorkoutHistoryRepository {
    override val workouts: Flow<List<FinishedWorkout>> =
        combine(
            workoutDao.observeFinished(),
            workoutDao.observeFinishedExercises(),
            workoutDao.observeFinishedSets(),
        ) { workouts, exercises, sets ->
            val exercisesByWorkout = exercises.groupBy { it.workoutId }
            val setsByExercise = sets.groupBy { it.workoutExerciseId }
            workouts.map { it.toFinishedWorkout(exercisesByWorkout[it.id].orEmpty(), setsByExercise) }
        }.distinctUntilChanged()

    // Synced rows are never hard-deleted, and nothing cascades by itself: tombstone the whole tree.
    override suspend fun delete(workoutId: String) {
        transactions.inTransaction {
            val rows = workoutDao.finishedRows(workoutId) ?: return@inTransaction
            if (rows.sets.isNotEmpty()) {
                workoutDao.upsertSets(
                    rows.sets.map { it.copy(sync = stamper.tombstone(it.sync)) },
                )
            }
            if (rows.exercises.isNotEmpty()) {
                workoutDao.upsertExercises(rows.exercises.map { it.copy(sync = stamper.tombstone(it.sync)) })
            }
            workoutDao.upsert(rows.workout.copy(sync = stamper.tombstone(rows.workout.sync)))
        }
    }

    override suspend fun saveAsRoutine(
        workoutId: String,
        name: String,
    ): String? {
        val done = workoutDao.finishedRows(workoutId)?.toModel() ?: return null
        val routine =
            RoutineUpdate.fromExercises(
                Routine(id = ids.newId(), name = name.trim()),
                done.exercises,
                ids::newId,
            )
        routines.save(routine)
        return routine.id
    }
}

/** A finished workout; [setsByExercise] holds its sets (and maybe others') by workout exercise id. */
private fun WorkoutEntity.toFinishedWorkout(
    exercises: List<WorkoutExerciseEntity>,
    setsByExercise: Map<String, List<WorkoutSetEntity>>,
): FinishedWorkout =
    FinishedWorkout(
        id = id,
        name = name,
        startedAt = Instant.fromEpochMilliseconds(startedAt),
        endedAt = Instant.fromEpochMilliseconds(requireNotNull(endedAt) { "Workout $id isn't finished" }),
        routineId = routineId,
        notes = notes,
        exercises = exercises.toModels(setsByExercise),
    )

/** The finished workout [id], or null if there is none (in progress, deleted or never existed). */
internal suspend fun WorkoutDao.finishedRows(id: String): WorkoutRows? {
    val workout = get(id)?.takeIf { it.endedAt != null && it.sync.deletedAt == null } ?: return null
    return WorkoutRows(workout, exercisesOf(id), setsOf(id))
}
