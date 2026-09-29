package app.liora.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.currentWindowLayout
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.designsystem.util.rememberElapsedTime
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.designsystem.util.workoutDisplayName
import app.liora.core.domain.SetField
import app.liora.core.domain.find
import app.liora.core.model.WeightUnit
import app.liora.core.ui.currentLanguage
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.cd_minimize
import app.liora.feature.logger.resources.cd_rename
import app.liora.feature.logger.resources.logger_add_exercises
import app.liora.feature.logger.resources.logger_discard
import app.liora.feature.logger.resources.logger_empty_body
import app.liora.feature.logger.resources.logger_empty_title
import app.liora.feature.logger.resources.logger_finish
import app.liora.feature.logger.resources.logger_no_workout_body
import app.liora.feature.logger.resources.logger_no_workout_title
import app.liora.feature.logger.resources.logger_stat_duration
import app.liora.feature.logger.resources.logger_stat_sets
import app.liora.feature.logger.resources.logger_stat_volume
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** What the logger asks of the app: closing, and picking exercises in the exercise picker. */
internal class LoggerNavigationActions(
    val onClose: () -> Unit,
    val onAddExercises: () -> Unit,
    val onReplaceExercise: () -> Unit,
)

@Composable
internal fun LoggerScreen(
    viewModel: LoggerViewModel,
    navigation: LoggerNavigationActions,
    modifier: Modifier = Modifier,
) {
    val language = currentLanguage()
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Close once the database confirms the workout ended. Closing right away would clear this
    // ViewModel and cancel the finish/discard write before it lands.
    var hadWorkout by remember { mutableStateOf(false) }
    val currentOnClose by rememberUpdatedState(navigation.onClose)
    LaunchedEffect(uiState) {
        when (uiState) {
            is LoggerUiState.Active -> hadWorkout = true
            LoggerUiState.NoWorkout -> if (hadWorkout) currentOnClose()
            LoggerUiState.Loading -> Unit
        }
    }

    // Back closes the pad before it leaves the logger.
    val editing = (uiState as? LoggerUiState.Active)?.edit != null
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = editing,
        onBackCompleted = viewModel::closePad,
    )

    val state = uiState
    if (state == LoggerUiState.Loading) return
    var dialog by rememberSaveable { mutableStateOf<LoggerDialog?>(null) }
    LoggerContent(
        state = state as? LoggerUiState.Active,
        actions =
            loggerActions(viewModel, navigation) {
                viewModel.closePad()
                dialog = it
            },
        modifier = modifier,
    )
    (state as? LoggerUiState.Active)?.let { active ->
        LoggerDialogHost(
            dialog = dialog,
            state = active,
            actions =
                LoggerDialogActions(
                    onRename = viewModel::rename,
                    onDiscard = viewModel::discard,
                    onReorder = viewModel::reorder,
                    onFinish = viewModel::finish,
                    onDismiss = { dialog = null },
                ),
        )
    }
}

/** Everything the logger's content can do, gathered once so the layouts below stay readable. */
internal class LoggerActions(
    val navigation: LoggerNavigationActions,
    val exercise: ExerciseActions,
    val set: SetRowActions,
    val pad: PadActions,
    val rest: RestActions,
    val onShowDialog: (LoggerDialog) -> Unit,
)

internal class PadActions(
    val onKey: (PadKey) -> Unit,
    /** Ticks off the set that's up next. */
    val onLogCurrent: () -> Unit,
    /** A text field got focus; the pad makes way for the system keyboard. */
    val onTextFocus: () -> Unit,
)

internal class RestActions(
    val onAdjust: (seconds: Int) -> Unit,
    val onSkip: () -> Unit,
)

private fun loggerActions(
    viewModel: LoggerViewModel,
    navigation: LoggerNavigationActions,
    onShowDialog: (LoggerDialog) -> Unit,
) = LoggerActions(
    navigation = navigation,
    exercise =
        ExerciseActions(
            onReplace = { id ->
                viewModel.startReplacing(id)
                navigation.onReplaceExercise()
            },
            onReorder = { onShowDialog(LoggerDialog.Reorder) },
            onSupersetChange = { id, link -> if (link) viewModel.linkWithNext(id) else viewModel.unlink(id) },
            onRemove = viewModel::removeExercise,
            onRestChange = viewModel::setRest,
            onNotesChange = viewModel::setNotes,
            onAddSet = viewModel::addSet,
        ),
    set =
        SetRowActions(
            onCellClick = viewModel::focus,
            onToggleComplete = viewModel::toggleComplete,
            onCopyPrevious = viewModel::copyPrevious,
            onTypeChange = viewModel::setType,
            onRemove = viewModel::removeSet,
        ),
    pad =
        PadActions(
            onKey = viewModel::onKey,
            onLogCurrent = viewModel::completeCurrent,
            onTextFocus = viewModel::closePad,
        ),
    rest = RestActions(onAdjust = viewModel::adjustRest, onSkip = viewModel::skipRest),
    onShowDialog = onShowDialog,
)

@Composable
private fun LoggerContent(
    state: LoggerUiState.Active?,
    actions: LoggerActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = workoutDisplayName(state?.workout?.name),
                navigationIcon = {
                    LioraIconButton(
                        LioraIcons.ExpandDown,
                        stringResource(Res.string.cd_minimize),
                        actions.navigation.onClose,
                    )
                },
                actions = {
                    if (state != null) {
                        LioraIconButton(
                            LioraIcons.Edit,
                            stringResource(Res.string.cd_rename),
                            { actions.onShowDialog(LoggerDialog.Rename) },
                        )
                        Button(
                            onClick = { actions.onShowDialog(LoggerDialog.Finish) },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(stringResource(Res.string.logger_finish))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state == null) {
            EmptyState(
                icon = LioraIcons.Train,
                title = stringResource(Res.string.logger_no_workout_title),
                body = stringResource(Res.string.logger_no_workout_body),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            WorkoutStats(state, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val layout = currentWindowLayout()
            when {
                layout.tabletop -> {
                    TabletopLogger(state, actions, Modifier.weight(1f))
                }

                layout.twoPane -> {
                    Row(Modifier.weight(1f)) {
                        WorkoutList(state, actions, Modifier.weight(1f).fillMaxHeight())
                        VerticalDivider()
                        FocusPane(state, actions, Modifier.width(FocusPaneWidth).fillMaxHeight())
                    }
                }

                else -> {
                    CompactLogger(state, actions, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Phones and the Fold's cover screen: the list, with the rest timer and the pad docked below it. */
@Composable
private fun CompactLogger(
    state: LoggerUiState.Active,
    actions: LoggerActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        WorkoutList(state, actions, Modifier.weight(1f))
        rememberRestProgress(state.restTimer)?.let { rest ->
            RestTimerBar(rest, onAdjust = actions.rest.onAdjust, onSkip = actions.rest.onSkip)
        }
        val edit = state.edit
        if (edit != null) {
            PadFor(state, actions)
        }
    }
}

/** The number pad set up for the cell being typed into, or for the set that's up next. */
@Composable
internal fun PadFor(
    state: LoggerUiState.Active,
    actions: LoggerActions,
    modifier: Modifier = Modifier,
    canHide: Boolean = true,
) {
    val cell = state.edit?.cell
    val ref = cell?.let { state.workout.find(it.setId) } ?: state.current
    val trackingType = ref?.let { state.trackingTypeOf(state.workout.exercises[it.exerciseIndex].exerciseId) }
    val fields = trackingType?.let(SetField::of).orEmpty()
    val field = cell?.field ?: fields.firstOrNull()
    // Clear focus from a note being typed, so the system keyboard and the pad never both show.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(cell) { if (cell != null) focusManager.clearFocus() }
    NumberPad(
        decimals = trackingType != null && field?.inputKind(trackingType) == InputKind.Decimal,
        nextLogsSet = field != null && field == fields.lastOrNull(),
        onKey = actions.pad.onKey,
        canHide = canHide,
        modifier = modifier,
    )
}

@Composable
internal fun WorkoutList(
    state: LoggerUiState.Active,
    actions: LoggerActions,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberNumberFormatter()
    val listState = rememberLazyListState()
    ScrollToEditedExercise(state, listState)
    LazyColumn(
        state = listState,
        modifier = modifier.readableWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (state.workout.exercises.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = LioraIcons.Exercise,
                    title = stringResource(Res.string.logger_empty_title),
                    body = stringResource(Res.string.logger_empty_body),
                )
            }
        }
        state.workout.exercises.forEachIndexed { index, exercise ->
            item(key = exercise.id) {
                ExerciseCard(
                    index = index,
                    state = state,
                    actions = actions.exercise,
                    setActions = actions.set,
                    onTextFocus = actions.pad.onTextFocus,
                    numbers = numbers,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        item(key = "footer") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = actions.navigation.onAddExercises, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(LioraIcons.Add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.logger_add_exercises))
                }
                TextButton(
                    onClick = { actions.onShowDialog(LoggerDialog.Discard) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(Res.string.logger_discard))
                }
            }
        }
    }
}

/** Brings the exercise being typed into on screen; the cell then scrolls itself above the pad. */
@Composable
private fun ScrollToEditedExercise(
    state: LoggerUiState.Active,
    listState: LazyListState,
) {
    val exerciseIndex =
        state.edit
            ?.cell
            ?.let { state.workout.find(it.setId) }
            ?.exerciseIndex
    LaunchedEffect(exerciseIndex) {
        if (exerciseIndex == null) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == exerciseIndex }
        if (!visible) listState.animateScrollToItem(exerciseIndex)
    }
}

@Composable
private fun WorkoutStats(
    state: LoggerUiState.Active,
    modifier: Modifier = Modifier,
) {
    val elapsed = rememberElapsedTime(state.workout.startedAt)
    val numbers = rememberNumberFormatter()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat(label = stringResource(Res.string.logger_stat_duration), value = elapsed.formatAsClock(), highlight = true)
        Stat(
            label = stringResource(Res.string.logger_stat_volume),
            value = "${numbers.format(state.volumeKg, maxFractionDigits = 0)} ${WeightUnit.Kilogram.symbol}",
        )
        Stat(label = stringResource(Res.string.logger_stat_sets), value = numbers.format(state.completedSets))
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

private val FocusPaneWidth = 360.dp

/** Test tags for the logger's value cells and number pad. */
object LoggerTags {
    const val CELL = "logger.cell"
    const val PAD = "logger.pad"
    const val TABLETOP = "logger.tabletop"
    const val FINISH_CONFIRM = "logger.finish.confirm"
}
