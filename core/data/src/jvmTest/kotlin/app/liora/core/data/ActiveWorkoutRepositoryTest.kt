package app.liora.core.data

import app.cash.turbine.test
import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.workout.OfflineActiveWorkoutRepository
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ActiveWorkoutRepositoryTest {
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(1_790_000_000_000)
        }
    private val database = inMemoryLioraDatabase()
    private val repository =
        OfflineActiveWorkoutRepository(
            workoutDao = database.workoutDao(),
            transactions = TransactionRunner(database),
            ids = IdGenerator(clock),
            stamper = SyncStamper(HybridLogicalClock(clock) { "test-device" }, clock),
        )

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun startingTwiceKeepsTheSameWorkout() =
        runTest {
            val first = repository.startEmptyWorkout()
            val second = repository.startEmptyWorkout()
            assertEquals(first, second)
            assertEquals(clock.now(), first.startedAt)
        }

    @Test
    fun finishingEndsTheWorkoutAndMarksItForSync() =
        runTest {
            val started = repository.startEmptyWorkout()
            repository.finish()

            val stored = assertNotNull(database.workoutDao().get(started.id))
            assertEquals(clock.now().toEpochMilliseconds(), stored.endedAt)
            assertTrue(stored.sync.dirty)
            assertTrue(stored.sync.hlc.endsWith(":test-device"))
            assertNull(database.workoutDao().getActive())
        }

    @Test
    fun discardingLeavesATombstoneForSync() =
        runTest {
            repository.activeWorkout.test {
                assertNull(awaitItem())
                val started = repository.startEmptyWorkout()
                assertEquals(started, awaitItem())
                repository.discard()
                assertNull(awaitItem())

                val stored = assertNotNull(database.workoutDao().get(started.id))
                assertNotNull(stored.sync.deletedAt)
            }
        }
}
