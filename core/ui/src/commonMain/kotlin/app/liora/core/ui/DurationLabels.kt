package app.liora.core.ui

import androidx.compose.runtime.Composable
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.ui.resources.Res
import app.liora.core.ui.resources.duration_hours
import app.liora.core.ui.resources.duration_minutes
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/** "58 min", "1 h 12 min": how long a workout, or a month of them, took, to the minute. */
@Composable
fun durationLabel(duration: Duration): String {
    val numbers = rememberNumberFormatter()
    // A workout that took seconds still reads as a minute rather than zero.
    val minutes = duration.inWholeMinutes.coerceAtLeast(1)
    val hours = minutes / MINUTES_PER_HOUR
    return if (hours == 0L) {
        stringResource(Res.string.duration_minutes, numbers.format(minutes.toInt()))
    } else {
        stringResource(
            Res.string.duration_hours,
            numbers.format(hours.toInt()),
            numbers.format((minutes % MINUTES_PER_HOUR).toInt()),
        )
    }
}

private const val MINUTES_PER_HOUR = 60L
