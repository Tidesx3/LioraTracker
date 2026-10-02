package app.liora.feature.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.domain.PlateLoad
import app.liora.core.model.GymProfile
import app.liora.core.model.WeightUnit
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.cd_gym
import app.liora.feature.logger.resources.dialog_cancel
import app.liora.feature.logger.resources.gym_title
import app.liora.feature.logger.resources.plates_bar_only
import app.liora.feature.logger.resources.plates_per_side
import app.liora.feature.logger.resources.plates_per_side_total
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Above the number pad: the plates on each side of the bar for the set's weight, in the gym's own unit,
 * and, with two or more gyms, which gym's equipment weights round to, to switch. Shows nothing when
 * neither applies.
 */
@Composable
internal fun PadInfo(
    plates: PlateLoad?,
    takesPlates: Boolean,
    gym: GymProfile,
    canSwitchGym: Boolean,
    onSwitchGym: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!takesPlates && !canSwitchGym) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp).heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (takesPlates) platesText(plates, gym.unit) else "",
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).testTag(LoggerTags.PLATES),
            )
            if (canSwitchGym) {
                val description = stringResource(Res.string.cd_gym, gym.name)
                TextButton(onClick = onSwitchGym, modifier = Modifier.semantics { contentDescription = description }) {
                    Text(gym.name, maxLines = 1)
                    Icon(painterResource(LioraIcons.DropDown), contentDescription = null)
                }
            }
        }
    }
}

/** "Per side: 25 · 15 · 1.25 kg", or with the total when the weight can't be loaded exactly. */
@Composable
private fun platesText(
    plates: PlateLoad?,
    unit: WeightUnit,
): String {
    if (plates == null) return ""
    if (plates.perSide.isEmpty()) return stringResource(Res.string.plates_bar_only)
    val numbers = rememberNumberFormatter()
    val perSide = plates.perSide.joinToString(" · ") { numbers.format(it) } + " ${unit.symbol}"
    return if (plates.isExact) {
        stringResource(Res.string.plates_per_side, perSide)
    } else {
        stringResource(Res.string.plates_per_side_total, perSide, "${numbers.format(plates.total)} ${unit.symbol}")
    }
}

/** The gyms set up; picking one rounds weights to its equipment from now on. */
@Composable
internal fun GymDialog(
    gyms: List<GymProfile>,
    inUse: GymProfile,
    onUse: (gymId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.gym_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                gyms.forEach { gym ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onUse(gym.id) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = gym.id == inUse.id, onClick = { onUse(gym.id) })
                        Text(gym.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}
