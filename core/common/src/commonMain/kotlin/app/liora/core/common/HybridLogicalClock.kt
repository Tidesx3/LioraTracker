package app.liora.core.common

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Hybrid logical clock timestamp: wall-clock millis, a counter for events within the same milli,
 * and the id of the device that produced it. [encoded] sorts lexicographically in causal order,
 * which is what last-writer-wins sync compares.
 */
data class Hlc(
    val millis: Long,
    val counter: Int,
    val node: String,
) : Comparable<Hlc> {
    val encoded: String
        get() =
            millis.toString().padStart(MILLIS_WIDTH, '0') + SEPARATOR +
                counter.toString(RADIX).padStart(COUNTER_WIDTH, '0') + SEPARATOR + node

    override fun compareTo(other: Hlc): Int = compareValuesBy(this, other, Hlc::millis, Hlc::counter, Hlc::node)

    companion object {
        private const val MILLIS_WIDTH = 13
        private const val COUNTER_WIDTH = 4
        private const val RADIX = 16
        private const val SEPARATOR = ':'
        private const val PART_COUNT = 3
        internal const val MAX_COUNTER = 0xFFFF

        fun parse(encoded: String): Hlc {
            val parts = encoded.split(SEPARATOR, limit = PART_COUNT)
            require(parts.size == PART_COUNT) { "Not an HLC: $encoded" }
            return Hlc(millis = parts[0].toLong(), counter = parts[1].toInt(RADIX), node = parts[2])
        }
    }
}

/**
 * Issues monotonically increasing [Hlc]s for local writes and folds in timestamps seen from other
 * devices, so a local edit made after syncing always wins over what it replaced, even if this
 * device's wall clock is behind.
 */
class HybridLogicalClock(
    private val clock: Clock = Clock.System,
    private val nodeId: suspend () -> String,
) {
    private val mutex = Mutex()
    private var last: Hlc? = null

    suspend fun now(): Hlc =
        mutex.withLock {
            val node = last?.node ?: nodeId()
            val wall = clock.now().toEpochMilliseconds()
            val previous = last
            val next =
                when {
                    previous == null || wall > previous.millis -> Hlc(wall, 0, node)
                    previous.counter < Hlc.MAX_COUNTER -> Hlc(previous.millis, previous.counter + 1, node)
                    else -> Hlc(previous.millis + 1, 0, node)
                }
            last = next
            next
        }

    /** Advances past a timestamp received from another device. */
    suspend fun receive(remote: Hlc) {
        mutex.withLock {
            val node = last?.node ?: nodeId()
            val local = last ?: Hlc(0, 0, node)
            val wall = clock.now().toEpochMilliseconds()
            val millis = maxOf(wall, local.millis, remote.millis)
            val counter =
                when {
                    millis == local.millis && millis == remote.millis -> maxOf(local.counter, remote.counter) + 1
                    millis == local.millis -> local.counter + 1
                    millis == remote.millis -> remote.counter + 1
                    else -> 0
                }
            last = Hlc(millis, counter, node)
        }
    }
}
