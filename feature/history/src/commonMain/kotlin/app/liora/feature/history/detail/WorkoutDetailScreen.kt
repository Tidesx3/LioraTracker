package app.liora.feature.history.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.designsystem.util.workoutDisplayName
import app.liora.core.domain.RecordKey
import app.liora.core.model.Exercise
import app.liora.core.model.RepRange
import app.liora.core.model.TrackingType
import app.liora.core.model.WorkoutExercise
import app.liora.core.navigation.LocalPaneRole
import app.liora.core.navigation.PaneRole
import app.liora.core.ui.ExerciseThumbnail
import app.liora.core.ui.SetTypeBadge
import app.liora.core.ui.currentLanguage
import app.liora.core.ui.recordLabel
import app.liora.core.ui.setNumbers
import app.liora.core.ui.setSummary
import app.liora.feature.history.WorkoutTotals
import app.liora.feature.history.resources.Res
import app.liora.feature.history.resources.cd_edit
import app.liora.feature.history.resources.cd_more
import app.liora.feature.history.resources.cd_records
import app.liora.feature.history.resources.date_time
import app.liora.feature.history.resources.detail_records
import app.liora.feature.history.resources.detail_repeat
import app.liora.feature.history.resources.menu_delete
import app.liora.feature.history.resources.menu_save_as_routine
import app.liora.feature.history.resources.superset
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Where the workout page leads. */
internal class WorkoutDetailNavigation(
    val onBack: () -> Unit,
    val onOpenExercise: (exerciseId: String) -> Unit,
    val onOpenLogger: () -> Unit,
    val onOpenRoutine: (routineId: String) -> Unit,
    /** Opens the workout in the logger to correct it. */
    val onEdit: () -> Unit,
)

@Composable
internal fun WorkoutDetailScreen(
    viewModel: WorkoutDetailViewModel,
    navigation: WorkoutDetailNavigation,
    modifier: Modifier = Modifier,
) {
    val language = currentLanguage()
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val routineToOpen by viewModel.routineToOpen.collectAsStateWithLifecycle()

    // Deleted (here or elsewhere): leave once the data is gone, never before the write lands.
    val currentNavigation by rememberUpdatedState(navigation)
    LaunchedEffect(uiState) { if (uiState == WorkoutDetailUiState.Gone) currentNavigation.onBack() }
    // Saved as a routine: open it once it exists.
    LaunchedEffect(routineToOpen) {
        routineToOpen?.let {
            viewModel.onRoutineOpened()
            currentNavigation.onOpenRoutine(it)
        }
    }

    val loaded = uiState as? WorkoutDetailUiState.Loaded ?: return
    WorkoutDetailContent(
        state = loaded,
        onBack = navigation.onBack,
        onOpenExercise = navigation.onOpenExercise,
        onRepeat = { if (viewModel.repeat()) navigation.onOpenLogger() },
        onEdit = navigation.onEdit,
        onMenu = viewModel::showDialog,
        modifier = modifier,
    )
    WorkoutDialogHost(
        dialog = loaded.dialog,
        defaultRoutineName = workoutDisplayName(loaded.workout.name),
        actions =
            WorkoutDialogActions(
                onSaveAsRoutine = viewModel::saveAsRoutine,
                onDelete = viewModel::delete,
                onResumeWorkout = {
                    viewModel.dismissDialog()
                    navigation.onOpenLogger()
                },
                onDismiss = viewModel::dismissDialog,
            ),
    )
}

@Composable
private fun WorkoutDetailContent(
    state: WorkoutDetailUiState.Loaded,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onRepeat: () -> Unit,
    onEdit: () -> Unit,
    onMenu: (WorkoutDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val workout = state.workout
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = "",
                // Beside the list there is nothing to go back to; the list is right there.
                navigationIcon = { if (LocalPaneRole.current != PaneRole.Detail) BackButton(onClick = onBack) },
                actions = {
                    LioraIconButton(LioraIcons.Edit, stringResource(Res.string.cd_edit), onEdit)
                    DetailMenu(onMenu)
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = onRepeat,
                    modifier =
                        Modifier
                            .navigationBarsPadding()
                            .readableWidth()
                            .padding(16.dp)
                            .height(52.dp),
                ) {
                    Icon(painterResource(LioraIcons.Repeat), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.detail_repeat))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.readableWidth(),
            contentPadding =
                PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item(key = "header") { Header(state, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            if (state.recordLines.isNotEmpty()) {
                item(key = "records") {
                    Records(state.recordLines, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            items(workout.exercises, key = { it.id }) { done ->
                DoneExercise(
                    done = done,
                    exercise = state.exercises[done.exerciseId],
                    trackingType = state.trackingTypeOf(done.exerciseId),
                    records = state.records,
                    onOpenExercise = { onOpenExercise(done.exerciseId) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(
    state: WorkoutDetailUiState.Loaded,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    val workout = state.workout
    val start = workout.startedAt.toLocalDateTime(TimeZone.currentSystemDefault())
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(workoutDisplayName(workout.name), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(Res.string.date_time, dates.longDate(start.date), dates.time(start.time)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WorkoutTotals(
            duration = workout.duration,
            volumeKg = state.volumeKg,
            sets = state.sets,
            records = state.recordLines.sumOf { it.second.size },
            valueStyle = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        workout.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** The records this workout set, gold like the trophies on their sets. */
@Composable
private fun Records(
    lines: List<Pair<String, List<RecordKey>>>,
    modifier: Modifier = Modifier,
) {
    val count = lines.sumOf { it.second.size }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = LioraTheme.colors.personalRecord.copy(alpha = RECORD_BACKGROUND_ALPHA),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(LioraIcons.Record),
                    contentDescription = null,
                    tint = LioraTheme.colors.personalRecord,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = pluralStringResource(Res.plurals.detail_records, count, count),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            lines.forEach { (exercise, keys) ->
                Text(
                    text = "$exercise: ${keys.map { recordLabel(it) }.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** One exercise as it was done: its photo and name (both open the exercise), then every set. */
@Composable
private fun DoneExercise(
    done: WorkoutExercise,
    exercise: Exercise?,
    trackingType: TrackingType,
    records: Map<String, Set<RecordKey>>,
    onOpenExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (done.supersetGroup != null) SupersetLabel()
        Row(verticalAlignment = Alignment.CenterVertically) {
            exercise?.let {
                ExerciseThumbnail(
                    exercise = it,
                    size = 40.dp,
                    modifier = Modifier.padding(end = 12.dp).clickable(onClick = onOpenExercise),
                )
            }
            Text(
                text = exercise?.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(enabled = exercise != null, onClick = onOpenExercise),
            )
        }
        done.notes?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val numbers = setNumbers(done.sets.map { it.type })
        done.sets.forEachIndexed { index, set ->
            Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SetTypeBadge(set.type, numbers[index])
                Spacer(Modifier.width(12.dp))
                Text(
                    text =
                        setSummary(
                            trackingType,
                            set.weight,
                            set.reps?.let(::RepRange),
                            set.duration,
                            set.distanceMeters,
                            set.rpe,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (!records[set.id].isNullOrEmpty()) {
                    Icon(
                        painterResource(LioraIcons.Record),
                        contentDescription = stringResource(Res.string.cd_records),
                        tint = LioraTheme.colors.personalRecord,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SupersetLabel(modifier: Modifier = Modifier) {
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

@Composable
private fun DetailMenu(
    onMenu: (WorkoutDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LioraIconButton(LioraIcons.More, stringResource(Res.string.cd_more), { expanded = true }, modifier)
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        listOf(
            WorkoutDialog.SaveAsRoutine to Res.string.menu_save_as_routine,
            WorkoutDialog.Delete to Res.string.menu_delete,
        ).forEach { (dialog, label) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                onClick = {
                    expanded = false
                    onMenu(dialog)
                },
            )
        }
    }
}

private const val RECORD_BACKGROUND_ALPHA = 0.14f
