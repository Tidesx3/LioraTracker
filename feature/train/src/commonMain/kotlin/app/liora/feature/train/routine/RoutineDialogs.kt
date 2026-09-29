package app.liora.feature.train.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.liora.core.model.RoutineFolder
import app.liora.feature.train.resources.Res
import app.liora.feature.train.resources.dialog_cancel
import app.liora.feature.train.resources.dialog_create
import app.liora.feature.train.resources.dialog_delete
import app.liora.feature.train.resources.dialog_save
import app.liora.feature.train.resources.folder_delete_body
import app.liora.feature.train.resources.folder_delete_title
import app.liora.feature.train.resources.folder_name
import app.liora.feature.train.resources.in_progress_body
import app.liora.feature.train.resources.in_progress_resume
import app.liora.feature.train.resources.in_progress_title
import app.liora.feature.train.resources.move_no_folder
import app.liora.feature.train.resources.move_title
import app.liora.feature.train.resources.routine_delete_body
import app.liora.feature.train.resources.routine_delete_title
import app.liora.feature.train.resources.train_new_folder
import org.jetbrains.compose.resources.stringResource

/** Dialogs the Train tab and the routine detail share. */
sealed interface RoutineDialog {
    data object NewFolder : RoutineDialog

    data class RenameFolder(
        val folder: RoutineFolder,
    ) : RoutineDialog

    data class DeleteFolder(
        val folder: RoutineFolder,
    ) : RoutineDialog

    data class MoveRoutine(
        val routineId: String,
        val folderId: String?,
    ) : RoutineDialog

    data class DeleteRoutine(
        val routineId: String,
    ) : RoutineDialog

    /** Only one workout runs at a time; starting another asks to finish the current one first. */
    data object WorkoutInProgress : RoutineDialog
}

internal class RoutineDialogActions(
    val onCreateFolder: (name: String) -> Unit = {},
    val onRenameFolder: (id: String, name: String) -> Unit = { _, _ -> },
    val onDeleteFolder: (id: String) -> Unit = {},
    val onMoveRoutine: (routineId: String, folderId: String?) -> Unit,
    val onDeleteRoutine: (routineId: String) -> Unit,
    val onResumeWorkout: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
internal fun RoutineDialogHost(
    dialog: RoutineDialog?,
    folders: List<RoutineFolder>,
    actions: RoutineDialogActions,
) {
    when (dialog) {
        RoutineDialog.NewFolder -> {
            NameDialog(
                title = stringResource(Res.string.train_new_folder),
                initialName = "",
                confirmLabel = stringResource(Res.string.dialog_create),
                onConfirm = actions.onCreateFolder,
                onDismiss = actions.onDismiss,
            )
        }

        is RoutineDialog.RenameFolder -> {
            NameDialog(
                title = stringResource(Res.string.folder_name),
                initialName = dialog.folder.name,
                confirmLabel = stringResource(Res.string.dialog_save),
                onConfirm = { actions.onRenameFolder(dialog.folder.id, it) },
                onDismiss = actions.onDismiss,
            )
        }

        is RoutineDialog.DeleteFolder -> {
            ConfirmDeleteDialog(
                title = stringResource(Res.string.folder_delete_title),
                body = stringResource(Res.string.folder_delete_body),
                onConfirm = { actions.onDeleteFolder(dialog.folder.id) },
                onDismiss = actions.onDismiss,
            )
        }

        is RoutineDialog.MoveRoutine -> {
            MoveToFolderDialog(
                folders = folders,
                currentFolderId = dialog.folderId,
                onMove = { actions.onMoveRoutine(dialog.routineId, it) },
                onDismiss = actions.onDismiss,
            )
        }

        is RoutineDialog.DeleteRoutine -> {
            ConfirmDeleteDialog(
                title = stringResource(Res.string.routine_delete_title),
                body = stringResource(Res.string.routine_delete_body),
                onConfirm = { actions.onDeleteRoutine(dialog.routineId) },
                onDismiss = actions.onDismiss,
            )
        }

        RoutineDialog.WorkoutInProgress -> {
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

@Composable
private fun NameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.folder_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) }
        },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.dialog_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) }
        },
    )
}

@Composable
private fun MoveToFolderDialog(
    folders: List<RoutineFolder>,
    currentFolderId: String?,
    onMove: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.move_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                val options =
                    listOf<Pair<String?, String>>(null to stringResource(Res.string.move_no_folder)) +
                        folders.map { it.id to it.name }
                options.forEach { (id, name) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onMove(id) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = id == currentFolderId, onClick = { onMove(id) })
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) }
        },
    )
}
