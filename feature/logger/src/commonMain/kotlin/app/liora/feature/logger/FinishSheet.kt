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
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.designsystem.util.rememberElapsedTime
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.model.WeightUnit
import app.liora.core.ui.recordLabel
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.finish_empty_body
import app.liora.feature.logger.resources.finish_empty_title
import app.liora.feature.logger.resources.finish_open_sets
import app.liora.feature.logger.resources.finish_records
import app.liora.feature.logger.resources.finish_title
import app.liora.feature.logger.resources.finish_update_routine
import app.liora.feature.logger.resources.logger_discard_cancel
import app.liora.feature.logger.resources.logger_discard_confirm
import app.liora.feature.logger.resources.logger_finish
import app.liora.feature.logger.resources.logger_stat_duration
import app.liora.feature.logger.resources.logger_stat_sets
import app.liora.feature.logger.resources.logger_stat_volume
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Before a workout ends: what it adds up to, the personal records it set, what gets dropped, and the
 * choice to carry today's numbers into the routine it came from. With nothing logged it offers to
 * discard instead.
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
            if (summary.records.isNotEmpty()) Records(summary)
            if (summary.openSets > 0) {
                Text(
                    text = pluralStringResource(Res.plurals.finish_open_sets, summary.openSets, summary.openSets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            summary.routineToUpdate?.let { name ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { updateRoutine = !updateRoutine },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = updateRoutine, onCheckedChange = { updateRoutine = it })
                    Text(
                        stringResource(Res.string.finish_update_routine, name),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
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

@Composable
private fun Totals(
    summary: FinishSummary,
    startedAt: Instant,
) {
    val numbers = rememberNumberFormatter()
    val elapsed = rememberElapsedTime(startedAt)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Total(stringResource(Res.string.logger_stat_duration), elapsed.formatAsClock())
        Total(
            stringResource(Res.string.logger_stat_volume),
            "${numbers.format(summary.volumeKg, maxFractionDigits = 0)} ${WeightUnit.Kilogram.symbol}",
        )
        Total(stringResource(Res.string.logger_stat_sets), numbers.format(summary.completedSets))
    }
}

@Composable
private fun Total(
    label: String,
    value: String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge.tabularNumbers())
    }
}

/** Today's records, gold like the trophy on the sets that set them. */
@Composable
private fun Records(summary: FinishSummary) {
    val count = summary.records.sumOf { it.second.size }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = LioraTheme.colors.personalRecord.copy(alpha = RECORD_BACKGROUND_ALPHA),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(LioraIcons.Record),
                    contentDescription = null,
                    tint = LioraTheme.colors.personalRecord,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = pluralStringResource(Res.plurals.finish_records, count, count),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            summary.records.forEach { (exercise, keys) ->
                Text(
                    text = "$exercise: ${keys.map { recordLabel(it) }.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
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

private const val RECORD_BACKGROUND_ALPHA = 0.14f
