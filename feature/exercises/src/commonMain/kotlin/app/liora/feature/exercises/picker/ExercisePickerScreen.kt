package app.liora.feature.exercises.picker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.model.Exercise
import app.liora.core.ui.currentLanguage
import app.liora.feature.exercises.components.ExerciseRow
import app.liora.feature.exercises.library.NoResults
import app.liora.feature.exercises.library.SearchField
import app.liora.feature.exercises.resources.Res
import app.liora.feature.exercises.resources.cd_close
import app.liora.feature.exercises.resources.picker_add
import app.liora.feature.exercises.resources.picker_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Picks exercises for a routine or workout: search in either language, tap to pick (numbers show the
 * order they'll be added in), or create a missing exercise right from the search.
 */
@Composable
internal fun ExercisePickerScreen(
    viewModel: ExercisePickerViewModel,
    onClose: () -> Unit,
    onDone: (List<String>) -> Unit,
    onCreateExercise: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = currentLanguage()
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExercisePickerContent(
        query = viewModel.query,
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onToggle = viewModel::toggle,
        onClose = onClose,
        onDone = { onDone(uiState.selected) },
        onCreateExercise = { name -> name?.let(onCreateExercise) },
        modifier = modifier,
    )
}

@Composable
private fun ExercisePickerContent(
    query: String,
    uiState: ExercisePickerUiState,
    onQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit,
    onCreateExercise: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.picker_title),
                navigationIcon = { LioraIconButton(LioraIcons.Close, stringResource(Res.string.cd_close), onClose) },
            )
        },
        bottomBar = {
            if (uiState.selected.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Button(
                        onClick = onDone,
                        modifier =
                            Modifier
                                .navigationBarsPadding()
                                .readableWidth()
                                .padding(16.dp)
                                .height(52.dp),
                    ) {
                        Text(
                            pluralStringResource(Res.plurals.picker_add, uiState.selected.size, uiState.selected.size),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).readableWidth()) {
            SearchField(query = query, onQueryChange = onQueryChange)
            if (!uiState.loading && uiState.results.isEmpty()) {
                NoResults(query = query, onCreate = onCreateExercise)
            } else {
                LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = padding.calculateBottomPadding())) {
                    items(uiState.results, key = { it.id }) { exercise ->
                        PickerRow(
                            exercise = exercise,
                            order = uiState.selected.indexOf(exercise.id) + 1,
                            onToggle = { onToggle(exercise.id) },
                        )
                    }
                }
            }
        }
    }
}

/** [order] is the 1-based position among the picked exercises, or 0 when not picked. */
@Composable
private fun PickerRow(
    exercise: Exercise,
    order: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExerciseRow(
        exercise = exercise,
        onClick = onToggle,
        selected = order > 0,
        modifier = modifier,
        trailing = {
            if (order > 0) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = order.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        },
    )
}
