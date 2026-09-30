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

/**
 * How long until the time left reaches the next multiple of [step]. With a step that divides a second,
 * every whole second is one of them, so [remainingClock] changes right on time.
 */
internal fun RestTimer.untilNextStep(
    now: Instant,
    step: Duration,
): Duration {
    val stepMillis = step.inWholeMilliseconds
    val intoStep = remaining(now).inWholeMilliseconds % stepMillis
    return (if (intoStep == 0L) stepMillis else intoStep).milliseconds + TICK_SLACK
}

/** Lands just past the step, so rounding up doesn't show the old second again. */
private val TICK_SLACK = 20.milliseconds
