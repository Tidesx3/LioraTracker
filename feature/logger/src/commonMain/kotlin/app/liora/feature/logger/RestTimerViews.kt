package app.liora.feature.logger

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.model.RestTimer
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.rest_minus
import app.liora.feature.logger.resources.rest_plus
import app.liora.feature.logger.resources.rest_skip
import app.liora.feature.logger.resources.rest_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * A rest countdown as shown: the time left, rounded up so it reads 0:01 until it's really over, and
 * the share of the rest still to go.
 */
internal data class RestProgress(
    val remaining: Duration,
    private val fractionLeft: FloatState,
) {
    /** Moves on every frame. Read it while drawing (a progress lambda), so only the indicator redraws. */
    val fraction: Float get() = fractionLeft.floatValue
}

/**
 * Ticks while shown; null once the rest is over. The time left recomposes once a second, while the
 * progress glides along frame by frame without recomposing anything.
 */
@Composable
internal fun rememberRestProgress(
    timer: RestTimer?,
    clock: Clock = Clock.System,
): RestProgress? {
    val remaining by produceState(remainingOf(timer, clock), timer, clock) {
        while (true) {
            value = remainingOf(timer, clock)
            if (value == null) break
            delay(TICK)
        }
    }
    val fraction = remember(timer, clock) { mutableFloatStateOf(fractionOf(timer, clock)) }
    LaunchedEffect(timer, clock) {
        // An infinite animation as far as Compose is concerned, so UI tests don't wait for it to end.
        while (fraction.floatValue > 0f) {
            withInfiniteAnimationFrameMillis { fraction.floatValue = fractionOf(timer, clock) }
        }
    }
    return remaining?.let { RestProgress(it, fraction) }
}

private fun remainingOf(
    timer: RestTimer?,
    clock: Clock,
): Duration? {
    val remaining = timer?.remaining(clock.now())?.takeIf { it > Duration.ZERO } ?: return null
    return ceil(remaining.toDouble(DurationUnit.SECONDS)).seconds
}

private fun fractionOf(
    timer: RestTimer?,
    clock: Clock,
): Float {
    timer ?: return 0f
    val total = timer.total.inWholeMilliseconds.coerceAtLeast(1)
    return (timer.remaining(clock.now()).inWholeMilliseconds.toFloat() / total).coerceIn(0f, 1f)
}

/** The rest countdown on phones: a strip above the pad, with −15 s, +15 s and skip. */
@Composable
internal fun RestTimerBar(
    progress: RestProgress,
    onAdjust: (seconds: Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(LioraIcons.Timer), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(Res.string.rest_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                )
                Text(
                    text = progress.remaining.formatAsClock(),
                    style = MaterialTheme.typography.titleLarge.tabularNumbers(),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onAdjust(-ADJUST_SECONDS) }) { Text(stringResource(Res.string.rest_minus)) }
                TextButton(onClick = { onAdjust(ADJUST_SECONDS) }) { Text(stringResource(Res.string.rest_plus)) }
                TextButton(onClick = onSkip) { Text(stringResource(Res.string.rest_skip)) }
            }
        }
    }
}

/** The rest countdown on wide screens and in tabletop posture: a big ring, readable from the bench. */
@Composable
internal fun RestTimerRing(
    progress: RestProgress,
    onAdjust: (seconds: Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 10.dp,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.rest_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = progress.remaining.formatAsClock(),
                    style = MaterialTheme.typography.displayMedium.tabularNumbers(),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAdjust(-ADJUST_SECONDS) }) { Text(stringResource(Res.string.rest_minus)) }
            OutlinedButton(onClick = { onAdjust(ADJUST_SECONDS) }) { Text(stringResource(Res.string.rest_plus)) }
            FilledTonalButton(onClick = onSkip) {
                Icon(painterResource(LioraIcons.Skip), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.rest_skip), modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

private val TICK = 200.milliseconds
private val RING_SIZE = 200.dp
private const val ADJUST_SECONDS = 15
