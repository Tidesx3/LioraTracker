package app.liora.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.ui.resources.Res
import app.liora.core.ui.resources.rest_default
import app.liora.core.ui.resources.rest_off
import app.liora.core.ui.resources.rest_picker_cancel
import app.liora.core.ui.resources.rest_picker_default
import app.liora.core.ui.resources.rest_picker_off
import app.liora.core.ui.resources.rest_picker_title
import app.liora.core.ui.resources.rest_value
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

/** "Rest 1:30", "Rest: exercise default" (null) or "No rest timer" (0). */
@Composable
fun restLabel(seconds: Int?): String =
    when (seconds) {
        null -> stringResource(Res.string.rest_default)
        0 -> stringResource(Res.string.rest_off)
        else -> stringResource(Res.string.rest_value, seconds.seconds.formatAsClock())
    }

/** Picks a rest time: the exercise's own default (null, when offered), off (0), or a common duration. */
@Composable
fun RestTimePicker(
    current: Int?,
    onChoose: (Int?) -> Unit,
    onDismiss: () -> Unit,
    // The app-wide default has no exercise default to fall back on.
    offerExerciseDefault: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.rest_picker_title)) },
        text = {
            LazyColumn(Modifier.selectableGroup()) {
                items(if (offerExerciseDefault) REST_CHOICES else REST_CHOICES.filterNotNull()) { seconds ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onChoose(seconds) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = seconds == current, onClick = { onChoose(seconds) })
                        Text(
                            text =
                                when (seconds) {
                                    null -> stringResource(Res.string.rest_picker_default)
                                    0 -> stringResource(Res.string.rest_picker_off)
                                    else -> seconds.seconds.formatAsClock()
                                },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.rest_picker_cancel)) } },
    )
}

/** Null keeps the exercise's own default; 0 turns the timer off. */
private val REST_CHOICES: List<Int?> = listOf(null, 0, 30, 45, 60, 90, 120, 150, 180, 240, 300)
