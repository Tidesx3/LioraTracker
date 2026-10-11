package app.liora.feature.logger

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.LioraOutlinedButton
import app.liora.core.designsystem.component.LioraTonalButton
import app.liora.core.designsystem.component.RollingText
import app.liora.core.designsystem.glass.GlassDefaults
import app.liora.core.designsystem.glass.LocalGlassBackdrop
import app.liora.core.designsystem.glass.glass
import app.liora.core.designsystem.glass.glassPane
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.LioraGlass
import app.liora.core.designsystem.theme.LioraTheme
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

/**
 * The rest countdown on phones: a pill floating above the pad, like the tabs, with −15 s, +15 s and
 * skip. The time left runs around its outline, from the top middle, and shrinks back as rest runs out.
 * The buttons get their room first and the time stays on one line; the word "Rest" shows only when
 * there's room left for it, as there isn't for German's longer buttons on a narrow phone.
 */
@Composable
internal fun RestTimerBar(
    progress: RestProgress,
    onAdjust: (seconds: Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LioraTheme.glass
    val container = MaterialTheme.colorScheme.primaryContainer
    // The violet on the pale container in light. In dark the container is the violet itself, so the
    // line is a light violet: still the accent, and clear against it. On the accent's glass it takes
    // the glass's own text color.
    val line =
        when {
            glass.enabled -> glass.onAccent
            container.luminance() > HALF -> MaterialTheme.colorScheme.primary
            else -> lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimaryContainer, LINE_LIGHTER)
        }
    val pulse = rememberEndingPulse(progress.remaining)
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .graphicsLayer {
                    val swell = 1f + PILL_SWELL * pulse.value
                    scaleX = swell
                    scaleY = swell
                }.then(if (glass.enabled) Modifier.accentGlass(glass) else Modifier),
        shape = CircleShape,
        color = if (glass.enabled) Color.Transparent else container,
        contentColor = if (glass.enabled) glass.onAccent else MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = if (glass.enabled) 0.dp else FLOATING_ELEVATION.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .drawWithContent {
                        drawContent()
                        drawPillProgress(progress.fraction, line, OUTLINE_WIDTH.dp.toPx() * (1f + pulse.value))
                    }.padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Weighted, so it's measured after the buttons and takes the room they leave.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(LioraIcons.Timer), contentDescription = null, modifier = Modifier.size(20.dp))
                WholeOrNothing(Modifier.weight(1f, fill = false)) {
                    Text(
                        text = stringResource(Res.string.rest_title),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                // Counting down, each second rolls away.
                RollingText(
                    text = AnnotatedString(progress.remaining.formatAsClock()),
                    style =
                        MaterialTheme.typography.titleLarge.tabularNumbers().copy(
                            color = LocalContentColor.current,
                        ),
                    modifier = Modifier.padding(start = 8.dp).testTag(LoggerTags.REST_TIME),
                )
            }
            // The pill's own text color: the accent would vanish on the accent.
            val buttons = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)
            TextButton(onClick = { onAdjust(-ADJUST_SECONDS) }, colors = buttons) {
                Text(stringResource(Res.string.rest_minus))
            }
            TextButton(onClick = { onAdjust(ADJUST_SECONDS) }, colors = buttons) {
                Text(stringResource(Res.string.rest_plus))
            }
            TextButton(onClick = onSkip, colors = buttons) { Text(stringResource(Res.string.rest_skip)) }
        }
    }
}

/** [content] at its own width when that fits, and nothing otherwise, rather than wrapped or cut off. */
@Composable
private fun WholeOrNothing(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints(maxHeight = constraints.maxHeight))
        if (placeable.width > constraints.maxWidth) {
            layout(0, 0) {}
        } else {
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    }
}

/**
 * [fraction] of the pill's outline, inside its edge: a faint track all around, and the time left from
 * the top middle, clockwise.
 */
private fun DrawScope.drawPillProgress(
    fraction: Float,
    color: Color,
    width: Float,
) {
    val inset = width / 2
    val right = size.width - inset
    val bottom = size.height - inset
    val radius = (bottom - inset) / 2
    val outline =
        Path().apply {
            moveTo(size.width / 2, inset)
            lineTo(right - radius, inset)
            arcTo(Rect(right - 2 * radius, inset, right, bottom), -QUARTER_TURN, HALF_TURN, false)
            lineTo(inset + radius, bottom)
            arcTo(Rect(inset, inset, inset + 2 * radius, bottom), QUARTER_TURN, HALF_TURN, false)
            close()
        }
    drawPath(outline, color.copy(alpha = TRACK_ALPHA), style = Stroke(width))
    if (fraction <= 0f) return
    val measure = PathMeasure().apply { setPath(outline, forceClosed = false) }
    val left = Path()
    measure.getSegment(0f, measure.length * fraction, left, startWithMoveTo = true)
    drawPath(left, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * The rest countdown on wide screens and in tabletop posture: a big ring, readable from the bench. With
 * Liquid Glass the ring runs inside a disc of glass, and its buttons are pills of glass, Skip the accent's.
 */
@Composable
internal fun RestTimerRing(
    progress: RestProgress,
    onAdjust: (seconds: Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LioraTheme.glass
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val pulse = rememberEndingPulse(progress.remaining)
        Box(
            Modifier
                .size(RING_SIZE)
                .graphicsLayer {
                    val swell = 1f + RING_SWELL * pulse.value
                    scaleX = swell
                    scaleY = swell
                }.then(if (glass.enabled) Modifier.glassDisc(glass) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxSize().then(if (glass.enabled) Modifier.padding(RING_INSET) else Modifier),
                strokeWidth = 10.dp,
                trackColor =
                    if (glass.enabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = GLASS_TRACK_ALPHA)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.rest_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RollingText(
                    text = AnnotatedString(progress.remaining.formatAsClock()),
                    style =
                        MaterialTheme.typography.displayMedium
                            .tabularNumbers()
                            .copy(color = MaterialTheme.colorScheme.onSurface),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (glass.enabled) {
                GlassPill(
                    glass,
                    onClick = { onAdjust(-ADJUST_SECONDS) },
                ) { Text(stringResource(Res.string.rest_minus)) }
                GlassPill(glass, onClick = { onAdjust(ADJUST_SECONDS) }) { Text(stringResource(Res.string.rest_plus)) }
                GlassPill(glass, onClick = onSkip, accent = true) { SkipLabel() }
            } else {
                LioraOutlinedButton(
                    onClick = { onAdjust(-ADJUST_SECONDS) },
                ) { Text(stringResource(Res.string.rest_minus)) }
                LioraOutlinedButton(
                    onClick = { onAdjust(ADJUST_SECONDS) },
                ) { Text(stringResource(Res.string.rest_plus)) }
                LioraTonalButton(onClick = onSkip) { SkipLabel() }
            }
        }
    }
}

@Composable
private fun RowScope.SkipLabel() {
    Icon(painterResource(LioraIcons.Skip), contentDescription = null, modifier = Modifier.size(18.dp))
    Text(stringResource(Res.string.rest_skip), modifier = Modifier.padding(start = 4.dp))
}

/**
 * A beat on each of the last seconds of rest: 1 as the second turns, easing back to 0 before the next,
 * so the timer pulses as it runs out. Drawing reads it, so a beat redraws without recomposing.
 */
@Composable
private fun rememberEndingPulse(remaining: Duration): State<Float> {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(remaining) {
        if (remaining > PULSE_FROM) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(0f, PulseSpec)
    }
    return pulse.asState()
}

private val TICK = 200.milliseconds
private val RING_SIZE = 200.dp

/** With Liquid Glass, the room between the ring and the disc's rim. */
private val RING_INSET = 14.dp
private const val GLASS_TRACK_ALPHA = 0.16f
private const val ADJUST_SECONDS = 15

/** The last seconds pulse; earlier ones just count down. */
private val PULSE_FROM = 5.seconds
private val PulseSpec = tween<Float>(durationMillis = 700, easing = FastOutSlowInEasing)
private const val PILL_SWELL = 0.03f
private const val RING_SWELL = 0.05f

private const val FLOATING_ELEVATION = 6
private const val OUTLINE_WIDTH = 3
private const val TRACK_ALPHA = 0.2f
private const val HALF = 0.5f
private const val LINE_LIGHTER = 0.3f
private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f
