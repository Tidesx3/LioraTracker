package app.liora.android.workout

import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.model.RestTimer
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant

/** Time left, rounded up so it reads 0:01 until the rest is really over. */
internal fun RestTimer.remainingClock(now: Instant): String =
    ceil(remaining(now).toDouble(DurationUnit.SECONDS)).seconds.formatAsClock()

/** How long until [remainingClock] shows the next second. */
internal fun RestTimer.untilNextSecond(now: Instant): Duration {
    val intoSecond = remaining(now).inWholeMilliseconds % MILLIS_PER_SECOND
    return (if (intoSecond == 0L) MILLIS_PER_SECOND else intoSecond).milliseconds + TICK_SLACK
}

private const val MILLIS_PER_SECOND = 1_000L

/** Lands just past the second, so rounding up doesn't show the old one again. */
private val TICK_SLACK = 20.milliseconds
