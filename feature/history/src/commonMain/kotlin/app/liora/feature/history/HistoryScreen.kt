package app.liora.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
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
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.navigation.LocalPaneRole
import app.liora.core.navigation.PaneRole
import app.liora.core.ui.currentLanguage
import app.liora.feature.history.resources.Res
import app.liora.feature.history.resources.cd_show_calendar
import app.liora.feature.history.resources.cd_show_list
import app.liora.feature.history.resources.history_empty_body
import app.liora.feature.history.resources.history_empty_title
import app.liora.feature.history.resources.history_month_none
import app.liora.feature.history.resources.history_title
import app.liora.feature.history.resources.history_whole_month
import app.liora.feature.history.resources.history_workouts
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun HistoryScreen(
    selectedWorkoutId: String?,
    onOpenWorkout: (workoutId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val language = currentLanguage()
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(
        state = uiState,
        // Only beside the open workout does highlighting it help.
        selectedWorkoutId = selectedWorkoutId.takeIf { LocalPaneRole.current == PaneRole.List },
        onOpenWorkout = onOpenWorkout,
        modifier = modifier,
    )
}

@Composable
private fun HistoryContent(
    state: HistoryUiState,
    selectedWorkoutId: String?,
    onOpenWorkout: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.history_title),
                actions = {
                    if (state is HistoryUiState.Loaded) {
                        if (showCalendar) {
                            LioraIconButton(LioraIcons.ListView, stringResource(Res.string.cd_show_list), {
                                showCalendar = false
                            })
                        } else {
                            LioraIconButton(LioraIcons.Calendar, stringResource(Res.string.cd_show_calendar), {
                                showCalendar = true
                            })
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            HistoryUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding))
            }

            HistoryUiState.Empty -> {
                EmptyState(
                    icon = LioraIcons.History,
                    title = stringResource(Res.string.history_empty_title),
                    body = stringResource(Res.string.history_empty_body),
                    modifier = Modifier.padding(padding),
                )
            }

            is HistoryUiState.Loaded -> {
                if (showCalendar) {
                    CalendarView(state, selectedWorkoutId, onOpenWorkout, padding)
                } else {
                    WorkoutList(state, selectedWorkoutId, onOpenWorkout, padding)
                }
            }
        }
    }
}

/** Every workout, newest first, under a header per month. */
@Composable
private fun WorkoutList(
    state: HistoryUiState.Loaded,
    selectedWorkoutId: String?,
    onOpenWorkout: (String) -> Unit,
    padding: PaddingValues,
) {
    val dates = rememberDateFormatter()
    LazyColumn(Modifier.readableWidth(), contentPadding = listPadding(padding)) {
        state.months.forEach { month ->
            val workouts = state.inMonth(month)
            item(key = "month-$month") {
                MonthHeader(dates.month(month), workouts.size)
            }
            workoutCards(workouts, selectedWorkoutId, onOpenWorkout)
        }
    }
}

/**
 * A month's calendar with its workouts below; tapping a training day narrows them to that day. It
 * opens on the current month and goes back as far as the first workout.
 */
@Composable
private fun CalendarView(
    state: HistoryUiState.Loaded,
    selectedWorkoutId: String?,
    onOpenWorkout: (String) -> Unit,
    padding: PaddingValues,
) {
    val dates = rememberDateFormatter()
    val current = state.today.yearMonth
    val first = state.months.lastOrNull()?.takeIf { it < current } ?: current
    var month by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf(current) }
    var selectedDay by rememberSaveable(stateSaver = DaySaver) { mutableStateOf<LocalDate?>(null) }
    val shown =
        state.inMonth(month).filter { selectedDay == null || it.date == selectedDay }
    LazyColumn(Modifier.readableWidth(), contentPadding = listPadding(padding)) {
        item(key = "calendar") {
            MonthCalendar(
                state =
                    CalendarMonth(
                        month = month,
                        trainingDays = state.trainingDays,
                        today = state.today,
                        selectedDay = selectedDay,
                        canGoBack = month > first,
                        canGoForward = month < current,
                    ),
                actions =
                    CalendarActions(
                        onPrevious = {
                            month = month.minusMonth()
                            selectedDay = null
                        },
                        onNext = {
                            month = month.plusMonth()
                            selectedDay = null
                        },
                        onSelectDay = { day -> selectedDay = day.takeUnless { it == selectedDay } },
                    ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        item(key = "shown") {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedDay?.let { dates.shortDate(it) } ?: dates.month(month),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                )
                if (selectedDay != null) {
                    TextButton(
                        onClick = { selectedDay = null },
                    ) { Text(stringResource(Res.string.history_whole_month)) }
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "none") {
                Text(
                    text = stringResource(Res.string.history_month_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        workoutCards(shown, selectedWorkoutId, onOpenWorkout)
    }
}

private fun LazyListScope.workoutCards(
    workouts: List<WorkoutItem>,
    selectedWorkoutId: String?,
    onOpenWorkout: (String) -> Unit,
) {
    items(workouts, key = { it.id }) { item ->
        WorkoutCard(
            item = item,
            selected = item.id == selectedWorkoutId,
            onClick = { onOpenWorkout(item.id) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun MonthHeader(
    month: String,
    workouts: Int,
) {
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(month, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            text = pluralStringResource(Res.plurals.history_workouts, workouts, workouts),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun listPadding(padding: PaddingValues) =
    PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)

private val YearMonthSaver =
    Saver<YearMonth, String>(save = { it.toString() }, restore = { YearMonth.parse(it) })

private val DaySaver =
    Saver<LocalDate?, String>(save = { it?.toString() }, restore = { LocalDate.parse(it) })
