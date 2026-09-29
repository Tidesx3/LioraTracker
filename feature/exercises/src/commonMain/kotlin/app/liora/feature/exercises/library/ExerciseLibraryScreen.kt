package app.liora.feature.exercises.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.model.Equipment
import app.liora.core.model.Exercise
import app.liora.core.model.Muscle
import app.liora.core.navigation.LocalPaneRole
import app.liora.core.navigation.PaneRole
import app.liora.core.ui.currentLanguage
import app.liora.core.ui.label
import app.liora.feature.exercises.components.ExerciseRow
import app.liora.feature.exercises.resources.Res
import app.liora.feature.exercises.resources.cd_clear_search
import app.liora.feature.exercises.resources.cd_more
import app.liora.feature.exercises.resources.exercises_count
import app.liora.feature.exercises.resources.exercises_create_named
import app.liora.feature.exercises.resources.exercises_filter_custom
import app.liora.feature.exercises.resources.exercises_filter_done
import app.liora.feature.exercises.resources.exercises_filter_equipment
import app.liora.feature.exercises.resources.exercises_filter_muscle
import app.liora.feature.exercises.resources.exercises_filter_reset
import app.liora.feature.exercises.resources.exercises_hide_hidden
import app.liora.feature.exercises.resources.exercises_new_custom
import app.liora.feature.exercises.resources.exercises_no_results_body
import app.liora.feature.exercises.resources.exercises_no_results_title
import app.liora.feature.exercises.resources.exercises_search_hint
import app.liora.feature.exercises.resources.exercises_show_hidden
import app.liora.feature.exercises.resources.exercises_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ExerciseLibraryScreen(
    selectedId: String?,
    onOpenExercise: (String) -> Unit,
    onCreateExercise: (initialName: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseLibraryViewModel = koinViewModel(),
) {
    val language = currentLanguage()
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExerciseLibraryContent(
        query = viewModel.query,
        uiState = uiState,
        // Only a list with its detail pane beside it has a selection to show.
        selectedId = selectedId.takeIf { LocalPaneRole.current == PaneRole.List },
        actions =
            LibraryActions(
                onQueryChange = viewModel::onQueryChange,
                onFilterEvent = viewModel::onFilterEvent,
                onOpenExercise = onOpenExercise,
                onCreateExercise = onCreateExercise,
            ),
        modifier = modifier,
    )
}

internal class LibraryActions(
    val onQueryChange: (String) -> Unit,
    val onFilterEvent: (FilterEvent) -> Unit,
    val onOpenExercise: (String) -> Unit,
    val onCreateExercise: (String?) -> Unit,
)

private enum class FilterSheet { Muscles, Equipment }

@Composable
private fun ExerciseLibraryContent(
    query: String,
    uiState: ExerciseLibraryUiState,
    selectedId: String?,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
) {
    var openSheet by rememberSaveable { mutableStateOf<FilterSheet?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(query, uiState.filters) { listState.scrollToItem(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.exercises_title),
                actions = {
                    OverflowMenu(
                        showHidden = uiState.filters.showHidden,
                        onToggle = { actions.onFilterEvent(FilterEvent.ToggleShowHidden) },
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { actions.onCreateExercise(null) },
                icon = { Icon(painterResource(LioraIcons.Add), contentDescription = null) },
                text = { Text(stringResource(Res.string.exercises_new_custom)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            SearchField(query = query, onQueryChange = actions.onQueryChange)
            FilterRow(
                filters = uiState.filters,
                onOpenSheet = { openSheet = it },
                onToggleCustomOnly = { actions.onFilterEvent(FilterEvent.ToggleCustomOnly) },
            )
            if (!uiState.loading) {
                Text(
                    text =
                        pluralStringResource(
                            Res.plurals.exercises_count,
                            uiState.results.size,
                            uiState.results.size,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (!uiState.loading && uiState.results.isEmpty()) {
                NoResults(query = query, onCreate = actions.onCreateExercise)
            } else {
                ResultList(
                    results = uiState.results,
                    selectedId = selectedId,
                    listState = listState,
                    bottomPadding = padding.calculateBottomPadding(),
                    onOpenExercise = actions.onOpenExercise,
                )
            }
        }
    }

    when (openSheet) {
        FilterSheet.Muscles -> {
            ChoiceSheet(
                title = stringResource(Res.string.exercises_filter_muscle),
                options = Muscle.entries,
                selected = uiState.filters.muscles,
                label = { it.label },
                onToggle = { actions.onFilterEvent(FilterEvent.ToggleMuscle(it)) },
                onReset = { actions.onFilterEvent(FilterEvent.ClearMuscles) },
                onDismiss = { openSheet = null },
            )
        }

        FilterSheet.Equipment -> {
            ChoiceSheet(
                title = stringResource(Res.string.exercises_filter_equipment),
                options = Equipment.entries,
                selected = uiState.filters.equipment,
                label = { it.label },
                onToggle = { actions.onFilterEvent(FilterEvent.ToggleEquipment(it)) },
                onReset = { actions.onFilterEvent(FilterEvent.ClearEquipment) },
                onDismiss = { openSheet = null },
            )
        }

        null -> {
            Unit
        }
    }
}

@Composable
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text(stringResource(Res.string.exercises_search_hint)) },
        leadingIcon = { Icon(painterResource(LioraIcons.Search), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                LioraIconButton(
                    icon = LioraIcons.Close,
                    contentDescription = stringResource(Res.string.cd_clear_search),
                    onClick = { onQueryChange("") },
                )
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun FilterRow(
    filters: LibraryFilters,
    onOpenSheet: (FilterSheet) -> Unit,
    onToggleCustomOnly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DropdownChip(
            label = stringResource(Res.string.exercises_filter_muscle),
            count = filters.muscles.size,
            onClick = { onOpenSheet(FilterSheet.Muscles) },
        )
        DropdownChip(
            label = stringResource(Res.string.exercises_filter_equipment),
            count = filters.equipment.size,
            onClick = { onOpenSheet(FilterSheet.Equipment) },
        )
        FilterChip(
            selected = filters.customOnly,
            onClick = onToggleCustomOnly,
            label = { Text(stringResource(Res.string.exercises_filter_custom)) },
        )
    }
}

@Composable
private fun DropdownChip(
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = count > 0,
        onClick = onClick,
        label = { Text(if (count > 0) "$label · $count" else label) },
        trailingIcon = { Icon(painterResource(LioraIcons.DropDown), contentDescription = null) },
        modifier = modifier,
    )
}

@Composable
private fun ResultList(
    results: List<Exercise>,
    selectedId: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // Room for the floating button so the last row stays reachable.
        contentPadding = PaddingValues(bottom = bottomPadding + FAB_CLEARANCE),
    ) {
        items(results, key = { it.id }) { exercise ->
            ExerciseRow(
                exercise = exercise,
                onClick = { onOpenExercise(exercise.id) },
                selected = exercise.id == selectedId,
            )
        }
    }
}

@Composable
internal fun NoResults(
    query: String,
    onCreate: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = LioraIcons.Search,
        title = stringResource(Res.string.exercises_no_results_title),
        body = stringResource(Res.string.exercises_no_results_body),
        modifier = modifier,
        action =
            query.trim().takeIf { it.isNotEmpty() }?.let { name ->
                {
                    OutlinedButton(onClick = { onCreate(name) }) {
                        Text(stringResource(Res.string.exercises_create_named, name))
                    }
                }
            },
    )
}

@Composable
private fun OverflowMenu(
    showHidden: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LioraIconButton(
        icon = LioraIcons.More,
        contentDescription = stringResource(Res.string.cd_more),
        onClick = { expanded = true },
        modifier = modifier,
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (showHidden) Res.string.exercises_hide_hidden else Res.string.exercises_show_hidden,
                    ),
                )
            },
            onClick = {
                expanded = false
                onToggle()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceSheet(
    title: String,
    options: List<T>,
    selected: Set<T>,
    label: (T) -> StringResource,
    onToggle: (T) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onReset, enabled = selected.isNotEmpty()) {
                    Text(stringResource(Res.string.exercises_filter_reset))
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    FilterChip(
                        selected = option in selected,
                        onClick = { onToggle(option) },
                        label = { Text(stringResource(label(option))) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.exercises_filter_done))
            }
        }
    }
}

private val FAB_CLEARANCE = 88.dp
