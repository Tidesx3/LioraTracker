package app.liora.core.model

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A running rest countdown. It belongs to the phone in your hand rather than to the synced workout,
 * and it is stored, so it keeps counting through the app being closed.
 */
data class RestTimer(
    val startedAt: Instant,
    val endsAt: Instant,
) {
    val total: Duration get() = endsAt - startedAt

    fun remaining(now: Instant): Duration = (endsAt - now).coerceAtLeast(Duration.ZERO)

    fun isOver(now: Instant): Boolean = now >= endsAt
}
