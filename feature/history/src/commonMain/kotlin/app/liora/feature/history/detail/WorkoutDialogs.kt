package app.liora.feature.history.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.liora.feature.history.resources.Res
import app.liora.feature.history.resources.delete_body
import app.liora.feature.history.resources.delete_title
import app.liora.feature.history.resources.dialog_cancel
import app.liora.feature.history.resources.dialog_delete
import app.liora.feature.history.resources.dialog_save
import app.liora.feature.history.resources.in_progress_body
import app.liora.feature.history.resources.in_progress_resume
import app.liora.feature.history.resources.in_progress_title
import app.liora.feature.history.resources.save_routine_body
import app.liora.feature.history.resources.save_routine_name
import app.liora.feature.history.resources.save_routine_title
import org.jetbrains.compose.resources.stringResource

internal class WorkoutDialogActions(
    val onSaveAsRoutine: (name: String) -> Unit,
    val onDelete: () -> Unit,
    val onResumeWorkout: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
internal fun WorkoutDialogHost(
    dialog: WorkoutDialog?,
    defaultRoutineName: String,
    actions: WorkoutDialogActions,
) {
    when (dialog) {
        WorkoutDialog.SaveAsRoutine -> {
            SaveAsRoutineDialog(defaultRoutineName, actions.onSaveAsRoutine, actions.onDismiss)
        }

        WorkoutDialog.Delete -> {
            AlertDialog(
                onDismissRequest = actions.onDismiss,
                title = { Text(stringResource(Res.string.delete_title)) },
                text = { Text(stringResource(Res.string.delete_body)) },
                confirmButton = {
                    TextButton(
                        onClick = actions.onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(Res.string.dialog_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = actions.onDismiss) { Text(stringResource(Res.string.dialog_cancel)) }
                },
            )
        }

        WorkoutDialog.WorkoutInProgress -> {
            AlertDialog(
                onDismissRequest = actions.onDismiss,
                title = { Text(stringResource(Res.string.in_progress_title)) },
                text = { Text(stringResource(Res.string.in_progress_body)) },
                confirmButton = {
                    TextButton(
                        onClick = actions.onResumeWorkout,
                    ) { Text(stringResource(Res.string.in_progress_resume)) }
                },
                dismissButton = {
                    TextButton(onClick = actions.onDismiss) { Text(stringResource(Res.string.dialog_cancel)) }
                },
            )
        }

        null -> {
            Unit
        }
    }
}

/** Names the new routine, starting from the workout's own name. */
@Composable
private fun SaveAsRoutineDialog(
    initialName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.save_routine_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.save_routine_body))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.save_routine_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(Res.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) }
        },
    )
}
