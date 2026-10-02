package app.liora.feature.settings.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.model.WeightUnit
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.cd_close
import app.liora.feature.settings.resources.cd_remove_dumbbells
import app.liora.feature.settings.resources.cd_remove_plate
import app.liora.feature.settings.resources.dialog_cancel
import app.liora.feature.settings.resources.gym_add_dumbbells
import app.liora.feature.settings.resources.gym_add_plate
import app.liora.feature.settings.resources.gym_barbell
import app.liora.feature.settings.resources.gym_delete
import app.liora.feature.settings.resources.gym_delete_body
import app.liora.feature.settings.resources.gym_delete_confirm
import app.liora.feature.settings.resources.gym_delete_title
import app.liora.feature.settings.resources.gym_discard
import app.liora.feature.settings.resources.gym_discard_title
import app.liora.feature.settings.resources.gym_dumbbell_from
import app.liora.feature.settings.resources.gym_dumbbell_step
import app.liora.feature.settings.resources.gym_dumbbell_to
import app.liora.feature.settings.resources.gym_dumbbells_hint
import app.liora.feature.settings.resources.gym_edit_title
import app.liora.feature.settings.resources.gym_ez_bar
import app.liora.feature.settings.resources.gym_keep_editing
import app.liora.feature.settings.resources.gym_name
import app.liora.feature.settings.resources.gym_new_title
import app.liora.feature.settings.resources.gym_plate_pairs
import app.liora.feature.settings.resources.gym_plate_weight
import app.liora.feature.settings.resources.gym_save
import app.liora.feature.settings.resources.gym_section_bars
import app.liora.feature.settings.resources.gym_section_dumbbells
import app.liora.feature.settings.resources.gym_section_machines
import app.liora.feature.settings.resources.gym_section_plates
import app.liora.feature.settings.resources.gym_stack_step
import app.liora.feature.settings.resources.gym_unit
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GymEditorScreen(
    viewModel: GymEditorViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Close once the save or delete has landed; leaving earlier would cancel it.
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(uiState.done) { if (uiState.done) currentOnClose() }

    var dialog by rememberSaveable { mutableStateOf<EditorDialog?>(null) }
    val close = { if (uiState.hasChanges) dialog = EditorDialog.Discard else onClose() }
    // Back with unsaved edits asks first, like the close button.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = uiState.hasChanges && !uiState.done,
        onBackCompleted = { dialog = EditorDialog.Discard },
    )

    val draft = uiState.draft
    if (draft != null) {
        GymEditorContent(
            state = uiState,
            draft = draft,
            actions =
                GymEditorActions(
                    onClose = close,
                    onSave = viewModel::save,
                    onEdit = viewModel::edit,
                    onUnit = viewModel::setUnit,
                    onAddPlate = viewModel::addPlate,
                    onAddDumbbells = viewModel::addDumbbells,
                    onDelete = { dialog = EditorDialog.Delete },
                ),
            modifier = modifier,
        )
    }

    when (dialog) {
        EditorDialog.Discard -> {
            ConfirmDialog(
                title = stringResource(Res.string.gym_discard_title),
                body = null,
                confirm = Res.string.gym_discard,
                dismiss = Res.string.gym_keep_editing,
                onConfirm = {
                    dialog = null
                    onClose()
                },
                onDismiss = { dialog = null },
            )
        }

        EditorDialog.Delete -> {
            ConfirmDialog(
                title = stringResource(Res.string.gym_delete_title, draft?.name.orEmpty()),
                body = stringResource(Res.string.gym_delete_body),
                confirm = Res.string.gym_delete_confirm,
                dismiss = Res.string.dialog_cancel,
                onConfirm = {
                    dialog = null
                    viewModel.delete()
                },
                onDismiss = { dialog = null },
            )
        }

        null -> {
            Unit
        }
    }
}

private enum class EditorDialog { Discard, Delete }

internal class GymEditorActions(
    val onClose: () -> Unit,
    val onSave: () -> Unit,
    val onEdit: (GymDraft.() -> GymDraft) -> Unit,
    val onUnit: (WeightUnit) -> Unit,
    val onAddPlate: () -> Unit,
    val onAddDumbbells: () -> Unit,
    val onDelete: () -> Unit,
)

@Composable
private fun GymEditorContent(
    state: GymEditorUiState,
    draft: GymDraft,
    actions: GymEditorActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(if (state.isNew) Res.string.gym_new_title else Res.string.gym_edit_title),
                navigationIcon = {
                    LioraIconButton(
                        LioraIcons.Close,
                        stringResource(Res.string.cd_close),
                        actions.onClose,
                    )
                },
                actions = {
                    TextButton(onClick = actions.onSave, enabled = state.canSave) {
                        Text(stringResource(Res.string.gym_save))
                    }
                },
            )
        },
    ) { padding ->
        val unit = draft.unit.symbol
        val edit = actions.onEdit
        Column(
            modifier =
                Modifier
                    .readableWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = padding.calculateTopPadding(), bottom = 32.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { name -> edit { copy(name = name) } },
                    label = { Text(stringResource(Res.string.gym_name)) },
                    isError = draft.name.isBlank() && state.hasChanges,
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
                UnitChoice(draft.unit, actions.onUnit)
            }

            SectionHeader(stringResource(Res.string.gym_section_bars))
            FieldRow {
                NumberField(
                    draft.barbell,
                    { value -> edit { copy(barbell = value) } },
                    stringResource(Res.string.gym_barbell),
                    Modifier.weight(1f),
                    suffix = unit,
                    required = true,
                )
                NumberField(
                    draft.ezBar,
                    { value -> edit { copy(ezBar = value) } },
                    stringResource(Res.string.gym_ez_bar),
                    Modifier.weight(1f),
                    suffix = unit,
                    required = true,
                )
            }

            SectionHeader(stringResource(Res.string.gym_section_plates))
            draft.plates.forEach { row ->
                key(row.key) { PlateRowFields(row, unit, edit) }
            }
            AddButton(Res.string.gym_add_plate, actions.onAddPlate)

            SectionHeader(stringResource(Res.string.gym_section_dumbbells))
            Text(
                text = stringResource(Res.string.gym_dumbbells_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            draft.dumbbells.forEach { row ->
                key(row.key) { DumbbellRowFields(row, unit, edit) }
            }
            AddButton(Res.string.gym_add_dumbbells, actions.onAddDumbbells)

            SectionHeader(stringResource(Res.string.gym_section_machines))
            FieldRow {
                NumberField(
                    draft.stackStep,
                    { value -> edit { copy(stackStep = value) } },
                    stringResource(Res.string.gym_stack_step),
                    Modifier.weight(1f),
                    suffix = unit,
                    required = true,
                )
                Spacer(Modifier.weight(1f))
            }

            if (!state.isNew) {
                TextButton(
                    onClick = actions.onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(start = 8.dp, top = 24.dp),
                ) {
                    Text(stringResource(Res.string.gym_delete))
                }
            }
        }
    }
}

@Composable
private fun UnitChoice(
    unit: WeightUnit,
    onUnit: (WeightUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.gym_unit),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        SingleChoiceSegmentedButtonRow {
            WeightUnit.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == unit,
                    onClick = { onUnit(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, WeightUnit.entries.size),
                ) { Text(option.symbol) }
            }
        }
    }
}

@Composable
private fun PlateRowFields(
    row: PlateRow,
    unit: String,
    edit: (GymDraft.() -> GymDraft) -> Unit,
) {
    val change = { updated: PlateRow -> edit { copy(plates = plates.map { if (it.key == row.key) updated else it }) } }
    FieldRow(removable = true) {
        NumberField(
            row.weight,
            { change(row.copy(weight = it)) },
            stringResource(Res.string.gym_plate_weight),
            Modifier.weight(1f),
            suffix = unit,
            required = !row.isEmpty,
        )
        NumberField(
            row.pairs?.toDouble(),
            { change(row.copy(pairs = it?.toInt())) },
            stringResource(Res.string.gym_plate_pairs),
            Modifier.weight(1f),
            whole = true,
            required = !row.isEmpty,
        )
        RemoveButton(Res.string.cd_remove_plate) { edit { copy(plates = plates.filterNot { it.key == row.key }) } }
    }
}

@Composable
private fun DumbbellRowFields(
    row: DumbbellRow,
    unit: String,
    edit: (GymDraft.() -> GymDraft) -> Unit,
) {
    val change = { updated: DumbbellRow ->
        edit {
            copy(
                dumbbells =
                    dumbbells.map {
                        if (it.key ==
                            row.key
                        ) {
                            updated
                        } else {
                            it
                        }
                    },
            )
        }
    }
    FieldRow(removable = true) {
        NumberField(
            row.from,
            { change(row.copy(from = it)) },
            stringResource(Res.string.gym_dumbbell_from),
            Modifier.weight(1f),
            required = !row.isEmpty,
        )
        NumberField(
            row.to,
            { change(row.copy(to = it)) },
            stringResource(Res.string.gym_dumbbell_to),
            Modifier.weight(1f),
            required = !row.isEmpty,
        )
        NumberField(
            row.step,
            { change(row.copy(step = it)) },
            stringResource(Res.string.gym_dumbbell_step),
            Modifier.weight(1f),
            suffix = unit,
            required = !row.isEmpty,
        )
        RemoveButton(
            Res.string.cd_remove_dumbbells,
        ) { edit { copy(dumbbells = dumbbells.filterNot { it.key == row.key }) } }
    }
}

/** Fields side by side; a row ending in a remove button leaves less margin, since the button has its own. */
@Composable
private fun FieldRow(
    removable: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = if (removable) 8.dp else 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RemoveButton(
    description: StringResource,
    onClick: () -> Unit,
) {
    LioraIconButton(LioraIcons.Close, stringResource(description), onClick)
}

@Composable
private fun AddButton(
    label: StringResource,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.padding(start = 8.dp)) {
        Icon(painterResource(LioraIcons.Add), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(label))
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String?,
    confirm: StringResource,
    dismiss: StringResource,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = body?.let { { Text(it) } },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(dismiss)) } },
    )
}
