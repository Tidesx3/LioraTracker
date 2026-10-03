package app.liora.feature.logger

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.motion.ConfettiBurst
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.cd_complete_set
import app.liora.feature.logger.resources.cd_personal_record
import app.liora.feature.logger.resources.cd_reopen_set
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.hypot

// Ticking a set off: the check pops and the row fills from it. Only drawing moves, so a tap is never
// held up, and with the system's animations off Compose shows the end at once.

/**
 * The done tint, [fraction] of the way: it spreads from the check across the row as the set is ticked
 * off, so the fill starts where the thumb is, and draws back the same way when it's reopened.
 */
internal fun DrawScope.drawFill(
    fraction: Float,
    color: Color,
) {
    if (fraction <= 0f) return
    if (fraction >= 1f) {
        drawRect(color)
        return
    }
    val center = Offset(size.width - (RowPadding + CheckSize / 2).toPx(), size.height / 2)
    // Far enough to reach the row's far corners from the check.
    val reach = hypot(center.x, size.height / 2)
    clipRect { drawCircle(color, radius = reach * fraction, center = center) }
}

@Composable
internal fun CheckButton(
    completed: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(if (completed) Res.string.cd_reopen_set else Res.string.cd_complete_set)
    // A ring until the set is done, then it fills in with a pop: the circle swells past its size and
    // settles, and the tick springs up inside it. Only when it's ticked here and now, not on opening.
    val pop = remember { Animatable(1f) }
    var wasCompleted by remember { mutableStateOf(completed) }
    LaunchedEffect(completed) {
        if (completed && !wasCompleted) {
            pop.snapTo(0f)
            pop.animateTo(1f, PopSpec)
        } else {
            pop.snapTo(1f)
        }
        wasCompleted = completed
    }
    val fill by animateColorAsState(if (completed) LioraTheme.colors.completed else Color.Transparent, ColorSpec)
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .size(CheckSize)
                .graphicsLayer {
                    val scale = lerp(POP_FROM, 1f, pop.value)
                    scaleX = scale
                    scaleY = scale
                }.semantics { contentDescription = description },
        shape = CircleShape,
        color = fill,
        contentColor = if (completed) LioraTheme.colors.onCompleted else MaterialTheme.colorScheme.outline,
        border = if (completed) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(LioraIcons.Check),
                contentDescription = null,
                modifier =
                    Modifier.graphicsLayer {
                        val scale = lerp(TICK_FROM, 1f, pop.value)
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
    }
}

/** The check starts a little small and overshoots as it settles; the tick inside starts smaller still. */
private const val POP_FROM = 0.8f
private const val TICK_FROM = 0.3f
private val PopSpec = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)
private val ColorSpec = tween<Color>(durationMillis = 120)

/** The tint spreads quickly, done well before the next tap. */
internal val FillSpec = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)

/**
 * The trophy on a set that broke a personal record. With [celebrate], as when the set has just been
 * logged, it pops in from nothing and a burst of confetti flies from it; [seed] picks the confetti.
 */
@Composable
internal fun RecordMark(
    celebrate: Boolean,
    seed: Int,
    modifier: Modifier = Modifier,
) {
    val pop = remember { Animatable(if (celebrate) 0f else 1f) }
    LaunchedEffect(Unit) { if (celebrate) pop.animateTo(1f, PopSpec) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(LioraIcons.Record),
            contentDescription = stringResource(Res.string.cd_personal_record),
            tint = LioraTheme.colors.personalRecord,
            modifier =
                Modifier.size(18.dp).graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
        if (celebrate) ConfettiBurst(Modifier.matchParentSize(), seed = seed)
    }
}
