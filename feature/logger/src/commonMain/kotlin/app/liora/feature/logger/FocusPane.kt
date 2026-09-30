package app.liora.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.domain.SetRef
import app.liora.core.domain.find
import app.liora.core.domain.setAt
import app.liora.core.model.RepRange
import app.liora.core.ui.ExercisePicture
import app.liora.core.ui.setSummary
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.focus_all_done
import app.liora.feature.logger.resources.focus_all_done_body
import app.liora.feature.logger.resources.focus_log
import app.liora.feature.logger.resources.focus_previous
import app.liora.feature.logger.resources.focus_set
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The right pane on wide screens such as the Fold's inner display: the rest countdown as a big ring,
 * the set that's up next with last session's values, and a number pad that is always there.
 */
@Composable
internal fun FocusPane(
    state: LoggerUiState.Active,
    actions: LoggerActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            rememberRestProgress(state.restTimer)?.let { rest ->
                RestTimerRing(rest, onAdjust = actions.rest.onAdjust, onSkip = actions.rest.onSkip)
            }
            UpNext(state, onLog = actions.pad.onLogCurrent, onToggle = actions.set.onToggleComplete)
        }
        PadFor(state, actions, canHide = false)
    }
}

/** The set being typed into, or else the one that's up next, with a button to log it. */
@Composable
internal fun UpNext(
    state: LoggerUiState.Active,
    onLog: () -> Unit,
    onToggle: (setId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ref: SetRef? = state.edit?.let { state.workout.find(it.cell.setId) } ?: state.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (ref == null) {
            Text(stringResource(Res.string.focus_all_done), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(Res.string.focus_all_done_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            return@Column
        }
        val exercise = state.workout.exercises[ref.exerciseIndex]
        val set = state.workout.setAt(ref)
        val trackingType = state.trackingTypeOf(exercise.exerciseId)
        state.exercises[exercise.exerciseId]?.let { ExercisePicture(it, Modifier.width(PictureWidth)) }
        Text(
            text = state.exercises[exercise.exerciseId]?.name.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.focus_set, ref.setIndex + 1, exercise.sets.size),
            style = MaterialTheme.typography.titleMedium,
        )
        state.previousFor(ref)?.let { previous ->
            Text(
                text =
                    stringResource(
                        Res.string.focus_previous,
                        setSummary(
                            trackingType,
                            previous.weight,
                            previous.reps?.let(::RepRange),
                            previous.duration,
                            previous.distanceMeters,
                        ),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = { if (state.edit != null) onToggle(set.id) else onLog() },
            enabled = !set.isCompleted,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(52.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = LioraTheme.colors.completed,
                    contentColor = LioraTheme.colors.onCompleted,
                ),
        ) {
            Icon(painterResource(LioraIcons.Check), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(Res.string.focus_log), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Big enough to recognise from the bench, small enough to leave room for the rest ring. */
private val PictureWidth = 176.dp
