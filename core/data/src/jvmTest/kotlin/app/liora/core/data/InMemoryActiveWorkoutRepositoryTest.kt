package app.liora.core.data

import app.cash.turbine.test
import app.liora.core.common.IdGenerator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class InMemoryActiveWorkoutRepositoryTest {
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(1_790_000_000_000)
        }
    private val repository = InMemoryActiveWorkoutRepository(IdGenerator(clock), clock)

    @Test
    fun startingTwiceKeepsTheSameWorkout() =
        runTest {
            val first = repository.startEmptyWorkout()
            val second = repository.startEmptyWorkout()
            assertEquals(first, second)
            assertEquals(clock.now(), first.startedAt)
        }

    @Test
    fun finishAndDiscardClearTheActiveWorkout() =
        runTest {
            repository.activeWorkout.test {
                assertNull(awaitItem())
                val started = repository.startEmptyWorkout()
                assertEquals(started, awaitItem())
                repository.finish()
                assertNull(awaitItem())
                repository.startEmptyWorkout()
                awaitItem()
                repository.discard()
                assertNull(awaitItem())
            }
        }
}
