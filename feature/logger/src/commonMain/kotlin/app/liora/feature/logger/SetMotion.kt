package app.liora.feature.logger

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.glass.GlassDefaults
import app.liora.core.designsystem.glass.LocalGlassBackdrop
import app.liora.core.designsystem.glass.glass
import app.liora.core.designsystem.glass.glassPane
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.motion.ConfettiBurst
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.domain.Earned
import app.liora.core.ui.weightText
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.cd_personal_record
import app.liora.feature.logger.resources.cd_top_of_range
import app.liora.feature.logger.resources.logger_maxed_out
import app.liora.feature.logger.resources.logger_maxed_out_body
import app.liora.feature.logger.resources.logger_step_earned
import app.liora.feature.logger.resources.logger_step_next_time
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// Ticking a set off pops its check and fills the row from it (`SetCheck` and `drawSetFill` in
// designsystem); a set that breaks a record celebrates here too, as does a step up earned. The step's
// pieces are the accent's glass, as the app's main buttons are, and fall back to solid violet without
// Liquid Glass.

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

/**
 * The chevron on a set done at the top of its rep range, the way to a step up: a bead of the accent's glass
 * with the light catching its rim. With [pop], as when the set has just been ticked off, it pops in like the
 * check.
 */
@Composable
internal fun TopMark(
    pop: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(if (pop) 0f else 1f) }
    LaunchedEffect(Unit) { if (pop) scale.animateTo(1f, PopSpec) }
    val glass = LioraTheme.glass
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .size(TopMarkSize)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }.then(
                    if (glass.enabled) {
                        Modifier.glassPane(
                            CircleShape,
                            fill = glass.accentTint.copy(alpha = BEAD_FILL),
                            rim = glass.rim,
                        )
                    } else {
                        Modifier.background(colors.primary, CircleShape)
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(LioraIcons.ExpandUp),
            contentDescription = stringResource(Res.string.cd_top_of_range),
            tint = if (glass.enabled) glass.onAccent else colors.onPrimary,
            modifier = Modifier.size(TopMarkSize - 3.dp),
        )
    }
}

/**
 * Under an exercise's sets once they've earned a step: a capsule of the accent's glass with the weight it
 * starts at next time, or that the gym has nothing heavier. With [celebrate], as when the set that earned it
 * has just been ticked off, it pops in, a small burst flies from its arrow, and light sweeps across the glass.
 */
@Composable
internal fun StepEarnedBanner(
    earned: Earned,
    celebrate: Boolean,
    modifier: Modifier = Modifier,
) {
    val pop = remember { Animatable(if (celebrate) 0f else 1f) }
    val shine = rememberShine(celebrate)
    LaunchedEffect(Unit) { if (celebrate) pop.animateTo(1f, PopSpec) }
    val glass = LioraTheme.glass
    val colors = MaterialTheme.colorScheme
    val onBanner = if (glass.enabled) glass.onAccent else colors.onPrimaryContainer
    val (title, body) =
        when (earned) {
            is Earned.StepUp -> {
                stringResource(Res.string.logger_step_earned) to
                    stringResource(Res.string.logger_step_next_time, weightText(earned.to))
            }

            is Earned.TopOfTheStack -> {
                stringResource(Res.string.logger_maxed_out) to
                    stringResource(Res.string.logger_maxed_out_body, weightText(earned.weight))
            }
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val grown = BANNER_FROM + (1 - BANNER_FROM) * pop.value
                    scaleX = grown
                    scaleY = grown
                    alpha = pop.value.coerceIn(0f, 1f)
                }.then(
                    if (glass.enabled) {
                        Modifier.glass(LocalGlassBackdrop.current, CircleShape, GlassDefaults.accent())
                    } else {
                        Modifier.background(colors.primaryContainer, CircleShape)
                    },
                ).glassShine({ shine.value }, CircleShape)
                .padding(start = 6.dp, end = 24.dp, top = 6.dp, bottom = 6.dp)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepBead(onViolet = true, burst = celebrate)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = onBanner)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = onBanner.copy(alpha = BODY_ALPHA),
            )
        }
    }
}

/**
 * The step's arrow in a bead of glass: lighter glass where it sits [onViolet] (the banner), the accent's glass
 * on a card. With [burst], a small burst flies from it, in emerald and violet rather than a record's gold; with
 * [pop], it pops in.
 */
@Composable
internal fun StepBead(
    onViolet: Boolean,
    burst: Boolean,
    modifier: Modifier = Modifier,
    pop: Boolean = false,
) {
    val scale = remember { Animatable(if (pop) 0f else 1f) }
    LaunchedEffect(Unit) { if (pop) scale.animateTo(1f, PopSpec) }
    val glass = LioraTheme.glass
    val colors = MaterialTheme.colorScheme
    val arrow =
        when {
            glass.enabled -> glass.onAccent
            onViolet -> colors.onPrimaryContainer
            else -> colors.onPrimary
        }
    val bead =
        when {
            onViolet && glass.enabled -> {
                Modifier.glassPane(
                    CircleShape,
                    fill = arrow.copy(alpha = BEAD_LIGHT),
                    rim = glass.rim,
                )
            }

            onViolet -> {
                Modifier.background(arrow.copy(alpha = BEAD_LIGHT), CircleShape)
            }

            glass.enabled -> {
                Modifier.glass(LocalGlassBackdrop.current, CircleShape, GlassDefaults.accent())
            }

            else -> {
                Modifier.background(colors.primary, CircleShape)
            }
        }
    Box(
        modifier =
            modifier
                .size(BeadSize)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }.then(bead),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(LioraIcons.StepUp),
            contentDescription = null,
            tint = arrow,
            modifier = Modifier.size(BeadIconSize),
        )
        if (burst) {
            ConfettiBurst(
                modifier = Modifier.matchParentSize(),
                seed = STEP_SEED,
                reach = StepReach,
                colors =
                    listOf(
                        LioraTheme.colors.completed,
                        colors.primary,
                        LioraTheme.colors.completed.copy(alpha = LIGHTER),
                        colors.primary.copy(alpha = LIGHTER),
                    ),
            )
        }
    }
}

/** Light sweeping once across glass, as if turned to it: from 0 to 1 when [celebrate], else resting at 1. */
@Composable
internal fun rememberShine(celebrate: Boolean): Animatable<Float, AnimationVector1D> {
    val shine = remember { Animatable(if (celebrate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (celebrate) {
            shine.animateTo(
                1f,
                tween(SHINE_MILLIS, delayMillis = SHINE_DELAY, easing = LinearOutSlowInEasing),
            )
        }
    }
    return shine
}

/**
 * The band of light [shine] (from [rememberShine]) sweeps across [shape], slanted, from the start to the end.
 * Nothing shows while it rests at either end.
 */
internal fun Modifier.glassShine(
    shine: () -> Float,
    shape: Shape,
): Modifier =
    drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val band = size.width * SHINE_BAND
        onDrawWithContent {
            drawContent()
            val at = shine()
            if (at <= 0f || at >= 1f) return@onDrawWithContent
            val x = -band + (size.width + 2 * band) * at
            clipPath(path) {
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        HALF to Color.White.copy(alpha = SHINE_ALPHA),
                        1f to Color.Transparent,
                        start = Offset(x - band, 0f),
                        end = Offset(x + band, size.height),
                    ),
                )
            }
        }
    }

/** It overshoots as it settles, like the set's check. */
private val PopSpec = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)

private val TopMarkSize = 16.dp
private val BeadSize = 40.dp
private val BeadIconSize = 22.dp

/** Smaller than a record's, so a record still feels like the bigger moment. */
private val StepReach = 44.dp
private const val STEP_SEED = 7
private const val LIGHTER = 0.6f

/** How small the banner starts as it pops in, and how much its second line steps back. */
private const val BANNER_FROM = 0.85f
private const val BODY_ALPHA = 0.86f

/** A chevron's bead, nearly solid so it reads over the done row's tint; the banner's bead, light on violet. */
private const val BEAD_FILL = 0.96f
private const val BEAD_LIGHT = 0.18f

/** The light: a band a third of the glass wide, crossing it just after the pop. */
private const val SHINE_BAND = 0.34f
private const val SHINE_ALPHA = 0.42f
private const val SHINE_MILLIS = 900
private const val SHINE_DELAY = 120
private const val HALF = 0.5f
