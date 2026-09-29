package app.liora.feature.exercises.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseDraft
import app.liora.core.model.Muscle
import app.liora.core.model.TrackingType
import app.liora.core.ui.examples
import app.liora.core.ui.label
import app.liora.feature.exercises.resources.Res
import app.liora.feature.exercises.resources.cd_close
import app.liora.feature.exercises.resources.editor_edit_title
import app.liora.feature.exercises.resources.editor_equipment
import app.liora.feature.exercises.resources.editor_name
import app.liora.feature.exercises.resources.editor_new_title
import app.liora.feature.exercises.resources.editor_notes
import app.liora.feature.exercises.resources.editor_notes_hint
import app.liora.feature.exercises.resources.editor_primary
import app.liora.feature.exercises.resources.editor_save
import app.liora.feature.exercises.resources.editor_secondary
import app.liora.feature.exercises.resources.editor_tracking
import app.liora.feature.exercises.resources.editor_tracking_locked
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExerciseEditorScreen(
    viewModel: ExerciseEditorViewModel,
    onClose: () -> Unit,
    onSaveComplete: (exerciseId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Close once the save has landed; leaving earlier would cancel it.
    val currentOnSaveComplete by rememberUpdatedState(onSaveComplete)
    LaunchedEffect(uiState.savedId) { uiState.savedId?.let(currentOnSaveComplete) }

    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title =
                    stringResource(if (uiState.isNew) Res.string.editor_new_title else Res.string.editor_edit_title),
                navigationIcon = { LioraIconButton(LioraIcons.Close, stringResource(Res.string.cd_close), onClose) },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = uiState.draft.isValid && !uiState.saving) {
                        Text(stringResource(Res.string.editor_save))
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.loading) return@Scaffold
        EditorForm(
            state = uiState,
            actions =
                EditorActions(
                    onNameChange = viewModel::onNameChange,
                    onTrackingTypeChange = viewModel::onTrackingTypeChange,
                    onEquipmentChange = viewModel::onEquipmentChange,
                    onTogglePrimary = viewModel::togglePrimary,
                    onToggleSecondary = viewModel::toggleSecondary,
                    onNotesChange = viewModel::onNotesChange,
                ),
            contentPadding =
                PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorForm(
    state: ExerciseEditorUiState,
    actions: EditorActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    LazyColumn(modifier = modifier.readableWidth(), contentPadding = contentPadding) {
        item {
            OutlinedTextField(
                value = draft.name,
                onValueChange = actions.onNameChange,
                label = { Text(stringResource(Res.string.editor_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            SectionHeader(stringResource(Res.string.editor_tracking))
            TrackingTypePicker(draft, locked = state.trackingLocked, onSelect = actions.onTrackingTypeChange)
        }
        item {
            SectionHeader(stringResource(Res.string.editor_equipment))
            ChipGroup(
                options = Equipment.entries,
                isSelected = { it == draft.equipment },
                label = { stringResource(it.label) },
                onClick = actions.onEquipmentChange,
            )
        }
        item {
            SectionHeader(stringResource(Res.string.editor_primary))
            ChipGroup(
                options = Muscle.entries,
                isSelected = { it in draft.primaryMuscles },
                label = { stringResource(it.label) },
                onClick = actions.onTogglePrimary,
            )
        }
        item {
            SectionHeader(stringResource(Res.string.editor_secondary))
            ChipGroup(
                options = Muscle.entries.filterNot { it in draft.primaryMuscles },
                isSelected = { it in draft.secondaryMuscles },
                label = { stringResource(it.label) },
                onClick = actions.onToggleSecondary,
            )
        }
        item {
            SectionHeader(stringResource(Res.string.editor_notes))
            OutlinedTextField(
                value = draft.notes,
                onValueChange = actions.onNotesChange,
                placeholder = { Text(stringResource(Res.string.editor_notes_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(horizontal = 16.dp),
            )
        }
    }
}

internal class EditorActions(
    val onNameChange: (String) -> Unit,
    val onTrackingTypeChange: (TrackingType) -> Unit,
    val onEquipmentChange: (Equipment) -> Unit,
    val onTogglePrimary: (Muscle) -> Unit,
    val onToggleSecondary: (Muscle) -> Unit,
    val onNotesChange: (String) -> Unit,
)

@Composable
private fun TrackingTypePicker(
    draft: ExerciseDraft,
    locked: Boolean,
    onSelect: (TrackingType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup()) {
        if (locked) {
            Text(
                text = stringResource(Res.string.editor_tracking_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        TrackingType.entries.forEach { type ->
            val selected = type == draft.trackingType
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, enabled = !locked, role = Role.RadioButton) { onSelect(type) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null, enabled = !locked || selected)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(stringResource(type.label), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(type.examples),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: @Composable (T) -> String,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = isSelected(option),
                onClick = { onClick(option) },
                label = { Text(label(option)) },
            )
        }
    }
}
