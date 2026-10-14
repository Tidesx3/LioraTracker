package app.liora.feature.logger

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.LioraStatusChip
import app.liora.core.designsystem.component.RollingText
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.domain.AgainstLastTime
import app.liora.core.domain.VersusLastTime
import app.liora.core.ui.volumeText
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.cd_ahead_of_last_time
import app.liora.feature.logger.resources.cd_behind_last_time
import app.liora.feature.logger.resources.cd_level_with_last_time
import app.liora.feature.logger.resources.race_ahead
import app.liora.feature.logger.resources.race_label
import app.liora.feature.logger.resources.race_reps_fewer
import app.liora.feature.logger.resources.race_reps_more
import app.liora.feature.logger.resources.race_score
import app.liora.feature.logger.resources.race_to_beat
import app.liora.feature.logger.resources.race_to_beat_described
import app.liora.feature.logger.resources.race_volume_less
import app.liora.feature.logger.resources.race_volume_more
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

// Racing last time: how today's sets do against their counterparts in the Previous column, as a scoreboard
// pill under the workout's totals and a mark on each set done. Ahead is emerald, like a set done; level and
// behind stay quiet grey, never red, as a lighter day is no failure.

/**
 * Today against last time, as a scoreboard pill under the totals: "Last time ▲ 1 of 3 │ +80 kg", how many of
 * the sets with a counterpart are ahead so far and what the sets done add up to against theirs. Before the first
 * is done, how many there are to beat. Nothing shows when nothing has a counterpart. The figures roll as they
 * change; screen readers hear it as one sentence.
 */
@Composable
internal fun RaceChips(
    race: AgainstLastTime,
    modifier: Modifier = Modifier,
) {
    if (race.comparable == 0) return
    val started = race.compared > 0
    val delta = raceDelta(race)
    val sentence =
        if (started) {
            pluralStringResource(Res.plurals.race_ahead, race.comparable, race.ahead, race.comparable)
        } else {
            pluralStringResource(Res.plurals.race_to_beat_described, race.comparable, race.comparable)
        }
    val description = listOfNotNull(sentence, delta?.first).joinToString(", ")
    val style = MaterialTheme.typography.labelLarge.tabularNumbers()
    Box(modifier.clearAndSetSemantics { contentDescription = description }) {
        LioraStatusChip(
            leadingIcon = {
                Icon(
                    painterResource(LioraIcons.History),
                    contentDescription = null,
                    tint = quietColor(),
                    modifier = Modifier.size(18.dp),
                )
            },
            label = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.race_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = quietColor(),
                    )
                    if (started) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VersusIcon(VersusLastTime.Ahead, tint = if (race.ahead > 0) aheadColor() else quietColor())
                            RaceText(
                                stringResource(Res.string.race_score, race.ahead, race.comparable),
                                style.copy(color = MaterialTheme.colorScheme.onSurface),
                            )
                        }
                    } else {
                        RaceText(
                            pluralStringResource(Res.plurals.race_to_beat, race.comparable, race.comparable),
                            style.copy(color = MaterialTheme.colorScheme.onSurface),
                        )
                    }
                    delta?.let { (text, ahead) ->
                        VerticalDivider(
                            Modifier.height(DIVIDER_HEIGHT.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        RaceText(text, style.copy(color = if (ahead) aheadColor() else quietColor()))
                    }
                }
            },
        )
    }
}

/** The sets done against theirs: the load moved, where they move load, else the reps; null when even. */
@Composable
private fun raceDelta(race: AgainstLastTime): Pair<String, Boolean>? {
    val volume = race.volumeKg
    return when {
        volume != null && abs(volume) >= HALF_A_KILO -> {
            val text = volumeText(abs(volume))
            if (volume > 0) {
                stringResource(Res.string.race_volume_more, text) to true
            } else {
                stringResource(Res.string.race_volume_less, text) to false
            }
        }

        volume == null && race.reps > 0 -> {
            pluralStringResource(Res.plurals.race_reps_more, race.reps, race.reps) to true
        }

        volume == null && race.reps < 0 -> {
            pluralStringResource(Res.plurals.race_reps_fewer, -race.reps, -race.reps) to false
        }

        else -> {
            null
        }
    }
}

@Composable
private fun RaceText(
    text: String,
    style: TextStyle,
) {
    RollingText(text = AnnotatedString(text), style = style)
}

/**
 * How a set did against last time, after last time's values in the Previous column: a triangle up or down,
 * or "=" for level. With [pop], as when the set has just been ticked off, it pops in like the check.
 */
@Composable
internal fun VersusMark(
    versus: VersusLastTime,
    pop: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(if (pop) 0f else 1f) }
    LaunchedEffect(Unit) { if (pop) scale.animateTo(1f, MarkPop) }
    val popping =
        modifier.padding(start = MARK_GAP.dp).graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    if (versus == VersusLastTime.Level) {
        val description = stringResource(Res.string.cd_level_with_last_time)
        Text(
            text = LEVEL,
            style = MaterialTheme.typography.bodyMedium,
            color = quietColor(),
            modifier = popping.semantics { contentDescription = description },
        )
    } else {
        VersusIcon(
            versus = versus,
            tint = if (versus == VersusLastTime.Ahead) aheadColor() else quietColor(),
            modifier = popping,
            described = true,
        )
    }
}

/** A triangle, up for ahead and down for behind. */
@Composable
private fun VersusIcon(
    versus: VersusLastTime,
    tint: Color,
    modifier: Modifier = Modifier,
    described: Boolean = false,
) {
    val ahead = versus == VersusLastTime.Ahead
    Icon(
        painter = painterResource(LioraIcons.DropDown),
        contentDescription =
            if (described) {
                stringResource(if (ahead) Res.string.cd_ahead_of_last_time else Res.string.cd_behind_last_time)
            } else {
                null
            },
        tint = tint,
        modifier = modifier.size(TRIANGLE_SIZE.dp).rotate(if (ahead) UPSIDE_DOWN else 0f),
    )
}

@Composable
private fun aheadColor(): Color = LioraTheme.colors.completed

@Composable
private fun quietColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/** It overshoots as it settles, like the set's check. */
private val MarkPop = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)

private const val TRIANGLE_SIZE = 22
private const val MARK_GAP = 4
private const val DIVIDER_HEIGHT = 16
private const val UPSIDE_DOWN = 180f
private const val LEVEL = "="

/** Less than this doesn't show in whole kilograms. */
private const val HALF_A_KILO = 0.5
