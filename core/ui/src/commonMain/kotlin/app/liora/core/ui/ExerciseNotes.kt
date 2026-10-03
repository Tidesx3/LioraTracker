package app.liora.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.ui.resources.Res
import app.liora.core.ui.resources.note_exercise
import app.liora.core.ui.resources.note_exercise_hint
import app.liora.core.ui.resources.note_workout
import app.liora.core.ui.resources.note_workout_hint
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The two notes an exercise can carry. */
enum class NoteKind {
    /** The exercise's own: seat height, grip, the plan. Pinned under it wherever it appears. */
    Exercise,

    /** One workout's: how it went that day. It stays with that workout. */
    Workout,
}

/**
 * A note under an exercise: a soft strip with the note's icon, a pin for the exercise's own (tinted
 * with the accent) and a calendar day for a workout's. Read only; see [ExerciseNoteField] to edit one.
 */
@Composable
fun ExerciseNote(
    text: String,
    kind: NoteKind,
    modifier: Modifier = Modifier,
) {
    NoteStrip(kind, modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A note under an exercise that edits in place: tap it and type, like writing on the card. Typed text
 * is passed on with every change but kept here meanwhile, so the cursor never waits for the database;
 * a change made elsewhere shows once it isn't being typed in. An empty note shows what it's for.
 *
 * @param focusOnStart puts the cursor in it as it first appears, when it was just added from a menu.
 * @param onFocusChange tells when typing starts and ends, e.g. to put a number pad away.
 * @param onLeaveEmpty tells that typing ended with no text, so a note just added can go again.
 */
@Composable
fun ExerciseNoteField(
    note: String?,
    kind: NoteKind,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusOnStart: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {},
    onLeaveEmpty: () -> Unit = {},
) {
    var text by rememberSaveable { mutableStateOf(note.orEmpty()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(note) { if (!focused) text = note.orEmpty() }
    val focus = remember { FocusRequester() }
    // Once: scrolled away and back, the note doesn't take the cursor again.
    var wantsFocus by rememberSaveable { mutableStateOf(focusOnStart) }
    LaunchedEffect(wantsFocus) {
        if (wantsFocus) {
            focus.requestFocus()
            wantsFocus = false
        }
    }
    NoteStrip(
        kind = kind,
        // The whole strip is the field: a tap beside the text still puts the cursor in.
        modifier =
            modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                focus.requestFocus()
            },
    ) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onNoteChange(it)
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .onFocusChanged {
                        if (it.isFocused != focused) {
                            focused = it.isFocused
                            onFocusChange(it.isFocused)
                            if (!it.isFocused && text.isBlank()) onLeaveEmpty()
                        }
                    },
            decorationBox = { field ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(kind.hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    field()
                }
            },
        )
    }
}

@Composable
private fun NoteStrip(
    kind: NoteKind,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val exercise = kind == NoteKind.Exercise
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(if (exercise) colors.primary.copy(alpha = 0.09f) else colors.onSurface.copy(alpha = 0.05f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(if (exercise) LioraIcons.Pin else LioraIcons.Today),
            contentDescription = stringResource(if (exercise) Res.string.note_exercise else Res.string.note_workout),
            tint = if (exercise) colors.primary else colors.onSurfaceVariant,
            // Level with the first line of text.
            modifier = Modifier.padding(top = 1.dp).size(18.dp),
        )
        content()
    }
}

private val NoteKind.hint
    get() =
        when (this) {
            NoteKind.Exercise -> Res.string.note_exercise_hint
            NoteKind.Workout -> Res.string.note_workout_hint
        }
