package app.liora.feature.train.routine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.model.Exercise
import app.liora.core.model.Mass
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.ui.LocalUnits
import app.liora.core.ui.RestTimePicker
import app.liora.core.ui.SetTypeMenu
import app.liora.core.ui.currentLanguage
import app.liora.core.ui.distanceFor
import app.liora.core.ui.restLabel
import app.liora.core.ui.setNumbers
import app.liora.feature.train.resources.Res
import app.liora.feature.train.resources.cd_close
import app.liora.feature.train.resources.cd_more
import app.liora.feature.train.resources.cd_remove_set
import app.liora.feature.train.resources.cd_reorder
import app.liora.feature.train.resources.editor_add_exercises
import app.liora.feature.train.resources.editor_add_note
import app.liora.feature.train.resources.editor_add_set
import app.liora.feature.train.resources.editor_col_reps
import app.liora.feature.train.resources.editor_col_set
import app.liora.feature.train.resources.editor_col_time
import app.liora.feature.train.resources.editor_discard
import app.liora.feature.train.resources.editor_discard_body
import app.liora.feature.train.resources.editor_discard_title
import app.liora.feature.train.resources.editor_edit_title
import app.liora.feature.train.resources.editor_empty
import app.liora.feature.train.resources.editor_keep_editing
import app.liora.feature.train.resources.editor_name
import app.liora.feature.train.resources.editor_new_title
import app.liora.feature.train.resources.editor_note_hint
import app.liora.feature.train.resources.editor_notes
import app.liora.feature.train.resources.editor_remove_exercise
import app.liora.feature.train.resources.editor_reps_max_hint
import app.liora.feature.train.resources.editor_rest
import app.liora.feature.train.resources.editor_save
import app.liora.feature.train.resources.editor_superset_next
import app.liora.feature.train.resources.editor_superset_remove
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun RoutineEditorScreen(
    viewModel: RoutineEditorViewModel,
    onClose: () -> Unit,
    onSaveComplete: (routineId: String) -> Unit,
    onAddExercises: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = currentLanguage()
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Close once the save has landed; leaving earlier would cancel it.
    val currentOnSaveComplete by rememberUpdatedState(onSaveComplete)
    LaunchedEffect(uiState.savedId) { uiState.savedId?.let(currentOnSaveComplete) }

    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val close = { if (uiState.hasChanges) confirmDiscard = true else onClose() }
    // Back with unsaved edits asks first, like the close button.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = uiState.hasChanges && uiState.savedId == null,
        onBackCompleted = { confirmDiscard = true },
    )

    if (!uiState.loading) {
        RoutineEditorContent(
            state = uiState,
            actions =
                EditorActions(
                    onClose = close,
                    onSave = viewModel::save,
                    onNameChange = viewModel::onNameChange,
                    onNotesChange = viewModel::onNotesChange,
                    onAddExercises = onAddExercises,
                    onMove = viewModel::move,
                    exercise =
                        ExerciseActions(
                            onRemove = viewModel::removeExercise,
                            onLinkWithNext = viewModel::linkWithNext,
                            onUnlink = viewModel::unlink,
                            onChange = viewModel::updateExercise,
                            onAddSet = viewModel::addSet,
                            onRemoveSet = viewModel::removeSet,
                            onSetChange = viewModel::updateSet,
                        ),
                ),
            modifier = modifier,
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(Res.string.editor_discard_title)) },
            text = { Text(stringResource(Res.string.editor_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onClose()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(Res.string.editor_discard))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDiscard = false },
                ) { Text(stringResource(Res.string.editor_keep_editing)) }
            },
        )
    }
}

internal class EditorActions(
    val onClose: () -> Unit,
    val onSave: () -> Unit,
    val onNameChange: (String) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onAddExercises: () -> Unit,
    val onMove: (fromId: String, toId: String) -> Unit,
    val exercise: ExerciseActions,
)

/** Edits to one exercise of the routine and its sets, each addressed by id. */
internal class ExerciseActions(
    val onRemove: (exerciseId: String) -> Unit,
    val onLinkWithNext: (exerciseId: String) -> Unit,
    val onUnlink: (exerciseId: String) -> Unit,
    val onChange: (exerciseId: String, change: RoutineExercise.() -> RoutineExercise) -> Unit,
    val onAddSet: (exerciseId: String) -> Unit,
    val onRemoveSet: (exerciseId: String, setId: String) -> Unit,
    val onSetChange: (exerciseId: String, setId: String, change: RoutineSet.() -> RoutineSet) -> Unit,
)

@Composable
private fun RoutineEditorContent(
    state: RoutineEditorUiState,
    actions: EditorActions,
    modifier: Modifier = Modifier,
) {
    val routine = state.routine
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            actions.onMove(from.key as String, to.key as String)
        }
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(if (state.isNew) Res.string.editor_new_title else Res.string.editor_edit_title),
                navigationIcon = {
                    LioraIconButton(
                        LioraIcons.Close,
                        stringResource(Res.string.cd_close),
                        actions.onClose,
                    )
                },
                actions = {
                    TextButton(onClick = actions.onSave, enabled = state.canSave) {
                        Text(stringResource(Res.string.editor_save))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.readableWidth(),
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom =
                        padding.calculateBottomPadding() + 32.dp,
                ),
        ) {
            item(key = "details") {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = routine.name,
                        onValueChange = actions.onNameChange,
                        label = { Text(stringResource(Res.string.editor_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = routine.notes.orEmpty(),
                        onValueChange = actions.onNotesChange,
                        label = { Text(stringResource(Res.string.editor_notes)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(routine.exercises, key = { it.id }) { planned ->
                ReorderableItem(reorderState, key = planned.id) { isDragging ->
                    val index = routine.exercises.indexOf(planned)
                    ExerciseEditorCard(
                        planned = planned,
                        exercise = state.exercises[planned.exerciseId],
                        isLast = index == routine.exercises.lastIndex,
                        dragging = isDragging,
                        actions = actions.exercise,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            item(key = "add") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (routine.exercises.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.editor_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = actions.onAddExercises, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            painterResource(LioraIcons.Add),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.editor_add_exercises))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.ExerciseEditorCard(
    planned: RoutineExercise,
    exercise: Exercise?,
    isLast: Boolean,
    dragging: Boolean,
    actions: ExerciseActions,
    modifier: Modifier = Modifier,
) {
    val trackingType = exercise?.trackingType ?: TrackingType.WeightReps
    var choosingRest by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    with(MaterialTheme.colorScheme) { if (dragging) surfaceContainerHighest else surfaceContainer },
            ),
        border = if (planned.supersetGroup != null) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 0.dp),
    ) {
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                    Icon(
                        painter = painterResource(LioraIcons.DragHandle),
                        contentDescription = stringResource(Res.string.cd_reorder),
                    )
                }
                Column(Modifier.weight(1f)) {
                    if (planned.supersetGroup != null) SupersetLabel()
                    Text(exercise?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = restLabel(planned.restSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { choosingRest = true }.padding(vertical = 4.dp),
                    )
                }
                ExerciseMenu(
                    planned = planned,
                    isLast = isLast,
                    onChooseRest = { choosingRest = true },
                    actions = actions,
                )
            }
            planned.notes?.let { notes ->
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes -> actions.onChange(planned.id) { copy(notes = notes) } },
                    placeholder = { Text(stringResource(Res.string.editor_note_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            SetHeader(trackingType, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            val numbers = setNumbers(planned.sets.map { it.type })
            planned.sets.forEachIndexed { index, set ->
                SetEditorRow(
                    set = set,
                    number = numbers[index],
                    trackingType = trackingType,
                    onChange = { change -> actions.onSetChange(planned.id, set.id, change) },
                    onRemove = { actions.onRemoveSet(planned.id, set.id) },
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            TextButton(onClick = { actions.onAddSet(planned.id) }, modifier = Modifier.padding(start = 4.dp)) {
                Icon(painterResource(LioraIcons.Add), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.editor_add_set))
            }
        }
    }
    if (choosingRest) {
        RestTimePicker(
            current = planned.restSeconds,
            onChoose = {
                choosingRest = false
                actions.onChange(planned.id) { copy(restSeconds = it) }
            },
            onDismiss = { choosingRest = false },
        )
    }
}

@Composable
private fun ExerciseMenu(
    planned: RoutineExercise,
    isLast: Boolean,
    onChooseRest: () -> Unit,
    actions: ExerciseActions,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val items =
        buildList {
            if (!isLast) add(Res.string.editor_superset_next to { actions.onLinkWithNext(planned.id) })
            if (planned.supersetGroup !=
                null
            ) {
                add(Res.string.editor_superset_remove to { actions.onUnlink(planned.id) })
            }
            add(Res.string.editor_rest to onChooseRest)
            if (planned.notes ==
                null
            ) {
                add(Res.string.editor_add_note to { actions.onChange(planned.id) { copy(notes = "") } })
            }
            add(Res.string.editor_remove_exercise to { actions.onRemove(planned.id) })
        }
    LioraIconButton(LioraIcons.More, stringResource(Res.string.cd_more), { expanded = true }, modifier)
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        items.forEach { (label, action) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                onClick = {
                    expanded = false
                    action()
                },
            )
        }
    }
}

/** Column titles that line up with [SetEditorRow]'s fields. */
@Composable
private fun SetHeader(
    trackingType: TrackingType,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(FIELD_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val units = LocalUnits.current
        HeaderText(stringResource(Res.string.editor_col_set), BADGE_WIDTH)
        if (trackingType.usesWeight) HeaderText(units.weight.symbol, WEIGHT_WIDTH)
        if (trackingType.usesReps) {
            HeaderText(
                stringResource(Res.string.editor_col_reps),
                REPS_WIDTH * 2 + FIELD_GAP * 2 + DASH_WIDTH,
            )
        }
        if (trackingType.usesDistance) {
            HeaderText(units.distanceFor(trackingType).symbol, WEIGHT_WIDTH)
        }
        if (trackingType.usesDuration) HeaderText(stringResource(Res.string.editor_col_time), WEIGHT_WIDTH)
    }
}

@Composable
private fun HeaderText(
    text: String,
    width: Dp,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun SetEditorRow(
    set: RoutineSet,
    number: Int,
    trackingType: TrackingType,
    onChange: (RoutineSet.() -> RoutineSet) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Typed in the chosen units, kept in kilograms and metres.
    val units = LocalUnits.current
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(FIELD_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetTypeMenu(set.type, number, onPick = { type -> onChange { copy(type = type) } })
        if (trackingType.usesWeight) {
            DecimalTargetField(
                value = set.weight?.inUnit(units.weight),
                onValueChange = { typed -> onChange { copy(weight = typed?.let { Mass.of(it, units.weight) }) } },
                placeholder = "–",
                modifier = Modifier.width(WEIGHT_WIDTH),
            )
        }
        if (trackingType.usesReps) {
            IntTargetField(
                value = set.reps?.min,
                onValueChange = { min -> onChange { copy(reps = reps.withMin(min)) } },
                placeholder = "–",
                modifier = Modifier.width(REPS_WIDTH),
            )
            Text("–", modifier = Modifier.width(DASH_WIDTH), textAlign = TextAlign.Center)
            IntTargetField(
                value = set.reps?.takeIf { it.isRange }?.max,
                onValueChange = { max -> onChange { copy(reps = reps.withMax(max)) } },
                placeholder = stringResource(Res.string.editor_reps_max_hint),
                modifier = Modifier.width(REPS_WIDTH),
            )
        }
        if (trackingType.usesDistance) {
            val unit = units.distanceFor(trackingType)
            DecimalTargetField(
                value = set.distanceMeters?.let(unit::fromMeters),
                onValueChange = { typed -> onChange { copy(distanceMeters = typed?.let(unit::toMeters)) } },
                placeholder = "–",
                modifier = Modifier.width(WEIGHT_WIDTH),
            )
        }
        if (trackingType.usesDuration) {
            ClockTargetField(
                seconds = set.duration?.inWholeSeconds?.toInt(),
                onSecondsChange = { seconds -> onChange { copy(duration = seconds?.seconds) } },
                placeholder = "0:00",
                modifier = Modifier.width(WEIGHT_WIDTH),
            )
        }
        Spacer(Modifier.weight(1f))
        LioraIconButton(LioraIcons.Close, stringResource(Res.string.cd_remove_set), onRemove)
    }
}

private val BADGE_WIDTH = 32.dp
private val WEIGHT_WIDTH = 72.dp
private val REPS_WIDTH = 52.dp
private val DASH_WIDTH = 8.dp
private val FIELD_GAP = 6.dp
