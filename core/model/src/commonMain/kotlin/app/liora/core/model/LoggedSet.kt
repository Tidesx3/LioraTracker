package app.liora.core.model

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * One set as logged. Which fields are filled depends on the exercise's [TrackingType]. For weighted
 * and assisted bodyweight exercises, [weight] is the added load or the assistance.
 */
data class LoggedSet(
    val id: String,
    val type: SetType = SetType.Normal,
    val weight: Mass? = null,
    val reps: Int? = null,
    val duration: Duration? = null,
    val distanceMeters: Double? = null,
    val rpe: Double? = null,
    /** When the set was ticked off; null while it is only planned. */
    val completedAt: Instant? = null,
) {
    val isCompleted: Boolean get() = completedAt != null

    /** Completed and not a warm-up: the sets that count for records, volume and muscle stats. */
    val isWorkingSet: Boolean get() = isCompleted && type != SetType.Warmup
}
