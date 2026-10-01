package app.liora.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.dialog_cancel
import app.liora.feature.logger.resources.dialog_ok
import app.liora.feature.logger.resources.edit_delete
import app.liora.feature.logger.resources.edit_empty_body
import app.liora.feature.logger.resources.edit_empty_title
import app.liora.feature.logger.resources.edit_end
import app.liora.feature.logger.resources.edit_end_title
import app.liora.feature.logger.resources.edit_keep_editing
import app.liora.feature.logger.resources.edit_open_confirm
import app.liora.feature.logger.resources.edit_open_sets
import app.liora.feature.logger.resources.edit_open_title
import app.liora.feature.logger.resources.edit_start
import app.liora.feature.logger.resources.edit_start_title
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

// Correcting a finished workout from history: its date and times, and what happens on leaving it.

/** What a finished workout's own dialogs do. */
internal class CorrectionActions(
    val onMoveTo: (LocalDate) -> Unit,
    val onStartAt: (LocalTime) -> Unit,
    val onEndAt: (LocalTime) -> Unit,
    /** Drops the sets that aren't logged and closes. */
    val onFinish: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * What leaving a finished workout asks first: about sets that aren't logged, or, with none logged at
 * all, about deleting it. Null when it can just close.
 */
internal fun askBeforeLeaving(state: LoggerUiState.Active): LoggerDialog? =
    when {
        state.completedSets == 0 -> LoggerDialog.DeleteEmpty
        state.openSets > 0 -> LoggerDialog.DropOpenSets
        else -> null
    }

@Composable
internal fun CorrectionDialogHost(
    dialog: LoggerDialog,
    state: LoggerUiState.Active,
    actions: CorrectionActions,
    onDismiss: () -> Unit,
) {
    val zone = TimeZone.currentSystemDefault()
    val start = state.workout.startedAt.toLocalDateTime(zone)
    val end = state.endedAt?.toLocalDateTime(zone) ?: return
    val dismissThen = { action: () -> Unit ->
        onDismiss()
        action()
    }
    when (dialog) {
        LoggerDialog.Date -> {
            WorkoutDatePicker(start.date, onPick = { dismissThen { actions.onMoveTo(it) } }, onDismiss = onDismiss)
        }

        LoggerDialog.Start -> {
            WorkoutTimePicker(
                ended = false,
                time = start.time,
                onPick = { dismissThen { actions.onStartAt(it) } },
                onDismiss = onDismiss,
            )
        }

        LoggerDialog.End -> {
            WorkoutTimePicker(
                ended = true,
                time = end.time,
                onPick = { dismissThen { actions.onEndAt(it) } },
                onDismiss = onDismiss,
            )
        }

        LoggerDialog.DropOpenSets -> {
            DropOpenSetsDialog(state.openSets, onDrop = { dismissThen(actions.onFinish) }, onDismiss = onDismiss)
        }

        LoggerDialog.DeleteEmpty -> {
            DeleteEmptyWorkoutDialog(onDelete = { dismissThen(actions.onDelete) }, onDismiss = onDismiss)
        }

        else -> {
            Unit
        }
    }
}

/** A finished workout's day, start and end, each a chip that opens its picker. */
@Composable
internal fun WorkoutTimeChips(
    startedAt: Instant,
    endedAt: Instant,
    onShowDialog: (LoggerDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    val zone = TimeZone.currentSystemDefault()
    val start = startedAt.toLocalDateTime(zone)
    val end = endedAt.toLocalDateTime(zone)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { onShowDialog(LoggerDialog.Date) },
            label = { Text(dates.longDate(start.date)) },
            leadingIcon = {
                Icon(painterResource(LioraIcons.Calendar), contentDescription = null, modifier = Modifier.size(18.dp))
            },
            modifier = Modifier.testTag(LoggerTags.DATE),
        )
        // Start and end stay together when a narrow screen wraps the row.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { onShowDialog(LoggerDialog.Start) },
                label = { Text(stringResource(Res.string.edit_start, dates.time(start.time))) },
            )
            AssistChip(
                onClick = { onShowDialog(LoggerDialog.End) },
                label = { Text(stringResource(Res.string.edit_end, dates.time(end.time))) },
            )
        }
    }
}

/** Picks the day a workout happened; days still to come can't have one. */
@Composable
internal fun WorkoutDatePicker(
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The picker works in UTC midnights.
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()).utcMillis() }
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = date.utcMillis(),
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = state.selectedDateMillis
                    if (picked != null) onPick(Instant.fromEpochMilliseconds(picked).toLocalDateTime(TimeZone.UTC).date)
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text(stringResource(Res.string.dialog_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

private fun LocalDate.utcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/** Picks when a workout started or ended, on a clock face that follows the phone's 12/24-hour setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutTimePicker(
    ended: Boolean,
    time: LocalTime,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (ended) Res.string.edit_end_title else Res.string.edit_start_title)) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime(state.hour, state.minute)) }) {
                Text(stringResource(Res.string.dialog_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    ) {
        TimePicker(state = state)
    }
}

/** Before leaving: sets that aren't logged get dropped, as when a workout is finished. */
@Composable
internal fun DropOpenSetsDialog(
    openSets: Int,
    onDrop: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit_open_title)) },
        text = { Text(pluralStringResource(Res.plurals.edit_open_sets, openSets, openSets)) },
        confirmButton = {
            TextButton(onClick = onDrop, modifier = Modifier.testTag(LoggerTags.CORRECTIONS_CONFIRM)) {
                Text(stringResource(Res.string.edit_open_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.edit_keep_editing)) } },
    )
}

/** Before leaving a finished workout with nothing logged: it can go, or get its sets back. */
@Composable
internal fun DeleteEmptyWorkoutDialog(
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit_empty_title)) },
        text = { Text(stringResource(Res.string.edit_empty_body)) },
        confirmButton = {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag(LoggerTags.CORRECTIONS_CONFIRM),
            ) {
                Text(stringResource(Res.string.edit_delete))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.edit_keep_editing)) } },
    )
}
