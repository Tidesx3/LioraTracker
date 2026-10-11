package app.liora.feature.settings.importing

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.data.importing.FolderPlan
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraCard
import app.liora.core.designsystem.component.LioraCardDefaults
import app.liora.core.designsystem.component.LioraScaffold
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.illustration.Illustration
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.domain.importing.ExerciseChoice
import app.liora.core.ui.rememberFileOpener
import app.liora.core.ui.rememberTextClipboard
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.import_new_exercises
import app.liora.feature.settings.resources.import_reading
import app.liora.feature.settings.resources.import_section_matched
import app.liora.feature.settings.resources.import_section_new
import app.liora.feature.settings.resources.import_sets
import app.liora.feature.settings.resources.import_unreadable_body
import app.liora.feature.settings.resources.import_unreadable_title
import app.liora.feature.settings.resources.routine_import_confirm
import app.liora.feature.settings.resources.routine_import_confirm_exercises
import app.liora.feature.settings.resources.routine_import_copy_format
import app.liora.feature.settings.resources.routine_import_copy_format_body
import app.liora.feature.settings.resources.routine_import_done
import app.liora.feature.settings.resources.routine_import_done_routines
import app.liora.feature.settings.resources.routine_import_done_title
import app.liora.feature.settings.resources.routine_import_exercises
import app.liora.feature.settings.resources.routine_import_format_copied
import app.liora.feature.settings.resources.routine_import_format_label
import app.liora.feature.settings.resources.routine_import_hint
import app.liora.feature.settings.resources.routine_import_misfit
import app.liora.feature.settings.resources.routine_import_new_folder
import app.liora.feature.settings.resources.routine_import_new_rotation
import app.liora.feature.settings.resources.routine_import_open
import app.liora.feature.settings.resources.routine_import_paste
import app.liora.feature.settings.resources.routine_import_routines
import app.liora.feature.settings.resources.routine_import_section_notes
import app.liora.feature.settings.resources.routine_import_section_routines
import app.liora.feature.settings.resources.routine_import_start_body
import app.liora.feature.settings.resources.routine_import_start_over
import app.liora.feature.settings.resources.routine_import_start_title
import app.liora.feature.settings.resources.routine_import_title
import app.liora.feature.settings.resources.routine_import_use_body
import app.liora.feature.settings.resources.routine_import_working
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Routines from a routine file, which an AI writes for the user: open or paste one, see what it holds and
 * where each exercise goes, and add it.
 */
@Composable
internal fun RoutineImportScreen(
    viewModel: RoutineImportViewModel,
    onClose: () -> Unit,
    onPickExercise: () -> Unit,
    onDone: (routineIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = rememberTextClipboard()
    val opener = rememberFileOpener(ROUTINE_TYPES, viewModel::open)
    RoutineImportContent(
        state = state,
        actions =
            RoutineImportActions(
                onClose = onClose,
                onOpen = opener::open,
                onPaste = { viewModel.paste(clipboard.text()) },
                onStartOver = viewModel::startOver,
                onChoose = viewModel::choose,
                onPick = { name ->
                    viewModel.startPicking(name)
                    onPickExercise()
                },
                onImport = viewModel::import,
                onDone = onDone,
            ),
        modifier = modifier,
    )
}

@Suppress("LongParameterList") // one per thing the screen offers, start to finish
internal class RoutineImportActions(
    val onClose: () -> Unit,
    val onOpen: () -> Unit,
    val onPaste: () -> Unit,
    val onStartOver: () -> Unit,
    val onChoose: (name: String, choice: ExerciseChoice) -> Unit,
    val onPick: (name: String) -> Unit,
    val onImport: () -> Unit,
    val onDone: (routineIds: List<String>) -> Unit,
)

@Composable
private fun RoutineImportContent(
    state: RoutineImportUiState,
    actions: RoutineImportActions,
    modifier: Modifier = Modifier,
) {
    LioraScaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.routine_import_title),
                navigationIcon = { BackButton(onClick = actions.onClose) },
            )
        },
        bottomBar = {
            when (state) {
                is RoutineImportUiState.Review -> ImportBar(state, actions.onImport)
                is RoutineImportUiState.Invalid -> ProblemsBar(state.problems, actions.onStartOver)
                else -> Unit
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                RoutineImportUiState.Start -> Start(actions)
                RoutineImportUiState.Reading -> Reading()
                RoutineImportUiState.Unreadable -> Unreadable(actions.onStartOver)
                is RoutineImportUiState.Invalid -> Problems(state.problems)
                is RoutineImportUiState.Review -> Review(state, actions)
                is RoutineImportUiState.Done -> Done(state, actions.onDone)
            }
        }
    }
}

/** How routines get here: a file, a paste, and the format to hand an AI first. */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun Start(
    actions: RoutineImportActions,
    modifier: Modifier = Modifier,
) {
    val clipboard = rememberTextClipboard()
    val scope = rememberCoroutineScope()
    var copied by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(Res.string.routine_import_format_label)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            illustration = Illustration.Routines,
            title = stringResource(Res.string.routine_import_start_title),
            body = stringResource(Res.string.routine_import_start_body),
            modifier = Modifier.readableWidth(),
            action = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = actions.onOpen) {
                            Icon(painterResource(LioraIcons.Folder), contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(Res.string.routine_import_open))
                        }
                        FilledTonalButton(onClick = actions.onPaste) {
                            Text(stringResource(Res.string.routine_import_paste))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                clipboard.copy(label, Res.readBytes(FORMAT_FILE).decodeToString())
                                copied = true
                            }
                        },
                    ) { Text(stringResource(Res.string.routine_import_copy_format)) }
                    Text(
                        text =
                            stringResource(
                                if (copied) {
                                    Res.string.routine_import_format_copied
                                } else {
                                    Res.string.routine_import_copy_format_body
                                },
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
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
private fun Unreadable(
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = Illustration.UnknownFile,
        title = stringResource(Res.string.import_unreadable_title),
        body = stringResource(Res.string.import_unreadable_body),
        modifier = modifier.readableWidth().padding(top = 48.dp),
        action = {
            TextButton(onClick = onStartOver) { Text(stringResource(Res.string.routine_import_start_over)) }
        },
    )
}

@Composable
private fun Review(
    state: RoutineImportUiState.Review,
    actions: RoutineImportActions,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(modifier = modifier.readableWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "summary") { Summary(state.folder, state.routines.size) }
        if (state.routines.isNotEmpty()) {
            item(key = "routines-header") { SectionHeader(stringResource(Res.string.routine_import_section_routines)) }
            item(key = "routines") { RoutineCard(state.routines) }
        }
        if (state.newExercises.isNotEmpty() || state.matchedExercises.isNotEmpty()) {
            item(key = "hint") {
                Text(
                    text = stringResource(Res.string.routine_import_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        exerciseSection(
            "new",
            Res.string.import_section_new,
            state.newExercises,
            state.importing,
            Res.string.routine_import_misfit,
        ) { open = it }
        exerciseSection(
            "matched",
            Res.string.import_section_matched,
            state.matchedExercises,
            state.importing,
            Res.string.routine_import_misfit,
        ) { open = it }
        if (state.warnings.isNotEmpty()) {
            item(key = "notes-header") { SectionHeader(stringResource(Res.string.routine_import_section_notes)) }
            item(key = "notes") { ProblemCard(state.warnings) }
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
            useBody = Res.string.routine_import_use_body,
        )
    }
}

/** The program's folder, or how many routines there are. */
@Composable
private fun Summary(
    folder: FolderPlan?,
    routines: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = folder?.name ?: pluralStringResource(Res.plurals.routine_import_routines, routines, routines),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (folder != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (folder.rotation) LioraIcons.Repeat else LioraIcons.Folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text =
                        stringResource(
                            if (folder.rotation) {
                                Res.string.routine_import_new_rotation
                            } else {
                                Res.string.routine_import_new_folder
                            },
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RoutineCard(
    routines: List<RoutineSummary>,
    modifier: Modifier = Modifier,
) {
    LioraCard(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            routines.forEach { routine ->
                ListItem(
                    headlineContent = { Text(routine.name) },
                    supportingContent = {
                        val exercises = routine.exercises
                        Text(
                            listOf(
                                pluralStringResource(Res.plurals.routine_import_exercises, exercises, exercises),
                                pluralStringResource(Res.plurals.import_sets, routine.sets, routine.sets),
                            ).joinToString(" · "),
                        )
                    },
                    colors = LioraCardDefaults.listItemColors(),
                )
            }
        }
    }
}

/** The add button, at the bottom where the thumb is. */
@Composable
private fun ImportBar(
    state: RoutineImportUiState.Review,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routines = state.routines.size
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Button(
            onClick = onImport,
            enabled = !state.importing,
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
                Text(stringResource(Res.string.routine_import_working))
            } else if (routines > 0) {
                Text(pluralStringResource(Res.plurals.routine_import_confirm, routines, routines))
            } else {
                Text(stringResource(Res.string.routine_import_confirm_exercises))
            }
        }
    }
}

@Composable
private fun Done(
    state: RoutineImportUiState.Done,
    onDone: (routineIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val result = state.result
    val routines = result.routineIds.size
    val lines =
        listOfNotNull(
            routines.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.routine_import_done_routines, it, it) },
            result.newExercises.takeIf { it > 0 }?.let {
                pluralStringResource(Res.plurals.import_new_exercises, it, it)
            },
        )
    EmptyState(
        illustration = Illustration.Done,
        title = stringResource(Res.string.routine_import_done_title),
        body = lines.joinToString("\n"),
        modifier = modifier.readableWidth().padding(top = 48.dp),
        action = {
            Button(onClick = { onDone(result.routineIds) }) { Text(stringResource(Res.string.routine_import_done)) }
        },
    )
}

/** Routine files are JSON, which phones and drives label in several ways. */
private val ROUTINE_TYPES = listOf("application/json", "text/*", "application/octet-stream")

/** docs/routine-format.md, as the app carries it to hand to an AI. */
private const val FORMAT_FILE = "files/routine-format.md"
