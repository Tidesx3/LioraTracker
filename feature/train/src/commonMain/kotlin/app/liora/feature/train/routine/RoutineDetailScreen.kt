package app.liora.feature.train.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.domain.Supersets
import app.liora.core.model.Exercise
import app.liora.core.model.RoutineExercise
import app.liora.core.model.TrackingType
import app.liora.core.navigation.LocalPaneRole
import app.liora.core.navigation.PaneRole
import app.liora.core.ui.SetTypeBadge
import app.liora.core.ui.currentLanguage
import app.liora.core.ui.setNumbers
import app.liora.core.ui.setSummary
import app.liora.feature.train.resources.Res
import app.liora.feature.train.resources.cd_edit
import app.liora.feature.train.resources.cd_more
import app.liora.feature.train.resources.detail_exercises
import app.liora.feature.train.resources.detail_sets
import app.liora.feature.train.resources.detail_start
import app.liora.feature.train.resources.rest_off
import app.liora.feature.train.resources.rest_value
import app.liora.feature.train.resources.routine_copy_name
import app.liora.feature.train.resources.routine_delete
import app.liora.feature.train.resources.routine_duplicate
import app.liora.feature.train.resources.routine_move
import app.liora.feature.train.resources.superset
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun RoutineDetailScreen(
    viewModel: RoutineDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenLogger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = currentLanguage()
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Deleted (here or elsewhere): leave once the data is gone, never before the write lands.
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(uiState) { if (uiState == RoutineDetailUiState.Gone) currentOnBack() }

    val loaded = uiState as? RoutineDetailUiState.Loaded ?: return
    val copyName = stringResource(Res.string.routine_copy_name, loaded.routine.name)
    RoutineDetailContent(
        state = loaded,
        onBack = onBack,
        onEdit = onEdit,
        onStart = { if (viewModel.start()) onOpenLogger() },
        onMenu = { action ->
            when (action) {
                DetailMenuAction.Duplicate -> {
                    viewModel.duplicate(copyName)
                }

                DetailMenuAction.Move -> {
                    viewModel.showDialog(
                        RoutineDialog.MoveRoutine(loaded.routine.id, loaded.routine.folderId),
                    )
                }

                DetailMenuAction.Delete -> {
                    viewModel.showDialog(RoutineDialog.DeleteRoutine(loaded.routine.id))
                }
            }
        },
        modifier = modifier,
    )

    RoutineDialogHost(
        dialog = loaded.dialog,
        folders = loaded.folders,
        actions =
            RoutineDialogActions(
                onMoveRoutine = { _, folderId -> viewModel.moveToFolder(folderId) },
                onDeleteRoutine = { viewModel.delete() },
                onResumeWorkout = {
                    viewModel.dismissDialog()
                    onOpenLogger()
                },
                onDismiss = viewModel::dismissDialog,
            ),
    )
}

private enum class DetailMenuAction { Duplicate, Move, Delete }

@Composable
private fun RoutineDetailContent(
    state: RoutineDetailUiState.Loaded,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onMenu: (DetailMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val routine = state.routine
    val groups = routine.exercises.map { it.supersetGroup }
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
                    onClick = onStart,
                    modifier =
                        Modifier
                            .navigationBarsPadding()
                            .readableWidth()
                            .padding(16.dp)
                            .height(52.dp),
                ) {
                    Icon(painterResource(LioraIcons.Play), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.detail_start))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.readableWidth(),
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom =
                        padding.calculateBottomPadding() + 16.dp,
                ),
        ) {
            item(key = "header") {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(routine.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text =
                            pluralStringResource(
                                Res.plurals.detail_exercises,
                                routine.exercises.size,
                                routine.exercises.size,
                            ) +
                                " · " +
                                routine.exercises.sumOf { it.sets.size }.let {
                                    pluralStringResource(Res.plurals.detail_sets, it, it)
                                },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    routine.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            itemsIndexed(routine.exercises, key = { _, exercise -> exercise.id }) { index, planned ->
                // Exercises of one superset sit flush, so their bar reads as one.
                val linkedAbove = Supersets.continuesAbove(groups, index)
                val linkedBelow = index < groups.lastIndex && Supersets.continuesAbove(groups, index + 1)
                PlannedExercise(
                    planned = planned,
                    exercise = state.exercises[planned.exerciseId],
                    inSuperset = planned.supersetGroup != null,
                    startsSuperset = planned.supersetGroup != null && !linkedAbove,
                    modifier =
                        Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = if (linkedAbove) 0.dp else 4.dp,
                            bottom = if (linkedBelow) 0.dp else 4.dp,
                        ),
                )
            }
        }
    }
}

/** One exercise of the plan with its sets; supersets share a colored bar down the side. */
@Composable
private fun PlannedExercise(
    planned: RoutineExercise,
    exercise: Exercise?,
    inSuperset: Boolean,
    startsSuperset: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackingType = exercise?.trackingType ?: TrackingType.WeightReps
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (inSuperset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
        )
        Column(
            Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (startsSuperset) SupersetLabel()
            Text(exercise?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
            planned.restSeconds?.let { seconds ->
                Text(
                    text =
                        if (seconds == 0) {
                            stringResource(Res.string.rest_off)
                        } else {
                            stringResource(Res.string.rest_value, seconds.seconds.formatAsClock())
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            planned.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val numbers = setNumbers(planned.sets.map { it.type })
            planned.sets.forEachIndexed { index, set ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetTypeBadge(set.type, numbers[index])
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = setSummary(trackingType, set.weight, set.reps, set.duration, set.distanceMeters),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
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

@Composable
private fun DetailMenu(
    onMenu: (DetailMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LioraIconButton(LioraIcons.More, stringResource(Res.string.cd_more), { expanded = true }, modifier)
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        listOf(
            DetailMenuAction.Duplicate to Res.string.routine_duplicate,
            DetailMenuAction.Move to Res.string.routine_move,
            DetailMenuAction.Delete to Res.string.routine_delete,
        ).forEach { (action, label) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                onClick = {
                    expanded = false
                    onMenu(action)
                },
            )
        }
    }
}
