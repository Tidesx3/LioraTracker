package app.liora.core.common

import kotlin.random.Random
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Creates UUIDv7 ids (RFC 9562): globally unique, so any device can create records offline and sync
 * later without id collisions, and time-ordered, so ids sort by creation time and index well.
 */
class IdGenerator(
    private val clock: Clock = Clock.System,
    private val random: Random = Random.Default,
) {
    fun newId(): String {
        val unixMillis = clock.now().toEpochMilliseconds()
        // 48-bit timestamp | 4-bit version (7) | 12 random bits
        val mostSignificant =
            (unixMillis shl TIMESTAMP_SHIFT) or (VERSION_7 shl VERSION_SHIFT) or (random.nextLong() and RAND_A_MASK)
        // 2-bit variant (0b10) | 62 random bits
        val leastSignificant = (random.nextLong() and RAND_B_MASK) or Long.MIN_VALUE
        return Uuid.fromLongs(mostSignificant, leastSignificant).toString()
    }

    private companion object {
        const val TIMESTAMP_SHIFT = 16
        const val VERSION_SHIFT = 12
        const val VERSION_7 = 0x7L
        const val RAND_A_MASK = 0xFFFL
        const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL
    }
}
