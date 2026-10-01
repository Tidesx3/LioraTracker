package app.liora.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.ui.currentLanguage
import app.liora.feature.progress.resources.Res
import app.liora.feature.progress.resources.progress_body_subtitle
import app.liora.feature.progress.resources.progress_body_title
import app.liora.feature.progress.resources.progress_consistency
import app.liora.feature.progress.resources.progress_empty_body
import app.liora.feature.progress.resources.progress_empty_title
import app.liora.feature.progress.resources.progress_muscles
import app.liora.feature.progress.resources.progress_records
import app.liora.feature.progress.resources.progress_sets_this_week
import app.liora.feature.progress.resources.progress_streak
import app.liora.feature.progress.resources.progress_streak_weeks
import app.liora.feature.progress.resources.progress_this_week
import app.liora.feature.progress.resources.progress_title
import app.liora.feature.progress.resources.progress_workouts
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Where the Progress tab leads. */
internal class ProgressNavigation(
    val onOpenExercise: (exerciseId: String) -> Unit,
    val onOpenBody: () -> Unit,
)

@Composable
internal fun ProgressScreen(
    viewModel: ProgressViewModel,
    navigation: ProgressNavigation,
    selectedExerciseId: String?,
    modifier: Modifier = Modifier,
) {
    val language = currentLanguage()
    val firstDayOfWeek = rememberDateFormatter().firstDayOfWeek
    LaunchedEffect(language, firstDayOfWeek) { viewModel.setLocale(language, firstDayOfWeek) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProgressContent(
        state = uiState,
        navigation = navigation,
        selectedExerciseId = selectedExerciseId,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        modifier = modifier,
    )
}

@Composable
private fun ProgressContent(
    state: ProgressUiState,
    navigation: ProgressNavigation,
    selectedExerciseId: String?,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { LioraTopAppBar(title = stringResource(Res.string.progress_title)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.readableWidth(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp),
        ) {
            when (state) {
                ProgressUiState.Loading -> {
                    Unit
                }

                ProgressUiState.Empty -> {
                    item {
                        EmptyState(
                            icon = LioraIcons.Progress,
                            title = stringResource(Res.string.progress_empty_title),
                            body = stringResource(Res.string.progress_empty_body),
                        )
                    }
                }

                is ProgressUiState.Loaded -> {
                    item(key = "summary") { Summary(state, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                    item(key = "consistency") {
                        SectionHeader(stringResource(Res.string.progress_consistency))
                        ConsistencyGrid(state.trainingDays, state.today, Modifier.padding(horizontal = 20.dp))
                    }
                    item(key = "muscles") {
                        SectionHeader(stringResource(Res.string.progress_muscles))
                        MuscleWeek(state, onPreviousWeek, onNextWeek, Modifier.padding(horizontal = 20.dp))
                    }
                    if (state.board.isNotEmpty()) {
                        item(key = "records") {
                            SectionHeader(stringResource(Res.string.progress_records))
                            RecordsBoard(state.board, selectedExerciseId, navigation.onOpenExercise)
                        }
                    }
                }
            }
            // Not while loading: the list would hold on to the card as the sections arrive above it and
            // open scrolled to the bottom.
            if (state != ProgressUiState.Loading) {
                item(key = "body") {
                    BodyCard(navigation.onOpenBody, Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
                }
            }
        }
    }
}

/** The streak, and this week so far. */
@Composable
private fun Summary(
    state: ProgressUiState.Loaded,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberNumberFormatter()
    // Equal thirds, so longer German labels wrap instead of pushing the last one off.
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SummaryStat(
            stringResource(Res.string.progress_streak),
            pluralStringResource(Res.plurals.progress_streak_weeks, state.streakWeeks, state.streakWeeks),
            Modifier.weight(1f),
        )
        SummaryStat(
            stringResource(Res.string.progress_this_week),
            pluralStringResource(Res.plurals.progress_workouts, state.workoutsThisWeek, state.workoutsThisWeek),
            Modifier.weight(1f),
        )
        SummaryStat(
            stringResource(Res.string.progress_sets_this_week),
            numbers.format(state.setsThisWeek),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge.tabularNumbers())
    }
}

@Composable
private fun BodyCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(Res.string.progress_body_title)) },
            supportingContent = { Text(stringResource(Res.string.progress_body_subtitle)) },
            leadingContent = {
                Icon(
                    painter = painterResource(LioraIcons.Body),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = {
                Icon(painter = painterResource(LioraIcons.ChevronRight), contentDescription = null)
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}
