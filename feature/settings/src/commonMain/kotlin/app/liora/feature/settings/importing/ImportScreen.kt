package app.liora.feature.settings.importing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraScaffold
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.illustration.Illustration
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.domain.importing.ExerciseChoice
import app.liora.core.domain.importing.ImportSource
import app.liora.core.model.WeightUnit
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.backup_measurements
import app.liora.feature.settings.resources.backup_workouts
import app.liora.feature.settings.resources.dialog_cancel
import app.liora.feature.settings.resources.import_choice_new
import app.liora.feature.settings.resources.import_choice_pick
import app.liora.feature.settings.resources.import_choice_use
import app.liora.feature.settings.resources.import_choice_use_body
import app.liora.feature.settings.resources.import_choice_variation
import app.liora.feature.settings.resources.import_close
import app.liora.feature.settings.resources.import_confirm
import app.liora.feature.settings.resources.import_done
import app.liora.feature.settings.resources.import_done_title
import app.liora.feature.settings.resources.import_exercises_hint
import app.liora.feature.settings.resources.import_from_hevy
import app.liora.feature.settings.resources.import_from_strong
import app.liora.feature.settings.resources.import_goes_to
import app.liora.feature.settings.resources.import_measurements_here
import app.liora.feature.settings.resources.import_misfit
import app.liora.feature.settings.resources.import_new_exercise
import app.liora.feature.settings.resources.import_new_exercises
import app.liora.feature.settings.resources.import_new_variation
import app.liora.feature.settings.resources.import_not_an_export_body
import app.liora.feature.settings.resources.import_not_an_export_title
import app.liora.feature.settings.resources.import_nothing_new
import app.liora.feature.settings.resources.import_range
import app.liora.feature.settings.resources.import_reading
import app.liora.feature.settings.resources.import_section_matched
import app.liora.feature.settings.resources.import_section_new
import app.liora.feature.settings.resources.import_sets
import app.liora.feature.settings.resources.import_title
import app.liora.feature.settings.resources.import_unit
import app.liora.feature.settings.resources.import_unit_body
import app.liora.feature.settings.resources.import_unreadable_body
import app.liora.feature.settings.resources.import_unreadable_title
import app.liora.feature.settings.resources.import_working
import app.liora.feature.settings.resources.import_workouts_here
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What a file from Hevy or Strong holds and where each exercise's sets go, before it's imported. */
@Composable
internal fun ImportScreen(
    viewModel: ImportViewModel,
    onClose: () -> Unit,
    onPickExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ImportContent(
        state = state,
        actions =
            ImportActions(
                onClose = onClose,
                onChoose = viewModel::choose,
                onPick = { name ->
                    viewModel.startPicking(name)
                    onPickExercise()
                },
                onWeightUnit = viewModel::setWeightUnit,
                onImport = viewModel::import,
            ),
        modifier = modifier,
    )
}

internal class ImportActions(
    val onClose: () -> Unit,
    val onChoose: (name: String, choice: ExerciseChoice) -> Unit,
    val onPick: (name: String) -> Unit,
    val onWeightUnit: (WeightUnit) -> Unit,
    val onImport: () -> Unit,
)

@Composable
private fun ImportContent(
    state: ImportUiState,
    actions: ImportActions,
    modifier: Modifier = Modifier,
) {
    val title =
        when ((state as? ImportUiState.Review)?.source) {
            ImportSource.Hevy -> stringResource(Res.string.import_from_hevy)
            ImportSource.Strong -> stringResource(Res.string.import_from_strong)
            null -> stringResource(Res.string.import_title)
        }
    LioraScaffold(
        modifier = modifier,
        topBar = { LioraTopAppBar(title = title, navigationIcon = { BackButton(onClick = actions.onClose) }) },
        bottomBar = {
            if (state is ImportUiState.Review) ImportBar(state, actions.onImport)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ImportUiState.Reading -> Reading()
                is ImportUiState.Failed -> Failed(state.problem, actions.onClose)
                is ImportUiState.Review -> Review(state, actions)
                is ImportUiState.Done -> Done(state, actions.onClose)
            }
        }
    }
}

@Composable
private fun Reading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.import_reading), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Failed(
    problem: ImportProblem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notAnExport = problem == ImportProblem.NotAnExport
    EmptyState(
        illustration = Illustration.UnknownFile,
        title =
            stringResource(
                if (notAnExport) Res.string.import_not_an_export_title else Res.string.import_unreadable_title,
            ),
        body =
            stringResource(
                if (notAnExport) Res.string.import_not_an_export_body else Res.string.import_unreadable_body,
            ),
        modifier = modifier.readableWidth().padding(top = 48.dp),
        action = { TextButton(onClick = onClose) { Text(stringResource(Res.string.import_close)) } },
    )
}

@Composable
private fun Review(
    state: ImportUiState.Review,
    actions: ImportActions,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(modifier = modifier.readableWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "summary") { Summary(state) }
        val unit = state.assumedWeightUnit
        if (unit != null) {
            item(key = "unit") { UnitChoice(unit, enabled = !state.importing, onUnit = actions.onWeightUnit) }
        }
        if (state.newExercises.isNotEmpty() || state.matchedExercises.isNotEmpty()) {
            item(key = "hint") {
                Text(
                    text = stringResource(Res.string.import_exercises_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        exerciseSection("new", Res.string.import_section_new, state.newExercises, state.importing) { open = it }
        exerciseSection("matched", Res.string.import_section_matched, state.matchedExercises, state.importing) {
            open = it
        }
    }
    val row = (state.newExercises + state.matchedExercises).firstOrNull { it.name == open }
    if (row != null) {
        ExerciseChoiceDialog(
            row = row,
            onChoose = { choice ->
                actions.onChoose(row.name, choice)
                open = null
            },
            onPick = {
                open = null
                actions.onPick(row.name)
            },
            onDismiss = { open = null },
        )
    }
}

/** How much is new, from when to when, and what's here already. */
@Composable
private fun Summary(
    state: ImportUiState.Review,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val headline =
            when {
                state.isEmpty -> stringResource(Res.string.import_nothing_new)
                state.workouts > 0 -> pluralStringResource(Res.plurals.backup_workouts, state.workouts, state.workouts)
                else -> pluralStringResource(Res.plurals.backup_measurements, state.measurements, state.measurements)
            }
        Text(headline, style = MaterialTheme.typography.headlineSmall)
        val days = state.days
        val details =
            listOfNotNull(
                state.sets.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.import_sets, it, it) },
                days?.let {
                    stringResource(
                        Res.string.import_range,
                        dates.mediumDate(it.start),
                        dates.mediumDate(it.endInclusive),
                    )
                },
            )
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.workoutsHere > 0) {
            Text(
                pluralStringResource(Res.plurals.import_workouts_here, state.workoutsHere, state.workoutsHere),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.measurementsHere > 0) {
            Text(
                pluralStringResource(
                    Res.plurals.import_measurements_here,
                    state.measurementsHere,
                    state.measurementsHere,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** For files that don't say their unit: which one the weights were logged in. */
@Composable
private fun UnitChoice(
    unit: WeightUnit,
    enabled: Boolean,
    onUnit: (WeightUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.import_unit),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            SingleChoiceSegmentedButtonRow {
                WeightUnit.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == unit,
                        onClick = { onUnit(option) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index, WeightUnit.entries.size),
                    ) { Text(option.symbol) }
                }
            }
        }
        Text(
            stringResource(Res.string.import_unit_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The import button, at the bottom where the thumb is. */
@Composable
private fun ImportBar(
    state: ImportUiState.Review,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Button(
            onClick = onImport,
            enabled = !state.isEmpty && !state.importing,
            modifier =
                Modifier
                    .navigationBarsPadding()
                    .readableWidth()
                    .padding(16.dp)
                    .height(52.dp),
        ) {
            if (state.importing) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(Res.string.import_working))
            } else {
                Text(stringResource(Res.string.import_confirm))
            }
        }
    }
}

@Composable
private fun Done(
    state: ImportUiState.Done,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val result = state.result
    val lines =
        listOfNotNull(
            result.workouts.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.backup_workouts, it, it) },
            result.sets.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.import_sets, it, it) },
            result.newExercises.takeIf { it > 0 }?.let {
                pluralStringResource(
                    Res.plurals.import_new_exercises,
                    it,
                    it,
                )
            },
            result.measurements.takeIf { it > 0 }?.let {
                pluralStringResource(
                    Res.plurals.backup_measurements,
                    it,
                    it,
                )
            },
        )
    EmptyState(
        illustration = Illustration.Done,
        title = stringResource(Res.string.import_done_title),
        body = lines.joinToString("\n"),
        modifier = modifier.readableWidth().padding(top = 48.dp),
        action = { Button(onClick = onClose) { Text(stringResource(Res.string.import_done)) } },
    )
}
