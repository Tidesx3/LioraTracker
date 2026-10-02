package app.liora.feature.logger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.util.NumberFormatter
import app.liora.core.domain.SetRef
import app.liora.core.domain.restAfter
import app.liora.core.model.ExerciseSettings
import app.liora.core.model.LoggedSet
import app.liora.core.model.WorkoutExercise
import app.liora.core.ui.ExerciseThumbnail
import app.liora.core.ui.RestTimePicker
import app.liora.core.ui.restLabel
import app.liora.core.ui.setNumbers
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.cd_more
import app.liora.feature.logger.resources.logger_add_set
import app.liora.feature.logger.resources.menu_add_note
import app.liora.feature.logger.resources.menu_remove
import app.liora.feature.logger.resources.menu_reorder
import app.liora.feature.logger.resources.menu_replace
import app.liora.feature.logger.resources.menu_rest
import app.liora.feature.logger.resources.menu_superset_next
import app.liora.feature.logger.resources.menu_superset_remove
import app.liora.feature.logger.resources.note_hint
import app.liora.feature.logger.resources.superset
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** What can be done to an exercise in the workout, each taking the workout exercise's id. */
internal class ExerciseActions(
    val onReplace: (String) -> Unit,
    val onReorder: () -> Unit,
    val onSupersetChange: (id: String, link: Boolean) -> Unit,
    val onRemove: (String) -> Unit,
    val onRestChange: (id: String, seconds: Int?) -> Unit,
    val onNotesChange: (id: String, notes: String?) -> Unit,
    val onAddSet: (String) -> Unit,
)

/** One exercise of the workout: name and rest, its note, and a row per set. */
@Composable
internal fun ExerciseCard(
    index: Int,
    state: LoggerUiState.Active,
    actions: ExerciseActions,
    setActions: SetRowActions,
    onTextFocus: () -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    numbers: NumberFormatter,
    modifier: Modifier = Modifier,
) {
    val workoutExercise = state.workout.exercises[index]
    val exercise = state.exercises[workoutExercise.exerciseId]
    val trackingType = state.trackingTypeOf(workoutExercise.exerciseId)
    var choosingRest by rememberSaveable { mutableStateOf(false) }
    var noteOpen by rememberSaveable { mutableStateOf(false) }
    val inSuperset = workoutExercise.supersetGroup != null

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (inSuperset) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        // The photo and the name open the exercise with its instructions and history.
        val openExercise: () -> Unit = { exercise?.let { onOpenExercise(it.id) } }
        Row(Modifier.padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            exercise?.let {
                ExerciseThumbnail(
                    exercise = it,
                    size = ThumbnailSize,
                    modifier = Modifier.padding(end = 12.dp).clickable(onClick = openExercise),
                )
            }
            Column(Modifier.weight(1f)) {
                if (inSuperset) SupersetLabel()
                Text(
                    text = exercise?.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = exercise != null, onClick = openExercise),
                )
                // Rest only matters while training; a finished workout has had its rests.
                if (!state.isFinished) {
                    Text(
                        text =
                            restLabel(
                                effectiveRestSeconds(workoutExercise, exercise?.settings ?: ExerciseSettings(), state),
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { choosingRest = true }.padding(vertical = 4.dp),
                    )
                }
            }
            ExerciseMenu(
                workoutExercise = workoutExercise,
                isLast = index == state.workout.exercises.lastIndex,
                hasNote = workoutExercise.notes != null || noteOpen,
                onChooseRest = { choosingRest = true }.takeUnless { state.isFinished },
                onAddNote = { noteOpen = true },
                actions = actions,
            )
        }
        if (workoutExercise.notes != null || noteOpen) {
            NoteField(
                workoutExercise = workoutExercise,
                onNotesChange = actions.onNotesChange,
                onFocus = onTextFocus,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        val columns = SetColumns(trackingType, state.fieldsOf(trackingType))
        SetHeader(columns, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        val numbersOfSets = setNumbers(workoutExercise.sets.map { it.type })
        workoutExercise.sets.forEachIndexed { setIndex, set ->
            key(set.id) {
                val ref = SetRef(index, setIndex)
                SetRow(
                    state =
                        SetRowState(
                            set = set,
                            number = numbersOfSets[setIndex],
                            columns = columns,
                            hints =
                                SetHints(
                                    previous = state.previousFor(ref),
                                    placeholder = state.placeholderFor(ref),
                                    isRecord = set.id in state.newRecords,
                                ),
                            isCurrent = state.current == ref,
                            edit = state.edit?.takeIf { it.cell.setId == set.id },
                            invalid = state.invalid?.takeIf { it.setId == set.id }?.field,
                        ),
                    actions = setActions,
                    numbers = numbers,
                )
            }
        }
        TextButton(onClick = {
            actions.onAddSet(workoutExercise.id)
        }, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
            Icon(painterResource(LioraIcons.Add), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.logger_add_set))
        }
    }

    if (choosingRest) {
        RestTimePicker(
            current = workoutExercise.restSeconds,
            onChoose = {
                choosingRest = false
                actions.onRestChange(workoutExercise.id, it)
            },
            onDismiss = { choosingRest = false },
        )
    }
}

/** The rest a working set of this exercise gets, as the label shows it; 0 when off. */
private fun effectiveRestSeconds(
    workoutExercise: WorkoutExercise,
    settings: ExerciseSettings,
    state: LoggerUiState.Active,
): Int = restAfter(LoggedSet(id = ""), workoutExercise, settings, state.restDefaults)?.inWholeSeconds?.toInt() ?: 0

@Composable
private fun NoteField(
    workoutExercise: WorkoutExercise,
    onNotesChange: (String, String?) -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Typed text stays local: waiting for each keystroke's round trip through the database would make
    // the cursor jump.
    var text by rememberSaveable(workoutExercise.id) { mutableStateOf(workoutExercise.notes.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onNotesChange(workoutExercise.id, it)
        },
        placeholder = { Text(stringResource(Res.string.note_hint)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
    )
}

@Composable
private fun ExerciseMenu(
    workoutExercise: WorkoutExercise,
    isLast: Boolean,
    hasNote: Boolean,
    /** Null where rest doesn't apply. */
    onChooseRest: (() -> Unit)?,
    onAddNote: () -> Unit,
    actions: ExerciseActions,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val id = workoutExercise.id
    val items: List<Pair<StringResource, () -> Unit>> =
        buildList {
            add(Res.string.menu_replace to { actions.onReplace(id) })
            add(Res.string.menu_reorder to actions.onReorder)
            if (!isLast) add(Res.string.menu_superset_next to { actions.onSupersetChange(id, true) })
            if (workoutExercise.supersetGroup !=
                null
            ) {
                add(Res.string.menu_superset_remove to { actions.onSupersetChange(id, false) })
            }
            onChooseRest?.let { add(Res.string.menu_rest to it) }
            if (!hasNote) add(Res.string.menu_add_note to onAddNote)
            add(Res.string.menu_remove to { actions.onRemove(id) })
        }
    LioraIconButton(LioraIcons.More, stringResource(Res.string.cd_more), { expanded = true })
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

@Composable
internal fun SupersetLabel(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(LioraIcons.Superset),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(Res.string.superset),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private val ThumbnailSize = 48.dp
