package app.liora.core.common

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class HybridLogicalClockTest {
    private var wallMillis = 1_790_000_000_000
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(wallMillis)
        }
    private val hlc = HybridLogicalClock(clock) { "device-a" }

    @Test
    fun timestampsIncreaseWithinTheSameMillisecond() =
        runTest {
            val first = hlc.now()
            val second = hlc.now()
            assertEquals(first.millis, second.millis)
            assertEquals(first.counter + 1, second.counter)
            assertTrue(first.encoded < second.encoded)
        }

    @Test
    fun timestampsNeverGoBackwardsWhenTheWallClockDoes() =
        runTest {
            val before = hlc.now()
            wallMillis -= 5_000
            val after = hlc.now()
            assertTrue(after > before)
            assertTrue(after.encoded > before.encoded)
        }

    @Test
    fun receivingAFutureRemoteTimestampPushesLocalTimePastIt() =
        runTest {
            val remote = Hlc(millis = wallMillis + 60_000, counter = 3, node = "device-b")
            hlc.receive(remote)
            val local = hlc.now()
            assertTrue(local > remote)
            assertEquals("device-a", local.node)
        }

    @Test
    fun encodingRoundTripsAndSortsLikeComparison() {
        val stamps =
            listOf(
                Hlc(1_790_000_000_000, 0, "b"),
                Hlc(1_790_000_000_000, 10, "a"),
                Hlc(1_790_000_000_001, 0, "a"),
                Hlc(999, 0xFFFF, "z"),
            )
        stamps.forEach { assertEquals(it, Hlc.parse(it.encoded)) }
        assertEquals(stamps.sorted(), stamps.sortedBy { it.encoded })
    }
}
