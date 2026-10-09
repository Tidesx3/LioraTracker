package app.liora.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.domain.BigJump
import app.liora.core.domain.ProgressionReason
import app.liora.core.ui.resources.Res
import app.liora.core.ui.resources.progression_below_twice
import app.liora.core.ui.resources.progression_big_jump
import app.liora.core.ui.resources.progression_first_time
import app.liora.core.ui.resources.progression_keep_going
import app.liora.core.ui.resources.progression_keep_going_weakest
import app.liora.core.ui.resources.progression_nothing_heavier
import app.liora.core.ui.resources.progression_top_reached
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Why progression set an exercise's targets, under its name in the logger and on routine detail: an arrow
 * when the weight moves a step, and a warning when the jump is big enough for a second look.
 */
@Composable
fun ProgressionNote(
    reason: ProgressionReason,
    modifier: Modifier = Modifier,
    bigJump: BigJump? = null,
) {
    val colors = MaterialTheme.colorScheme
    val icon =
        when {
            bigJump != null -> LioraIcons.Warning
            reason is ProgressionReason.TopReached -> LioraIcons.StepUp
            reason is ProgressionReason.BelowTwice -> LioraIcons.StepDown
            else -> null
        }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (bigJump != null) colors.error else colors.primary,
                modifier = Modifier.padding(top = 1.dp).size(16.dp),
            )
        }
        Column {
            Text(
                text = progressionReasonText(reason),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            if (bigJump != null) {
                Text(
                    text =
                        stringResource(
                            Res.string.progression_big_jump,
                            weightText(bigJump.target),
                            weightText(bigJump.heaviestLately),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
            }
        }
    }
}

/** [reason] in words, weights in the user's units and numbers as the locale writes them. */
@Composable
fun progressionReasonText(reason: ProgressionReason): String {
    val numbers = rememberNumberFormatter()
    return when (reason) {
        ProgressionReason.FirstTime -> {
            stringResource(Res.string.progression_first_time)
        }

        is ProgressionReason.TopReached -> {
            pluralStringResource(
                Res.plurals.progression_top_reached,
                reason.sets,
                numbers.format(reason.reps),
                reason.sets,
                weightText(reason.from),
                weightText(reason.to),
            )
        }

        is ProgressionReason.NothingHeavier -> {
            stringResource(Res.string.progression_nothing_heavier, weightText(reason.weight))
        }

        is ProgressionReason.KeepGoing -> {
            val reps = reason.reps.joinToString(", ") { numbers.format(it) }
            val set = reason.set
            if (set != null) {
                stringResource(Res.string.progression_keep_going, reps, weightText(reason.weight), set)
            } else {
                stringResource(Res.string.progression_keep_going_weakest, reps, weightText(reason.weight))
            }
        }

        is ProgressionReason.BelowTwice -> {
            stringResource(
                Res.string.progression_below_twice,
                numbers.format(reason.reps),
                weightText(reason.from),
                weightText(reason.to),
            )
        }
    }
}
