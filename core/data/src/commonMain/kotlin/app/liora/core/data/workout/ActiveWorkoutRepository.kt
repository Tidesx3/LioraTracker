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
import app.liora.core.domain.RestDefaults
import app.liora.core.domain.RoutineUpdate
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
 * and logging sets go through its [editor] and [sets].
 */
interface ActiveWorkoutRepository : WorkoutSession {
    /** The workout in progress with its exercises and sets, or null. */
    val activeWorkout: Flow<ActiveWorkout?>

    /** Starts an empty workout, or returns the one already in progress. */
    suspend fun startEmptyWorkout(): ActiveWorkout

    /**
     * Starts a workout from a routine: its exercises, supersets, rest times and planned sets, with the
     * routine's targets prefilled. Returns the workout already in progress instead, if there is one.
     */
    suspend fun startFromRoutine(routineId: String): ActiveWorkout

    /**
     * Starts a workout like a finished one: its name, exercises, supersets, rest times and sets, with
     * that day's values prefilled (a rep range stays a hint). It isn't tied to a routine. Returns the
     * workout already in progress instead, if there is one.
     */
    suspend fun repeat(workoutId: String): ActiveWorkout

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
    exerciseDao: ExerciseDao,
    private val routines: RoutineRepository,
    private val restTimer: RestTimerRepository,
    private val transactions: TransactionRunner,
    private val ids: IdGenerator,
    private val stamper: SyncStamper,
    // Read as each set is ticked off, so a change in Settings applies to the next rest.
    restDefaults: suspend () -> RestDefaults = { RestDefaults() },
) : ActiveWorkoutRepository {
    override val editor: WorkoutEditor =
        OfflineWorkoutEditor(WorkoutTarget.Active, workoutDao, exerciseDao, transactions, ids, stamper)

    override val sets: SetLogger =
        OfflineSetLogger(
            WorkoutTarget.Active,
            workoutDao,
            exerciseDao,
            restTimer,
            transactions,
            ids,
            stamper,
            restDefaults,
        )

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

    private val earlier = activeWorkout.map { active -> active?.let { EarlierSessions.ofActive(it.exercises) } }

    override val previousSets: Flow<Map<String, List<LoggedSet>>> = earlier.sets(workoutDao::observeLastSessionSets)

    override val exerciseHistory: Flow<Map<String, List<LoggedSet>>> = earlier.sets(workoutDao::observeHistorySets)

    override suspend fun startEmptyWorkout(): ActiveWorkout =
        transactions.inTransaction {
            workoutDao.activeRows()?.toModel() ?: startPlanned(name = null, routineId = null, plan = NOTHING_PLANNED)
        }

    override suspend fun startFromRoutine(routineId: String): ActiveWorkout {
        val routine = requireNotNull(routines.get(routineId)) { "No routine $routineId" }
        return transactions.inTransaction {
            workoutDao.activeRows()?.toModel()
                ?: startPlanned(name = routine.name, routineId = routine.id, plan = routine)
        }
    }

    override suspend fun repeat(workoutId: String): ActiveWorkout =
        transactions.inTransaction {
            workoutDao.activeRows()?.toModel() ?: run {
                val done = requireNotNull(workoutDao.finishedRows(workoutId)) { "No finished workout $workoutId" }
                // What was done that day becomes the plan, just like a routine's targets.
                val plan =
                    RoutineUpdate.fromExercises(Routine(id = "", name = ""), done.toModel().exercises, ids::newId)
                startPlanned(name = done.workout.name, routineId = null, plan = plan)
            }
        }

    /** A new workout with [plan]'s exercises and sets, not yet ticked off. */
    private suspend fun startPlanned(
        name: String?,
        routineId: String?,
        plan: Routine,
    ): ActiveWorkout {
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
        val (exercises, sets) = plannedRows(workout.id, plan)
        if (exercises.isNotEmpty()) workoutDao.upsertExercises(exercises)
        if (sets.isNotEmpty()) workoutDao.upsertSets(sets)
        return workout.toActiveWorkout(exercises, sets)
    }

    override suspend fun rename(name: String) {
        transactions.inTransaction {
            workoutDao.getActive()?.let { workoutDao.rename(it, name, stamper) }
        }
    }

    override suspend fun finish(updateRoutine: Boolean) {
        val workout = workoutDao.activeRows()?.toModel() ?: return
        val routine = workout.routineId?.takeIf { updateRoutine }?.let { routines.get(it) }
        transactions.inTransaction {
            val rows = workoutDao.activeRows() ?: return@inTransaction
            workoutDao.dropWhatWasntDone(rows, stamper)
            workoutDao.upsert(rows.workout.copy(endedAt = stamper.nowMillis(), sync = stamper.touch(rows.workout.sync)))
            restTimer.stop()
        }
        routine?.let { routines.save(RoutineUpdate.fromWorkout(it, workout, ids::newId)) }
    }

    override suspend fun discard() {
        transactions.inTransaction {
            val workout = workoutDao.getActive() ?: return@inTransaction
            workoutDao.upsert(workout.copy(sync = stamper.tombstone(workout.sync)))
            restTimer.stop()
        }
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

/** An empty workout starts from an empty plan. */
private val NOTHING_PLANNED = Routine(id = "", name = "")
