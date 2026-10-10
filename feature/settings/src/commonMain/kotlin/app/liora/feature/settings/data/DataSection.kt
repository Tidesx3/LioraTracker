package app.liora.feature.settings.data

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liora.core.data.health.HealthAccess
import app.liora.core.data.sync.SyncState
import app.liora.core.designsystem.component.LioraCardDefaults
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.domain.CsvContent
import app.liora.feature.settings.ChoiceDialog
import app.liora.feature.settings.SettingsGroup
import app.liora.feature.settings.health.healthStatusText
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.backup_body
import app.liora.feature.settings.resources.backup_exercises
import app.liora.feature.settings.resources.backup_measurements
import app.liora.feature.settings.resources.backup_photos
import app.liora.feature.settings.resources.backup_routines
import app.liora.feature.settings.resources.backup_title
import app.liora.feature.settings.resources.backup_workouts
import app.liora.feature.settings.resources.csv_body
import app.liora.feature.settings.resources.csv_both
import app.liora.feature.settings.resources.csv_both_body
import app.liora.feature.settings.resources.csv_choice_title
import app.liora.feature.settings.resources.csv_measurements
import app.liora.feature.settings.resources.csv_measurements_body
import app.liora.feature.settings.resources.csv_title
import app.liora.feature.settings.resources.csv_workouts
import app.liora.feature.settings.resources.csv_workouts_body
import app.liora.feature.settings.resources.dialog_cancel
import app.liora.feature.settings.resources.health_title
import app.liora.feature.settings.resources.import_row_body
import app.liora.feature.settings.resources.import_row_title
import app.liora.feature.settings.resources.message_backup_saved
import app.liora.feature.settings.resources.message_body_csv_saved
import app.liora.feature.settings.resources.message_both_csv_saved
import app.liora.feature.settings.resources.message_csv_saved
import app.liora.feature.settings.resources.message_newer_backup
import app.liora.feature.settings.resources.message_not_a_backup
import app.liora.feature.settings.resources.message_nothing_new
import app.liora.feature.settings.resources.message_read_failed
import app.liora.feature.settings.resources.message_restored
import app.liora.feature.settings.resources.message_save_failed
import app.liora.feature.settings.resources.restore_body
import app.liora.feature.settings.resources.restore_confirm
import app.liora.feature.settings.resources.restore_empty
import app.liora.feature.settings.resources.restore_keeps
import app.liora.feature.settings.resources.restore_made
import app.liora.feature.settings.resources.restore_question
import app.liora.feature.settings.resources.restore_title
import app.liora.feature.settings.resources.section_data
import app.liora.feature.settings.resources.sync_row_title
import app.liora.feature.settings.resources.working_backup
import app.liora.feature.settings.resources.working_csv
import app.liora.feature.settings.resources.working_read
import app.liora.feature.settings.resources.working_restore
import app.liora.feature.settings.sync.syncStatusText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What the Data section's rows and the restore dialog do. */
internal class DataActions(
    val onBackUp: () -> Unit,
    val onRestore: () -> Unit,
    val onImport: () -> Unit,
    val onExportCsv: (CsvContent) -> Unit,
    val onConfirmRestore: () -> Unit,
    val onCancelRestore: () -> Unit,
    val onMessageShown: () -> Unit,
)

/**
 * Sync, then backing up, restoring and exporting; the row at work shows it, and the others wait. [sync] is
 * null until it's known. Exporting asks first what goes in the file.
 */
@Composable
internal fun DataSection(
    working: DataWork?,
    sync: SyncState?,
    health: HealthAccess?,
    today: LocalDate,
    onOpenSync: () -> Unit,
    onOpenHealth: () -> Unit,
    actions: DataActions,
    modifier: Modifier = Modifier,
) {
    var choosingCsv by rememberSaveable { mutableStateOf(false) }
    // The last choice stays picked for next time.
    var csvContent by rememberSaveable { mutableStateOf(CsvContent.Workouts) }
    SettingsGroup(stringResource(Res.string.section_data), modifier) {
        ListItem(
            headlineContent = { Text(stringResource(Res.string.sync_row_title)) },
            supportingContent = {
                Text(
                    text = sync?.let { syncStatusText(it, today) }.orEmpty(),
                    color =
                        if (sync?.needsYou == true) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            },
            colors = LioraCardDefaults.listItemColors(),
            modifier = Modifier.clickable(onClick = onOpenSync),
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.health_title)) },
            supportingContent = { Text(health?.let { healthStatusText(it) }.orEmpty()) },
            colors = LioraCardDefaults.listItemColors(),
            modifier = Modifier.clickable(onClick = onOpenHealth),
        )
        DataRow(
            title = stringResource(Res.string.backup_title),
            body = stringResource(Res.string.backup_body),
            busy = working == DataWork.BackingUp,
            busyText = stringResource(Res.string.working_backup),
            enabled = working == null,
            onClick = actions.onBackUp,
        )
        DataRow(
            title = stringResource(Res.string.restore_title),
            body = stringResource(Res.string.restore_body),
            busy = working == DataWork.Reading || working == DataWork.Restoring,
            busyText =
                stringResource(
                    if (working ==
                        DataWork.Reading
                    ) {
                        Res.string.working_read
                    } else {
                        Res.string.working_restore
                    },
                ),
            enabled = working == null,
            onClick = actions.onRestore,
        )
        DataRow(
            title = stringResource(Res.string.import_row_title),
            body = stringResource(Res.string.import_row_body),
            busy = false,
            busyText = "",
            enabled = working == null,
            onClick = actions.onImport,
        )
        DataRow(
            title = stringResource(Res.string.csv_title),
            body = stringResource(Res.string.csv_body),
            busy = working == DataWork.Exporting,
            busyText = stringResource(Res.string.working_csv),
            enabled = working == null,
            onClick = { choosingCsv = true },
        )
    }
    if (choosingCsv) {
        ChoiceDialog(
            title = stringResource(Res.string.csv_choice_title),
            options = CsvContent.entries,
            selected = csvContent,
            label = { stringResource(it.label) },
            description = { stringResource(it.description) },
            onChoose = { content ->
                choosingCsv = false
                csvContent = content
                actions.onExportCsv(content)
            },
            onDismiss = { choosingCsv = false },
        )
    }
}

private val CsvContent.label: StringResource
    get() =
        when (this) {
            CsvContent.Workouts -> Res.string.csv_workouts
            CsvContent.Body -> Res.string.csv_measurements
            CsvContent.Both -> Res.string.csv_both
        }

private val CsvContent.description: StringResource
    get() =
        when (this) {
            CsvContent.Workouts -> Res.string.csv_workouts_body
            CsvContent.Body -> Res.string.csv_measurements_body
            CsvContent.Both -> Res.string.csv_both_body
        }

@Composable
private fun DataRow(
    title: String,
    body: String,
    busy: Boolean,
    busyText: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(if (busy) busyText else body) },
        trailingContent =
            if (busy) {
                { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
        colors = LioraCardDefaults.listItemColors(),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

/** What a backup holds and when it was made, before it's restored. */
@Composable
internal fun RestoreDialog(
    summary: BackupSummary,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dates = rememberDateFormatter()
    val made = summary.exportedAt.toLocalDateTime(TimeZone.currentSystemDefault())
    val counts =
        listOfNotNull(
            summary.workouts.counted(Res.plurals.backup_workouts),
            summary.routines.counted(Res.plurals.backup_routines),
            summary.customExercises.counted(Res.plurals.backup_exercises),
            summary.measurements.counted(Res.plurals.backup_measurements),
            summary.photos.counted(Res.plurals.backup_photos),
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.restore_question)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.restore_made, dates.longDate(made.date), dates.time(made.time)))
                Column {
                    if (counts.isEmpty()) Text(stringResource(Res.string.restore_empty))
                    counts.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
                Text(stringResource(Res.string.restore_keeps))
            }
        },
        confirmButton = { TextButton(onClick = onRestore) { Text(stringResource(Res.string.restore_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

/** "12 workouts", or nothing for none. */
@Composable
private fun Int.counted(plural: PluralStringResource): String? =
    takeIf { it > 0 }?.let { pluralStringResource(plural, it, it) }

internal fun DataMessage.text(): StringResource =
    when (this) {
        DataMessage.BackupSaved -> Res.string.message_backup_saved
        DataMessage.Restored -> Res.string.message_restored
        DataMessage.NothingNew -> Res.string.message_nothing_new
        DataMessage.CsvSaved -> Res.string.message_csv_saved
        DataMessage.BodyCsvSaved -> Res.string.message_body_csv_saved
        DataMessage.BothCsvSaved -> Res.string.message_both_csv_saved
        DataMessage.SaveFailed -> Res.string.message_save_failed
        DataMessage.ReadFailed -> Res.string.message_read_failed
        DataMessage.NotABackup -> Res.string.message_not_a_backup
        DataMessage.NewerBackup -> Res.string.message_newer_backup
    }
