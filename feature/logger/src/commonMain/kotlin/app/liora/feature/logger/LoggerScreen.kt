package app.liora.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.designsystem.util.rememberElapsedTime
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.designsystem.util.workoutDisplayName
import app.liora.core.model.ActiveWorkout
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.cd_minimize
import app.liora.feature.logger.resources.logger_discard
import app.liora.feature.logger.resources.logger_discard_body
import app.liora.feature.logger.resources.logger_discard_cancel
import app.liora.feature.logger.resources.logger_discard_confirm
import app.liora.feature.logger.resources.logger_discard_title
import app.liora.feature.logger.resources.logger_empty_body
import app.liora.feature.logger.resources.logger_empty_title
import app.liora.feature.logger.resources.logger_finish
import app.liora.feature.logger.resources.logger_no_workout_body
import app.liora.feature.logger.resources.logger_no_workout_title
import app.liora.feature.logger.resources.logger_stat_duration
import app.liora.feature.logger.resources.logger_stat_sets
import app.liora.feature.logger.resources.logger_stat_volume
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun LoggerScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoggerViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Close once the database confirms the workout ended. Closing right away would clear this
    // ViewModel and cancel the finish/discard write before it lands.
    var hadWorkout by remember { mutableStateOf(false) }
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(uiState) {
        when (uiState) {
            is LoggerUiState.Active -> hadWorkout = true
            LoggerUiState.NoWorkout -> if (hadWorkout) currentOnClose()
            LoggerUiState.Loading -> Unit
        }
    }

    if (uiState == LoggerUiState.Loading) return
    LoggerContent(
        workout = (uiState as? LoggerUiState.Active)?.workout,
        onMinimize = onClose,
        onFinish = viewModel::finish,
        onDiscard = viewModel::discard,
        modifier = modifier,
    )
}

@Composable
private fun LoggerContent(
    workout: ActiveWorkout?,
    onMinimize: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = workoutDisplayName(workout?.name),
                navigationIcon = {
                    LioraIconButton(
                        icon = LioraIcons.ExpandDown,
                        contentDescription = stringResource(Res.string.cd_minimize),
                        onClick = onMinimize,
                    )
                },
                actions = {
                    if (workout != null) {
                        Button(onClick = onFinish, modifier = Modifier.padding(end = 8.dp)) {
                            Text(stringResource(Res.string.logger_finish))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (workout == null) {
            EmptyState(
                icon = LioraIcons.Train,
                title = stringResource(Res.string.logger_no_workout_title),
                body = stringResource(Res.string.logger_no_workout_body),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            WorkoutStats(workout = workout, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            EmptyState(
                icon = LioraIcons.Exercise,
                title = stringResource(Res.string.logger_empty_title),
                body = stringResource(Res.string.logger_empty_body),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { confirmDiscard = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp),
            ) {
                Text(stringResource(Res.string.logger_discard))
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(Res.string.logger_discard_title)) },
            text = { Text(stringResource(Res.string.logger_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onDiscard()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(Res.string.logger_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(Res.string.logger_discard_cancel))
                }
            },
        )
    }
}

@Composable
private fun WorkoutStats(
    workout: ActiveWorkout,
    modifier: Modifier = Modifier,
) {
    val elapsed = rememberElapsedTime(workout.startedAt)
    val numbers = rememberNumberFormatter()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat(label = stringResource(Res.string.logger_stat_duration), value = elapsed.formatAsClock(), highlight = true)
        // Volume and set counts become live once set logging lands.
        Stat(label = stringResource(Res.string.logger_stat_volume), value = "${numbers.format(0)} kg")
        Stat(label = stringResource(Res.string.logger_stat_sets), value = numbers.format(0))
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.tabularNumbers(),
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
