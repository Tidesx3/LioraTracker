package app.liora.core.data.workout

import app.liora.core.common.IdGenerator
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.model.ActiveWorkout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * Owns the single in-progress workout. It lives in the database from the first tap, so it survives
 * the app being killed, the phone rebooting or the battery dying mid-session.
 */
interface ActiveWorkoutRepository {
    val activeWorkout: Flow<ActiveWorkout?>

    /** Starts an empty workout, or returns the one already in progress. */
    suspend fun startEmptyWorkout(): ActiveWorkout

    suspend fun finish()

    suspend fun discard()
}

internal class OfflineActiveWorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val transactions: TransactionRunner,
    private val ids: IdGenerator,
    private val stamper: SyncStamper,
) : ActiveWorkoutRepository {
    override val activeWorkout: Flow<ActiveWorkout?> = workoutDao.observeActive().map { it?.toActiveWorkout() }

    override suspend fun startEmptyWorkout(): ActiveWorkout =
        transactions.inTransaction {
            workoutDao.getActive()?.toActiveWorkout() ?: run {
                val sync = stamper.newRow()
                val workout =
                    WorkoutEntity(
                        id = ids.newId(),
                        name = null,
                        startedAt = sync.createdAt,
                        endedAt = null,
                        routineId = null,
                        notes = null,
                        bodyweightKg = null,
                        sync = sync,
                    )
                workoutDao.upsert(workout)
                workout.toActiveWorkout()
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
}

private fun WorkoutEntity.toActiveWorkout() =
    ActiveWorkout(id = id, name = name, startedAt = Instant.fromEpochMilliseconds(startedAt))
