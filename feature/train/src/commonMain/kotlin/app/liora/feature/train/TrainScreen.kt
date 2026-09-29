package app.liora.feature.train

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.model.RoutineFolder
import app.liora.core.navigation.LocalPaneRole
import app.liora.core.navigation.PaneRole
import app.liora.core.ui.currentLanguage
import app.liora.feature.train.resources.Res
import app.liora.feature.train.resources.cd_more
import app.liora.feature.train.resources.cd_settings
import app.liora.feature.train.resources.folder_delete
import app.liora.feature.train.resources.folder_new_routine
import app.liora.feature.train.resources.folder_rename
import app.liora.feature.train.resources.routine_copy_name
import app.liora.feature.train.resources.routine_delete
import app.liora.feature.train.resources.routine_duplicate
import app.liora.feature.train.resources.routine_edit
import app.liora.feature.train.resources.routine_move
import app.liora.feature.train.resources.train_folder_empty
import app.liora.feature.train.resources.train_my_routines
import app.liora.feature.train.resources.train_new_folder
import app.liora.feature.train.resources.train_new_routine
import app.liora.feature.train.resources.train_quick_start
import app.liora.feature.train.resources.train_quick_start_body
import app.liora.feature.train.resources.train_resume
import app.liora.feature.train.resources.train_routine_no_exercises
import app.liora.feature.train.resources.train_routines
import app.liora.feature.train.resources.train_routines_empty_body
import app.liora.feature.train.resources.train_routines_empty_title
import app.liora.feature.train.resources.train_start
import app.liora.feature.train.resources.train_start_empty
import app.liora.feature.train.resources.train_title
import app.liora.feature.train.routine.RoutineDialog
import app.liora.feature.train.routine.RoutineDialogActions
import app.liora.feature.train.routine.RoutineDialogHost
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

internal class TrainNavigationActions(
    val onOpenLogger: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenRoutine: (routineId: String) -> Unit,
    val onNewRoutine: (folderId: String?) -> Unit,
    val onEditRoutine: (routineId: String) -> Unit,
)

@Composable
internal fun TrainScreen(
    selectedRoutineId: String?,
    navigation: TrainNavigationActions,
    modifier: Modifier = Modifier,
    viewModel: TrainViewModel = koinViewModel(),
) {
    val language = currentLanguage()
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TrainContent(
        uiState = uiState,
        // Only a list with its detail pane beside it has a selection to show.
        selectedRoutineId = selectedRoutineId.takeIf { LocalPaneRole.current == PaneRole.List },
        actions =
            TrainActions(
                onStartWorkout = {
                    viewModel.startEmptyWorkout()
                    navigation.onOpenLogger()
                },
                onResumeWorkout = navigation.onOpenLogger,
                onNewRoutine = navigation.onNewRoutine,
                onShowDialog = viewModel::showDialog,
                onOpenSettings = navigation.onOpenSettings,
                routine =
                    RoutineCardActions(
                        onOpen = navigation.onOpenRoutine,
                        onStart = { if (viewModel.startRoutine(it)) navigation.onOpenLogger() },
                        onEdit = navigation.onEditRoutine,
                        onDuplicate = viewModel::duplicateRoutine,
                        onShowDialog = viewModel::showDialog,
                    ),
            ),
        modifier = modifier,
    )

    RoutineDialogHost(
        dialog = uiState.dialog,
        folders = uiState.folders,
        actions =
            RoutineDialogActions(
                onCreateFolder = viewModel::createFolder,
                onRenameFolder = viewModel::renameFolder,
                onDeleteFolder = viewModel::deleteFolder,
                onMoveRoutine = viewModel::moveRoutine,
                onDeleteRoutine = viewModel::deleteRoutine,
                onResumeWorkout = {
                    viewModel.dismissDialog()
                    navigation.onOpenLogger()
                },
                onDismiss = viewModel::dismissDialog,
            ),
    )
}

private class TrainActions(
    val onStartWorkout: () -> Unit,
    val onResumeWorkout: () -> Unit,
    val onNewRoutine: (folderId: String?) -> Unit,
    val onShowDialog: (RoutineDialog) -> Unit,
    val onOpenSettings: () -> Unit,
    val routine: RoutineCardActions,
)

/** What a routine card offers, each taking the routine's id. */
private class RoutineCardActions(
    val onOpen: (String) -> Unit,
    val onStart: (String) -> Unit,
    val onEdit: (String) -> Unit,
    val onDuplicate: (id: String, name: String) -> Unit,
    val onShowDialog: (RoutineDialog) -> Unit,
)

@Composable
private fun TrainContent(
    uiState: TrainUiState,
    selectedRoutineId: String?,
    actions: TrainActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.train_title),
                actions = {
                    LioraIconButton(
                        icon = LioraIcons.Settings,
                        contentDescription = stringResource(Res.string.cd_settings),
                        onClick = actions.onOpenSettings,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.readableWidth(),
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
        ) {
            item(key = "quick-start") {
                QuickStartCard(
                    hasActiveWorkout = uiState.hasActiveWorkout,
                    onStartWorkout = actions.onStartWorkout,
                    onResumeWorkout = actions.onResumeWorkout,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "routines-header") {
                SectionHeader(title = stringResource(Res.string.train_routines))
                RoutineButtons(
                    onNewRoutine = { actions.onNewRoutine(null) },
                    onNewFolder = { actions.onShowDialog(RoutineDialog.NewFolder) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (!uiState.loading && uiState.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        icon = LioraIcons.Train,
                        title = stringResource(Res.string.train_routines_empty_title),
                        body = stringResource(Res.string.train_routines_empty_body),
                    )
                }
            }
            uiState.sections.forEach { section ->
                val folder = section.folder
                if (folder != null) {
                    item(key = "folder-${folder.id}") {
                        FolderHeader(
                            folder = folder,
                            onNewRoutine = { actions.onNewRoutine(folder.id) },
                            onShowDialog = actions.onShowDialog,
                        )
                    }
                } else if (uiState.folders.isNotEmpty() && section.routines.isNotEmpty()) {
                    item(key = "unfiled-header") { FolderTitle(stringResource(Res.string.train_my_routines)) }
                }
                if (folder != null && section.routines.isEmpty()) {
                    item(key = "folder-empty-${folder.id}") {
                        Text(
                            text = stringResource(Res.string.train_folder_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                }
                items(section.routines, key = { it.routine.id }) { card ->
                    RoutineCard(
                        card = card,
                        selected = card.routine.id == selectedRoutineId,
                        actions = actions.routine,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineButtons(
    onNewRoutine: () -> Unit,
    onNewFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onNewRoutine, modifier = Modifier.weight(1f)) {
            Icon(painterResource(LioraIcons.Add), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.train_new_routine), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(onClick = onNewFolder, modifier = Modifier.weight(1f)) {
            Icon(painterResource(LioraIcons.Folder), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.train_new_folder), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FolderTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(LioraIcons.Folder),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun FolderHeader(
    folder: RoutineFolder,
    onNewRoutine: () -> Unit,
    onShowDialog: (RoutineDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    FolderTitle(title = folder.name, modifier = modifier) {
        OverflowMenu(
            items =
                listOf(
                    stringResource(Res.string.folder_new_routine) to onNewRoutine,
                    stringResource(Res.string.folder_rename) to { onShowDialog(RoutineDialog.RenameFolder(folder)) },
                    stringResource(Res.string.folder_delete) to { onShowDialog(RoutineDialog.DeleteFolder(folder)) },
                ),
        )
    }
}

@Composable
private fun RoutineCard(
    card: RoutineCardState,
    selected: Boolean,
    actions: RoutineCardActions,
    modifier: Modifier = Modifier,
) {
    val routine = card.routine
    val copyName = stringResource(Res.string.routine_copy_name, routine.name)
    Card(
        onClick = { actions.onOpen(routine.id) },
        modifier = modifier.fillMaxWidth().semantics { if (selected) this.selected = true },
        colors =
            CardDefaults.cardColors(
                containerColor =
                    with(MaterialTheme.colorScheme) { if (selected) secondaryContainer else surfaceContainer },
            ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                OverflowMenu(
                    items =
                        listOf(
                            stringResource(Res.string.routine_edit) to { actions.onEdit(routine.id) },
                            stringResource(Res.string.routine_duplicate) to
                                { actions.onDuplicate(routine.id, copyName) },
                            stringResource(Res.string.routine_move) to {
                                actions.onShowDialog(RoutineDialog.MoveRoutine(routine.id, routine.folderId))
                            },
                            stringResource(Res.string.routine_delete) to {
                                actions.onShowDialog(RoutineDialog.DeleteRoutine(routine.id))
                            },
                        ),
                )
            }
            Text(
                text = exerciseList(card.exerciseNames),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp),
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { actions.onStart(routine.id) },
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                // The tonal color is the selected card's color; the open routine's button goes solid instead.
                colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
            ) {
                Icon(painterResource(LioraIcons.Play), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.train_start))
            }
        }
    }
}

@Composable
private fun exerciseList(names: List<String>): String =
    if (names.isEmpty()) stringResource(Res.string.train_routine_no_exercises) else names.joinToString(", ")

@Composable
private fun OverflowMenu(
    items: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LioraIconButton(LioraIcons.More, stringResource(Res.string.cd_more), { expanded = true }, modifier)
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        items.forEach { (label, action) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    expanded = false
                    action()
                },
            )
        }
    }
}

@Composable
private fun QuickStartCard(
    hasActiveWorkout: Boolean,
    onStartWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.train_quick_start),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.train_quick_start_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = if (hasActiveWorkout) onResumeWorkout else onStartWorkout,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(
                    painter = painterResource(LioraIcons.Play),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text =
                        stringResource(
                            if (hasActiveWorkout) Res.string.train_resume else Res.string.train_start_empty,
                        ),
                )
            }
        }
    }
}
