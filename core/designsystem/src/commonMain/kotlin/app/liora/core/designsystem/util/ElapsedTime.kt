package app.liora.core.designsystem.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Time since [startedAt], updated on every whole second while in composition. */
@Composable
fun rememberElapsedTime(
    startedAt: Instant,
    clock: Clock = Clock.System,
): Duration {
    val elapsed by produceState(initialValue = clock.now() - startedAt, startedAt, clock) {
        while (true) {
            value = clock.now() - startedAt
            // Sleep until the next second boundary so the display never skips or stutters.
            delay(1.seconds - (value.inWholeMilliseconds % 1_000).milliseconds)
        }
    }
    return elapsed
}

/** `0:42`, `12:05`, `1:02:09`: stopwatch style, the way lifters read workout and rest times. */
fun Duration.formatAsClock(): String =
    coerceAtLeast(Duration.ZERO).toComponents { hours, minutes, seconds, _ ->
        if (hours > 0) {
            "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
        } else {
            "$minutes:${seconds.twoDigits()}"
        }
    }

private fun Int.twoDigits(): String = toString().padStart(2, '0')
