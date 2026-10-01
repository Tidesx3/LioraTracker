package app.liora.core.data.workout

import app.liora.core.common.IdGenerator
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.database.model.WorkoutSetEntity
import app.liora.core.domain.RoutineUpdate
import app.liora.core.model.ExerciseSession
import app.liora.core.model.FinishedWorkout
import app.liora.core.model.LoggedSet
import app.liora.core.model.Routine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * Finished workouts: what history lists and shows. Starting one again goes through
 * [ActiveWorkoutRepository.repeat].
 */
interface WorkoutHistoryRepository {
    /** Every finished workout with its exercises and sets, newest first. */
    val workouts: Flow<List<FinishedWorkout>>

    /** Every finished session of one exercise, oldest first: what its charts, records and history show. */
    fun sessionsOf(exerciseId: String): Flow<List<ExerciseSession>>

    /** Opens a finished workout to correct it with the logger's tools. */
    fun edit(workoutId: String): FinishedWorkoutSession

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

/**
 * A finished workout opened to correct it: its sets and exercises change like in the logger, and its
 * date and times can move. Every change is saved as it's made, like the workout in progress.
 */
interface FinishedWorkoutSession : WorkoutSession {
    /** The workout as it stands; null once it's deleted. */
    val workout: Flow<FinishedWorkout?>

    /**
     * Moves the workout to start at [startedAt] and end at [endedAt]; an end before the start counts as
     * the start. Its sets move along with the start, so records keep the day they were set.
     */
    suspend fun setTimes(
        startedAt: Instant,
        endedAt: Instant,
    )

    /** Done correcting: sets left unticked are dropped, and exercises left without any, as when finishing. */
    suspend fun tidyUp()
}

internal class OfflineWorkoutHistoryRepository(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val routines: RoutineRepository,
    private val restTimer: RestTimerRepository,
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

    override fun sessionsOf(exerciseId: String): Flow<List<ExerciseSession>> =
        workoutDao
            .observeSessionSets(exerciseId)
            .map { rows ->
                // Rows come oldest first; grouping keeps that order.
                rows.groupBy { it.workoutId }.map { (workoutId, sets) ->
                    val first = sets.first()
                    ExerciseSession(
                        workoutId = workoutId,
                        workoutName = first.workoutName,
                        startedAt = Instant.fromEpochMilliseconds(first.startedAt),
                        sets = sets.map { it.set.toLoggedSet() },
                    )
                }
            }.distinctUntilChanged()

    override fun edit(workoutId: String): FinishedWorkoutSession {
        val target = WorkoutTarget.Finished(workoutId)
        return OfflineFinishedWorkoutSession(
            workoutId = workoutId,
            workoutDao = workoutDao,
            transactions = transactions,
            stamper = stamper,
            editor = OfflineWorkoutEditor(target, workoutDao, exerciseDao, transactions, ids, stamper),
            sets = OfflineSetLogger(target, workoutDao, exerciseDao, restTimer, transactions, ids, stamper),
        )
    }

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

internal class OfflineFinishedWorkoutSession(
    private val workoutId: String,
    private val workoutDao: WorkoutDao,
    private val transactions: TransactionRunner,
    private val stamper: SyncStamper,
    override val editor: WorkoutEditor,
    override val sets: SetLogger,
) : FinishedWorkoutSession {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val workout: Flow<FinishedWorkout?> =
        workoutDao
            .observe(workoutId)
            .flatMapLatest { workout ->
                if (workout?.endedAt == null || workout.sync.deletedAt != null) {
                    flowOf(null)
                } else {
                    combine(
                        workoutDao.observeExercises(workoutId),
                        workoutDao.observeSets(workoutId),
                    ) { exercises, sets ->
                        workout.toFinishedWorkout(exercises, sets.groupBy { it.workoutExerciseId })
                    }
                }
            }.distinctUntilChanged()

    private val earlier = workout.map { done -> done?.let { EarlierSessions.ofFinished(it.startedAt, it.exercises) } }

    override val previousSets: Flow<Map<String, List<LoggedSet>>> = earlier.sets(workoutDao::observeLastSessionSets)

    override val exerciseHistory: Flow<Map<String, List<LoggedSet>>> = earlier.sets(workoutDao::observeHistorySets)

    override suspend fun rename(name: String) {
        transactions.inTransaction {
            workoutDao.finishedRows(workoutId)?.let { workoutDao.rename(it.workout, name, stamper) }
        }
    }

    override suspend fun setTimes(
        startedAt: Instant,
        endedAt: Instant,
    ) {
        transactions.inTransaction {
            val rows = workoutDao.finishedRows(workoutId) ?: return@inTransaction
            val start = startedAt.toEpochMilliseconds()
            val end = maxOf(endedAt.toEpochMilliseconds(), start)
            val shift = start - rows.workout.startedAt
            val moved =
                rows.sets.mapNotNull { set ->
                    set.completedAt?.takeIf { shift != 0L }?.let {
                        set.copy(completedAt = it + shift, sync = stamper.touch(set.sync))
                    }
                }
            if (moved.isNotEmpty()) workoutDao.upsertSets(moved)
            if (start != rows.workout.startedAt || end != rows.workout.endedAt) {
                workoutDao.upsert(
                    rows.workout.copy(startedAt = start, endedAt = end, sync = stamper.touch(rows.workout.sync)),
                )
            }
        }
    }

    override suspend fun tidyUp() {
        transactions.inTransaction {
            workoutDao.finishedRows(workoutId)?.let { workoutDao.dropWhatWasntDone(it, stamper) }
        }
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
