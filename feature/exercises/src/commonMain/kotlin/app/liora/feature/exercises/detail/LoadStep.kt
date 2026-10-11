package app.liora.feature.exercises.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.LioraAlertDialog
import app.liora.core.designsystem.component.LioraCard
import app.liora.core.designsystem.component.LioraCardDefaults
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.model.Mass
import app.liora.core.model.WeightUnit
import app.liora.core.ui.LocalUnits
import app.liora.core.ui.weightText
import app.liora.feature.exercises.resources.Res
import app.liora.feature.exercises.resources.cd_load_step_down
import app.liora.feature.exercises.resources.cd_load_step_up
import app.liora.feature.exercises.resources.detail_cancel
import app.liora.feature.exercises.resources.detail_load_step
import app.liora.feature.exercises.resources.detail_load_step_body
import app.liora.feature.exercises.resources.detail_load_step_gym
import app.liora.feature.exercises.resources.detail_load_step_own
import app.liora.feature.exercises.resources.detail_load_step_reset
import app.liora.feature.exercises.resources.detail_load_step_save
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * How much a step adds on a machine or cable: the gym's stack step ([gymStep]) unless it has its own
 * ([ownStep]). Progression and the logger's ± go up and down by it. Tapping it opens a stepper.
 */
@Composable
internal fun LoadStepRow(
    ownStep: Mass?,
    gymStep: Mass?,
    onChange: (Mass?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val value =
        when {
            ownStep != null -> stringResource(Res.string.detail_load_step_own, weightText(ownStep))
            gymStep != null -> stringResource(Res.string.detail_load_step_gym, weightText(gymStep))
            else -> ""
        }
    LioraCard(modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(stringResource(Res.string.detail_load_step)) },
            supportingContent = { Text(value) },
            colors = LioraCardDefaults.listItemColors(),
            modifier = Modifier.clickable { editing = true },
        )
    }
    if (editing) {
        LoadStepDialog(
            start = ownStep ?: gymStep ?: Mass(DEFAULT_STEP_KG),
            onSave = {
                editing = false
                onChange(it)
            },
            onDismiss = { editing = false },
        )
    }
}

/** − and + in the user's unit (half a kilogram, or a pound), and a way back to the gym's step. */
@Composable
private fun LoadStepDialog(
    start: Mass,
    onSave: (Mass?) -> Unit,
    onDismiss: () -> Unit,
) {
    val unit = LocalUnits.current.weight
    val increment = if (unit == WeightUnit.Pound) 1.0 else HALF_KG
    // Kept in the unit shown, on the stepper's own grid.
    var value by rememberSaveable { mutableDoubleStateOf(snap(start.inUnit(unit), increment)) }
    LioraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.detail_load_step)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.detail_load_step_body), style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LioraIconButton(
                        LioraIcons.Decrease,
                        stringResource(Res.string.cd_load_step_down),
                        { value = (value - increment).coerceAtLeast(increment) },
                    )
                    Text(
                        text = weightText(Mass.of(value, unit)),
                        style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    LioraIconButton(
                        LioraIcons.Add,
                        stringResource(Res.string.cd_load_step_up),
                        { value = (value + increment).coerceAtMost(MAX_STEPS * increment) },
                    )
                }
                TextButton(onClick = { onSave(null) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(Res.string.detail_load_step_reset))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(Mass.of(value, unit)) },
            ) { Text(stringResource(Res.string.detail_load_step_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.detail_cancel)) } },
    )
}

/** [value] on the stepper's grid of [increment]s, at least one. */
private fun snap(
    value: Double,
    increment: Double,
): Double = ((value / increment).roundToInt().coerceAtLeast(1)) * increment

private const val HALF_KG = 0.5
private const val DEFAULT_STEP_KG = 5.0

/** A hundred steps up: 50 kg or 100 lb, more than any stack moves at once. */
private const val MAX_STEPS = 100
