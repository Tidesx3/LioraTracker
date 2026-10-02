package app.liora.core.data.workout

import app.liora.core.common.IdGenerator
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.database.model.WorkoutSetEntity
import app.liora.core.domain.RestDefaults
import app.liora.core.domain.SetField
import app.liora.core.domain.SetPlaceholders
import app.liora.core.domain.WorkoutOrder
import app.liora.core.domain.find
import app.liora.core.domain.restAfter
import app.liora.core.domain.setAt
import app.liora.core.model.LoggedSet
import app.liora.core.model.SetType
import kotlin.time.Duration
import kotlin.time.Instant

/** The type of a set added after one of this type: after the warm-ups comes a working set. */
private fun SetType.afterAdding(): SetType = if (this == SetType.Warmup) SetType.Normal else this

/** What ticking off a set did. */
sealed interface SetCompletion {
    /** Logged. [rest] is the rest timer it started, if any. */
    data class Logged(
        val rest: Duration?,
    ) : SetCompletion

    /** Not logged: these fields need a value and have no placeholder to fall back on. */
    data class Missing(
        val fields: Set<SetField>,
    ) : SetCompletion
}

/**
 * Logging sets in a workout. The one in progress has a single logger, shared by the logger screen and
 * the workout notification; a finished one gets its own while it's corrected (see [WorkoutSession]).
 */
interface SetLogger {
    /** Adds a set to the exercise, repeating the last one's values so a working set needs no typing. */
    suspend fun addSet(workoutExerciseId: String)

    /** Changes a set's values or type; it stays completed or open as it was. */
    suspend fun updateSet(
        setId: String,
        change: LoggedSet.() -> LoggedSet,
    )

    suspend fun removeSet(setId: String)

    /**
     * Ticks a set off. Empty fields take their placeholder (last session, else the routine's target).
     * In the workout in progress rest starts, unless the set is mid-round in a superset; a finished
     * workout's set counts as done when the workout ended, and starts no rest.
     */
    suspend fun completeSet(setId: String): SetCompletion

    /** Ticks off the set that's up next, e.g. from the notification; null when there is none. */
    suspend fun completeCurrentSet(): SetCompletion?

    suspend fun reopenSet(setId: String)
}

internal class OfflineSetLogger(
    private val target: WorkoutTarget,
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val restTimer: RestTimerRepository,
    private val transactions: TransactionRunner,
    private val ids: IdGenerator,
    private val stamper: SyncStamper,
    private val restDefaults: suspend () -> RestDefaults = { RestDefaults() },
) : SetLogger {
    override suspend fun addSet(workoutExerciseId: String) {
        transactions.inTransaction {
            val rows = target.rows(workoutDao) ?: return@inTransaction
            if (rows.exercises.none { it.id == workoutExerciseId }) return@inTransaction
            val last = rows.setsOf(workoutExerciseId).lastOrNull()
            val row =
                emptySetRow(
                    ids.newId(),
                    workoutExerciseId,
                    (last?.position ?: -1) + 1,
                    SetType.Normal,
                    stamper.newRow(),
                )
            val copied =
                last?.toLoggedSet()?.let { set ->
                    set.copy(id = row.id, type = set.type.afterAdding(), completedAt = null, rpe = null)
                }
            workoutDao.upsertSet(copied?.let(row::withValues) ?: row)
        }
    }

    override suspend fun updateSet(
        setId: String,
        change: LoggedSet.() -> LoggedSet,
    ) = editSet(setId) { it.change() }

    override suspend fun removeSet(setId: String) {
        transactions.inTransaction {
            val row = target.rows(workoutDao)?.sets?.firstOrNull { it.id == setId } ?: return@inTransaction
            workoutDao.upsertSet(row.copy(sync = stamper.tombstone(row.sync)))
        }
    }

    override suspend fun completeSet(setId: String): SetCompletion =
        transactions.inTransaction { complete(setId) } ?: SetCompletion.Missing(emptySet())

    override suspend fun completeCurrentSet(): SetCompletion? =
        transactions.inTransaction {
            val workout = target.rows(workoutDao)?.toModel() ?: return@inTransaction null
            val current = WorkoutOrder.current(workout) ?: return@inTransaction null
            complete(workout.setAt(current).id)
        }

    override suspend fun reopenSet(setId: String) = editSet(setId) { it.copy(completedAt = null) }

    /** Runs inside a transaction; null when the set isn't part of the target workout. */
    private suspend fun complete(setId: String): SetCompletion? {
        val rows = target.rows(workoutDao)
        val workout = rows?.toModel()
        val ref = workout?.find(setId)
        if (rows == null || workout == null || ref == null) return null
        val exercise = workout.exercises[ref.exerciseIndex]
        val previous =
            workoutDao
                .lastSessionSets(listOf(exercise.exerciseId), before = rows.earlierThan)
                .map { it.set.toLoggedSet() }
        val filled =
            SetPlaceholders.fill(workout.setAt(ref), SetPlaceholders.previousFor(exercise.sets, ref.setIndex, previous))
        val missing = SetPlaceholders.missingFields(exerciseDao.trackingTypeOf(exercise.exerciseId), filled)
        if (missing.isNotEmpty()) return SetCompletion.Missing(missing)

        val row = rows.sets.first { it.id == setId }
        // A set added to a finished workout was done during it; its end is the closest time there is.
        val endedAt = rows.workout.endedAt
        val logged = filled.copy(completedAt = Instant.fromEpochMilliseconds(endedAt ?: stamper.nowMillis()))
        workoutDao.upsertSet(row.withValues(logged).copy(sync = stamper.touch(row.sync)))

        // Mid-round in a superset you go straight to the next exercise; a timer left running would lie.
        val rest =
            if (endedAt == null && WorkoutOrder.restsAfter(workout, ref)) {
                restAfter(logged, exercise, exerciseDao.settingsOf(exercise.exerciseId), restDefaults())
            } else {
                null
            }
        when {
            // Correcting history leaves the timer of the workout in progress alone.
            endedAt != null -> Unit

            rest != null -> restTimer.start(rest)

            else -> restTimer.stop()
        }
        return SetCompletion.Logged(rest)
    }

    private suspend fun editSet(
        setId: String,
        change: (LoggedSet) -> LoggedSet,
    ) {
        transactions.inTransaction {
            val row = target.rows(workoutDao)?.sets?.firstOrNull { it.id == setId } ?: return@inTransaction
            val updated = row.withValues(change(row.toLoggedSet()).copy(id = row.id))
            if (updated != row) workoutDao.upsertSet(updated.copy(sync = stamper.touch(row.sync)))
        }
    }
}
