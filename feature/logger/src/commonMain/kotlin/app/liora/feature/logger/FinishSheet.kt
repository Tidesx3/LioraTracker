package app.liora.feature.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.Stat
import app.liora.core.designsystem.component.StatMotion
import app.liora.core.designsystem.component.StatRow
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.designsystem.util.rememberElapsedTime
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.domain.RoutineChange
import app.liora.core.ui.volumeText
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.finish_empty_body
import app.liora.feature.logger.resources.finish_empty_title
import app.liora.feature.logger.resources.finish_open_sets
import app.liora.feature.logger.resources.finish_title
import app.liora.feature.logger.resources.finish_update_routine
import app.liora.feature.logger.resources.finish_update_routine_changes
import app.liora.feature.logger.resources.logger_discard_cancel
import app.liora.feature.logger.resources.logger_discard_confirm
import app.liora.feature.logger.resources.logger_finish
import app.liora.feature.logger.resources.logger_stat_duration
import app.liora.feature.logger.resources.logger_stat_sets
import app.liora.feature.logger.resources.logger_stat_volume
import app.liora.feature.logger.resources.routine_change_added
import app.liora.feature.logger.resources.routine_change_dropped
import app.liora.feature.logger.resources.routine_change_order
import app.liora.feature.logger.resources.routine_change_replaced
import app.liora.feature.logger.resources.routine_change_sets
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Before a workout ends: what it adds up to, what gets dropped, and the choice to carry today's numbers
 * into the routine it came from. With nothing logged it offers to discard instead. The records it set
 * are celebrated once it's saved, on its summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinishSheet(
    summary: FinishSummary,
    startedAt: Instant,
    onFinish: (updateRoutine: Boolean) -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    var updateRoutine by rememberSaveable { mutableStateOf(true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (summary.completedSets == 0) {
                Text(stringResource(Res.string.finish_empty_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.finish_empty_body), style = MaterialTheme.typography.bodyLarge)
                Buttons(
                    confirm = stringResource(Res.string.logger_discard_confirm),
                    destructive = true,
                    onConfirm = onDiscard,
                    onDismiss = onDismiss,
                )
                return@Column
            }
            Text(stringResource(Res.string.finish_title), style = MaterialTheme.typography.headlineSmall)
            Totals(summary, startedAt)
            if (summary.openSets > 0) {
                Text(
                    text = pluralStringResource(Res.plurals.finish_open_sets, summary.openSets, summary.openSets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            summary.routineToUpdate?.let { name ->
                RoutineUpdateChoice(name, summary, checked = updateRoutine, onCheckedChange = { updateRoutine = it })
            }
            Buttons(
                confirm = stringResource(Res.string.logger_finish),
                destructive = false,
                onConfirm = { onFinish(summary.routineToUpdate != null && updateRoutine) },
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * The choice to update the routine. When its shape would change (an exercise swapped, added or dropped,
 * a different number of sets, a new order) each change gets a row, so it's clear what ticking it does.
 */
@Composable
private fun RoutineUpdateChoice(
    name: String,
    summary: FinishSummary,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(
                stringResource(
                    if (summary.routineChanges.isEmpty()) {
                        Res.string.finish_update_routine
                    } else {
                        Res.string.finish_update_routine_changes
                    },
                    name,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        // Lined up under the label, past the checkbox.
        Column(Modifier.padding(start = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            summary.routineChanges.forEach { change ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(change.icon()),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = change.text(summary.exerciseNames),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

private fun RoutineChange.icon(): DrawableResource =
    when (this) {
        is RoutineChange.Replaced -> LioraIcons.Replace
        is RoutineChange.Added -> LioraIcons.Add
        is RoutineChange.Dropped -> LioraIcons.Decrease
        is RoutineChange.SetCount -> LioraIcons.ListView
        RoutineChange.Reordered -> LioraIcons.Reorder
    }

@Composable
private fun RoutineChange.text(names: Map<String, String>): String {
    fun name(id: String) = names[id].orEmpty()
    return when (this) {
        is RoutineChange.Replaced -> {
            stringResource(Res.string.routine_change_replaced, name(from), name(to))
        }

        is RoutineChange.Added -> {
            stringResource(Res.string.routine_change_added, name(exerciseId))
        }

        is RoutineChange.Dropped -> {
            stringResource(Res.string.routine_change_dropped, name(exerciseId))
        }

        is RoutineChange.SetCount -> {
            pluralStringResource(
                Res.plurals.routine_change_sets,
                sets,
                name(exerciseId),
                sets,
                was,
            )
        }

        RoutineChange.Reordered -> {
            stringResource(Res.string.routine_change_order)
        }
    }
}

@Composable
private fun Totals(
    summary: FinishSummary,
    startedAt: Instant,
) {
    val numbers = rememberNumberFormatter()
    val elapsed = rememberElapsedTime(startedAt)
    // The totals roll in as the sheet comes up, the end of the workout counted out.
    StatRow {
        Stat(
            value = elapsed.formatAsClock(),
            label = stringResource(Res.string.logger_stat_duration),
            modifier = Modifier.weight(1f),
            motion = StatMotion.RollIn,
        )
        Stat(
            value = volumeText(summary.volumeKg),
            label = stringResource(Res.string.logger_stat_volume),
            modifier = Modifier.weight(1f),
            motion = StatMotion.RollIn,
        )
        Stat(
            value = numbers.format(summary.completedSets),
            label = stringResource(Res.string.logger_stat_sets),
            modifier = Modifier.weight(1f),
            motion = StatMotion.RollIn,
        )
    }
}

@Composable
private fun Buttons(
    confirm: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.logger_discard_cancel)) }
        Button(
            onClick = onConfirm,
            modifier = Modifier.testTag(LoggerTags.FINISH_CONFIRM),
            colors =
                if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
        ) {
            Text(confirm)
        }
    }
}
