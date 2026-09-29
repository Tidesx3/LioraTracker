package app.liora.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class IdGeneratorTest {
    private val clock = MutableClock(Instant.fromEpochMilliseconds(1_790_000_000_000))
    private val ids = IdGenerator(clock)

    @Test
    fun idsAreVersion7WithRfcVariant() {
        val id = ids.newId()
        assertEquals(36, id.length)
        assertEquals('7', id[14])
        assertTrue(id[19] in "89ab", "variant nibble was ${id[19]}")
    }

    @Test
    fun idsSortByCreationTime() {
        val generated =
            List(50) {
                clock.advanceMillis(1)
                ids.newId()
            }
        assertEquals(generated, generated.sorted())
    }

    @Test
    fun idsAreUniqueWithinTheSameMillisecond() {
        val generated = List(1_000) { ids.newId() }
        assertEquals(generated.size, generated.toSet().size)
    }

    private class MutableClock(
        private var now: Instant,
    ) : Clock {
        override fun now(): Instant = now

        fun advanceMillis(millis: Long) {
            now = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + millis)
        }
    }
}
