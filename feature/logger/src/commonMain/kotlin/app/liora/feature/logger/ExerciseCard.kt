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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.LioraCard
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.util.NumberFormatter
import app.liora.core.domain.SetRef
import app.liora.core.domain.restAfter
import app.liora.core.model.Exercise
import app.liora.core.model.ExerciseSettings
import app.liora.core.model.LoggedSet
import app.liora.core.model.WorkoutExercise
import app.liora.core.ui.ExerciseNoteField
import app.liora.core.ui.ExerciseThumbnail
import app.liora.core.ui.NoteKind
import app.liora.core.ui.ProgressionNote
import app.liora.core.ui.RestTimePicker
import app.liora.core.ui.restLabel
import app.liora.core.ui.setNumbers
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.cd_more
import app.liora.feature.logger.resources.logger_add_set
import app.liora.feature.logger.resources.menu_add_warmups
import app.liora.feature.logger.resources.menu_exercise_note
import app.liora.feature.logger.resources.menu_remove
import app.liora.feature.logger.resources.menu_reorder
import app.liora.feature.logger.resources.menu_replace
import app.liora.feature.logger.resources.menu_rest
import app.liora.feature.logger.resources.menu_superset_next
import app.liora.feature.logger.resources.menu_superset_remove
import app.liora.feature.logger.resources.menu_workout_note
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
    /**
     * A note on the exercise: its own ([NoteKind.Exercise], by the exercise's id), which shows every time
     * it's done, or this workout's ([NoteKind.Workout], by the workout exercise's id).
     */
    val onNoteChange: (kind: NoteKind, id: String, note: String?) -> Unit,
    /** Adds a set at the end, or with [warmups] the ramp of warm-up sets before the first. */
    val onAddSets: (id: String, warmups: Boolean) -> Unit,
)

/** One exercise of the workout: name and rest, its notes, and a row per set. */
@Composable
internal fun ExerciseCard(
    index: Int,
    state: LoggerUiState.Active,
    actions: ExerciseActions,
    setActions: SetRowActions,
    onTextFocus: () -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    numbers: NumberFormatter,
    place: WorkoutListPlace,
    modifier: Modifier = Modifier,
) {
    val workoutExercise = state.workout.exercises[index]
    val exercise = state.exercises[workoutExercise.exerciseId]
    val trackingType = state.trackingTypeOf(workoutExercise.exerciseId)
    var choosingRest by rememberSaveable { mutableStateOf(false) }
    // A note added from the menu shows before it has any text, and goes again if left empty.
    var adding by rememberSaveable { mutableStateOf<NoteKind?>(null) }
    val inSuperset = workoutExercise.supersetGroup != null
    val exerciseNote = exercise?.settings?.stickyNote

    LioraCard(
        modifier = modifier.fillMaxWidth(),
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
                canAddWarmups = state.warmupsFor(index).isNotEmpty(),
                onChooseRest = { choosingRest = true }.takeUnless { state.isFinished },
                onAddNote = { kind: NoteKind -> adding = kind }.takeIf { exercise != null },
                hasExerciseNote = exerciseNote != null || adding == NoteKind.Exercise,
                hasWorkoutNote = workoutExercise.notes != null || adding == NoteKind.Workout,
                actions = actions,
            )
        }
        state.progression[workoutExercise.exerciseId]?.let { progression ->
            ProgressionNote(
                reason = progression.target.reason,
                bigJump = progression.bigJump,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
            )
        }
        CardNotes(
            exercise = exercise,
            workoutExercise = workoutExercise,
            adding = adding,
            onNoteChange = actions.onNoteChange,
            onTextFocus = onTextFocus,
            onLeaveEmpty = { kind -> if (adding == kind) adding = null },
        )
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
                    onReveal = place::opened.takeIf { place.openingSetId == set.id },
                )
            }
        }
        TextButton(onClick = {
            actions.onAddSets(workoutExercise.id, false)
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

/**
 * The exercise's notes under its name: its own first, as it's there every time, then this workout's.
 * One being [adding] from the menu shows before it has any text.
 */
@Composable
private fun CardNotes(
    exercise: Exercise?,
    workoutExercise: WorkoutExercise,
    adding: NoteKind?,
    onNoteChange: (kind: NoteKind, id: String, note: String?) -> Unit,
    onTextFocus: () -> Unit,
    onLeaveEmpty: (NoteKind) -> Unit,
) {
    val exerciseNote = exercise?.settings?.stickyNote
    if (exercise != null && (exerciseNote != null || adding == NoteKind.Exercise)) {
        NoteField(
            note = exerciseNote,
            kind = NoteKind.Exercise,
            justAdded = adding == NoteKind.Exercise,
            onNoteChange = { onNoteChange(NoteKind.Exercise, exercise.id, it) },
            onTextFocus = onTextFocus,
            onLeaveEmpty = { onLeaveEmpty(NoteKind.Exercise) },
        )
    }
    if (workoutExercise.notes != null || adding == NoteKind.Workout) {
        NoteField(
            note = workoutExercise.notes,
            kind = NoteKind.Workout,
            justAdded = adding == NoteKind.Workout,
            onNoteChange = { onNoteChange(NoteKind.Workout, workoutExercise.id, it) },
            onTextFocus = onTextFocus,
            onLeaveEmpty = { onLeaveEmpty(NoteKind.Workout) },
        )
    }
}

/**
 * One of the exercise's notes, written on the card. Typing in it puts the number pad away; one just
 * added from the menu gets the cursor, and goes again when left without text.
 */
@Composable
private fun NoteField(
    note: String?,
    kind: NoteKind,
    justAdded: Boolean,
    onNoteChange: (String) -> Unit,
    onTextFocus: () -> Unit,
    onLeaveEmpty: () -> Unit,
) {
    ExerciseNoteField(
        note = note,
        kind = kind,
        onNoteChange = onNoteChange,
        focusOnStart = justAdded,
        onFocusChange = { focused -> if (focused) onTextFocus() },
        onLeaveEmpty = onLeaveEmpty,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
    )
}

@Composable
private fun ExerciseMenu(
    workoutExercise: WorkoutExercise,
    isLast: Boolean,
    canAddWarmups: Boolean,
    /** Null where rest doesn't apply. */
    onChooseRest: (() -> Unit)?,
    /** Null while the exercise isn't known yet. */
    onAddNote: ((NoteKind) -> Unit)?,
    hasExerciseNote: Boolean,
    hasWorkoutNote: Boolean,
    actions: ExerciseActions,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val id = workoutExercise.id
    val items: List<Pair<StringResource, () -> Unit>> =
        buildList {
            if (canAddWarmups) add(Res.string.menu_add_warmups to { actions.onAddSets(id, true) })
            add(Res.string.menu_replace to { actions.onReplace(id) })
            add(Res.string.menu_reorder to actions.onReorder)
            if (!isLast) add(Res.string.menu_superset_next to { actions.onSupersetChange(id, true) })
            if (workoutExercise.supersetGroup !=
                null
            ) {
                add(Res.string.menu_superset_remove to { actions.onSupersetChange(id, false) })
            }
            onChooseRest?.let { add(Res.string.menu_rest to it) }
            if (onAddNote != null && !hasExerciseNote) {
                add(Res.string.menu_exercise_note to { onAddNote(NoteKind.Exercise) })
            }
            if (onAddNote != null && !hasWorkoutNote) {
                add(Res.string.menu_workout_note to { onAddNote(NoteKind.Workout) })
            }
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
