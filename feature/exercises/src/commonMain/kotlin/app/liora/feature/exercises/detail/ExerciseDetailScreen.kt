package app.liora.feature.exercises.detail

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.model.Exercise
import app.liora.core.model.Muscle
import app.liora.core.ui.currentLanguage
import app.liora.core.ui.customExerciseBadge
import app.liora.core.ui.label
import app.liora.feature.exercises.resources.Res
import app.liora.feature.exercises.resources.cd_edit
import app.liora.feature.exercises.resources.cd_more
import app.liora.feature.exercises.resources.detail_cancel
import app.liora.feature.exercises.resources.detail_create_variation
import app.liora.feature.exercises.resources.detail_delete
import app.liora.feature.exercises.resources.detail_delete_body
import app.liora.feature.exercises.resources.detail_delete_title
import app.liora.feature.exercises.resources.detail_hide
import app.liora.feature.exercises.resources.detail_history
import app.liora.feature.exercises.resources.detail_history_empty
import app.liora.feature.exercises.resources.detail_in_use_body
import app.liora.feature.exercises.resources.detail_in_use_hide
import app.liora.feature.exercises.resources.detail_in_use_title
import app.liora.feature.exercises.resources.detail_instructions
import app.liora.feature.exercises.resources.detail_muscles
import app.liora.feature.exercises.resources.detail_notes
import app.liora.feature.exercises.resources.detail_primary
import app.liora.feature.exercises.resources.detail_secondary
import app.liora.feature.exercises.resources.detail_unhide
import app.liora.feature.exercises.resources.detail_variation_of
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExerciseDetailScreen(
    viewModel: ExerciseDetailViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onCreateVariation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = currentLanguage()
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Deleted (here or elsewhere): leave once the data is gone, never before the write lands.
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(uiState) { if (uiState == ExerciseDetailUiState.Gone) currentOnBack() }

    val loaded = uiState as? ExerciseDetailUiState.Loaded ?: return
    ExerciseDetailContent(
        state = loaded,
        onBack = onBack,
        onEdit = { onEdit(loaded.exercise.id) },
        onCreateVariation = { onCreateVariation(loaded.exercise.id) },
        onHiddenChange = viewModel::setHidden,
        onDelete = viewModel::requestDelete,
        modifier = modifier,
    )

    when (loaded.dialog) {
        DetailDialog.ConfirmDelete -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = { Text(stringResource(Res.string.detail_delete_title)) },
                text = { Text(stringResource(Res.string.detail_delete_body)) },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(Res.string.detail_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(Res.string.detail_cancel)) }
                },
            )
        }

        DetailDialog.InUse -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = { Text(stringResource(Res.string.detail_in_use_title)) },
                text = { Text(stringResource(Res.string.detail_in_use_body)) },
                confirmButton = {
                    TextButton(onClick = viewModel::hideInsteadOfDelete) {
                        Text(stringResource(Res.string.detail_in_use_hide))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(Res.string.detail_cancel)) }
                },
            )
        }

        null -> {
            Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseDetailContent(
    state: ExerciseDetailUiState.Loaded,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCreateVariation: () -> Unit,
    onHiddenChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercise = state.exercise
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = "",
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (exercise.isCustom) {
                        LioraIconButton(LioraIcons.Edit, stringResource(Res.string.cd_edit), onEdit)
                    }
                    DetailMenu(exercise = exercise, onHiddenChange = onHiddenChange, onDelete = onDelete)
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            if (exercise.imageUrls.isNotEmpty()) {
                item { ExerciseImages(exercise.imageUrls, Modifier.padding(horizontal = 16.dp)) }
            }
            item { Header(state, Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) }
            item {
                SectionHeader(stringResource(Res.string.detail_muscles))
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleLine(stringResource(Res.string.detail_primary), exercise.primaryMuscles, primary = true)
                    MuscleLine(stringResource(Res.string.detail_secondary), exercise.secondaryMuscles, primary = false)
                }
            }
            exercise.notes?.let { notes ->
                item {
                    SectionHeader(stringResource(Res.string.detail_notes))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            if (exercise.instructions.isNotEmpty()) {
                item { SectionHeader(stringResource(Res.string.detail_instructions)) }
                itemsIndexed(exercise.instructions) { index, step -> InstructionStep(index + 1, step) }
            }
            item {
                SectionHeader(stringResource(Res.string.detail_history))
                HistoryPlaceholder(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (!exercise.isCustom) {
                item {
                    OutlinedButton(
                        onClick = onCreateVariation,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    ) {
                        Text(stringResource(Res.string.detail_create_variation))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    state: ExerciseDetailUiState.Loaded,
    modifier: Modifier = Modifier,
) {
    val exercise = state.exercise
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
        state.baseName?.let {
            Text(
                text = stringResource(Res.string.detail_variation_of, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(onClick = {}, label = { Text(stringResource(exercise.trackingType.label)) })
            SuggestionChip(onClick = {}, label = { Text(stringResource(exercise.equipment.label)) })
            if (exercise.isCustom) SuggestionChip(onClick = {}, label = { Text(stringResource(customExerciseBadge)) })
        }
    }
}

/**
 * The dataset's start and end photos, alternated like a slow animation so the movement reads at a
 * glance. White backdrop because the photos are shot on white.
 */
@Composable
private fun ExerciseImages(
    urls: List<String>,
    modifier: Modifier = Modifier,
) {
    var frame by remember(urls) { mutableIntStateOf(0) }
    var loaded by remember(urls) { mutableStateOf(false) }
    var failed by remember(urls) { mutableStateOf(false) }
    // Offline or unavailable: show nothing rather than an empty frame.
    if (failed) return
    LaunchedEffect(urls, loaded) {
        while (loaded && urls.size > 1) {
            delay(FRAME_MILLIS)
            frame = (frame + 1) % urls.size
        }
    }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(IMAGE_ASPECT)
                .clip(MaterialTheme.shapes.large)
                // The photos are shot on white; until one arrives, a neutral tile avoids a white flash.
                .background(if (loaded) Color.White else MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Icon(
                painter = painterResource(LioraIcons.Exercise),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Crossfade(targetState = urls[frame], label = "exercise-frame") { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { if (!loaded) failed = true },
                modifier = Modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleLine(
    title: String,
    muscles: Set<Muscle>,
    primary: Boolean,
    modifier: Modifier = Modifier,
) {
    if (muscles.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            muscles.forEach { muscle -> MuscleChip(stringResource(muscle.label), primary) }
        }
    }
}

/** Primary muscles are filled, secondary ones outlined, so the main target reads at a glance. */
@Composable
private fun MuscleChip(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = if (primary) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor =
            if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (primary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun InstructionStep(
    number: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailMenu(
    exercise: Exercise,
    onHiddenChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LioraIconButton(LioraIcons.More, stringResource(Res.string.cd_more), { expanded = true }, modifier)
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        val hidden = exercise.settings.archived
        DropdownMenuItem(
            text = { Text(stringResource(if (hidden) Res.string.detail_unhide else Res.string.detail_hide)) },
            onClick = {
                expanded = false
                onHiddenChange(!hidden)
            },
        )
        if (exercise.isCustom) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.detail_delete), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** Until sets are logged for the exercise; history, records and charts replace this. */
@Composable
private fun HistoryPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(LioraIcons.History),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.detail_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val FRAME_MILLIS = 1_200L
private const val IMAGE_ASPECT = 4f / 3f
