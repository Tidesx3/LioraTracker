package app.liora.feature.logger

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.dialog_cancel
import app.liora.feature.logger.resources.dialog_save
import app.liora.feature.logger.resources.logger_discard_body
import app.liora.feature.logger.resources.logger_discard_cancel
import app.liora.feature.logger.resources.logger_discard_confirm
import app.liora.feature.logger.resources.logger_discard_title
import app.liora.feature.logger.resources.menu_reorder
import app.liora.feature.logger.resources.rename_title
import app.liora.feature.logger.resources.reorder_done
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal enum class LoggerDialog {
    Rename,
    Discard,
    Reorder,
    Finish,
    Gym,

    // A finished workout being corrected.
    Date,
    Start,
    End,
    DropOpenSets,
    DeleteEmpty,
}

internal class LoggerDialogActions(
    val onRename: (String) -> Unit,
    val onDiscard: () -> Unit,
    val onReorder: (workoutExerciseIds: List<String>) -> Unit,
    val onFinish: (updateRoutine: Boolean) -> Unit,
    val onUseGym: (gymId: String) -> Unit,
    val corrections: CorrectionActions,
    val onDismiss: () -> Unit,
)

@Composable
internal fun LoggerDialogHost(
    dialog: LoggerDialog?,
    state: LoggerUiState.Active,
    actions: LoggerDialogActions,
) {
    val onDismiss = actions.onDismiss
    when (dialog) {
        LoggerDialog.Rename -> {
            RenameDialog(
                name = state.workout.name.orEmpty(),
                onRename = {
                    onDismiss()
                    actions.onRename(it)
                },
                onDismiss = onDismiss,
            )
        }

        LoggerDialog.Discard -> {
            DiscardDialog(
                onDiscard = {
                    onDismiss()
                    actions.onDiscard()
                },
                onDismiss = onDismiss,
            )
        }

        LoggerDialog.Reorder -> {
            ReorderSheet(
                exercises = state.workout.exercises.map { it.id to state.exercises[it.exerciseId]?.name.orEmpty() },
                onDone = { order ->
                    onDismiss()
                    actions.onReorder(order)
                },
                onDismiss = onDismiss,
            )
        }

        LoggerDialog.Finish -> {
            FinishSheet(
                summary = state.finishSummary(),
                startedAt = state.workout.startedAt,
                onFinish = { updateRoutine ->
                    onDismiss()
                    actions.onFinish(updateRoutine)
                },
                onDiscard = {
                    onDismiss()
                    actions.onDiscard()
                },
                onDismiss = onDismiss,
            )
        }

        LoggerDialog.Gym -> {
            GymDialog(
                gyms = state.gyms,
                inUse = state.gym,
                onUse = { gymId ->
                    onDismiss()
                    actions.onUseGym(gymId)
                },
                onDismiss = onDismiss,
            )
        }

        LoggerDialog.Date,
        LoggerDialog.Start,
        LoggerDialog.End,
        LoggerDialog.DropOpenSets,
        LoggerDialog.DeleteEmpty,
        -> {
            CorrectionDialogHost(dialog, state, actions.corrections, onDismiss)
        }

        null -> {
            Unit
        }
    }
}

@Composable
private fun RenameDialog(
    name: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = { TextButton(onClick = { onRename(text) }) { Text(stringResource(Res.string.dialog_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

@Composable
private fun DiscardDialog(
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.logger_discard_title)) },
        text = { Text(stringResource(Res.string.logger_discard_body)) },
        confirmButton = {
            TextButton(
                onClick = onDiscard,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.logger_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.logger_discard_cancel)) }
        },
    )
}

/**
 * Exercises by name only, dragged into a new order. The order is kept here while dragging and saved
 * in one go, so the list doesn't jump while the database catches up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReorderSheet(
    exercises: List<Pair<String, String>>,
    onDone: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var order by remember { mutableStateOf(exercises) }
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.menu_reorder),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { onDone(order.map { it.first }) }) { Text(stringResource(Res.string.reorder_done)) }
        }
        LazyColumn(state = listState, modifier = Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
            items(order, key = { it.first }) { (id, name) ->
                ReorderableItem(reorderState, key = id) { dragging ->
                    Surface(
                        color =
                            with(
                                MaterialTheme.colorScheme,
                            ) { if (dragging) surfaceContainerHighest else surfaceContainerLow },
                        shadowElevation = if (dragging) 8.dp else 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                Icon(painterResource(LioraIcons.DragHandle), contentDescription = null)
                            }
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
