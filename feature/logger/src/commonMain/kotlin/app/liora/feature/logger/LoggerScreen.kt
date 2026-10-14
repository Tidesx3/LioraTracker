package app.liora.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraButton
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraOutlinedButton
import app.liora.core.designsystem.component.LioraScaffold
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.Stat
import app.liora.core.designsystem.component.StatMotion
import app.liora.core.designsystem.component.StatRow
import app.liora.core.designsystem.glass.LocalGlassBackdrop
import app.liora.core.designsystem.glass.aurora
import app.liora.core.designsystem.glass.glassSource
import app.liora.core.designsystem.glass.rememberGlassBackdrop
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.illustration.Illustration
import app.liora.core.designsystem.layout.currentWindowLayout
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.motion.rememberLioraHaptics
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.designsystem.util.rememberElapsedTime
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.designsystem.util.workoutDisplayName
import app.liora.core.domain.find
import app.liora.core.domain.setAt
import app.liora.core.ui.NoteKind
import app.liora.core.ui.currentLanguage
import app.liora.core.ui.volumeText
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.cd_minimize
import app.liora.feature.logger.resources.cd_rename
import app.liora.feature.logger.resources.edit_done
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
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** What the logger asks of the app: closing, the summary of a finished workout, and the exercise picker. */
internal class LoggerNavigationActions(
    val onClose: () -> Unit,
    /** The workout in progress is finished: the logger makes way for its summary. */
    val onFinished: (workoutId: String) -> Unit,
    val onAddExercises: () -> Unit,
    val onReplaceExercise: () -> Unit,
    val onOpenExercise: (exerciseId: String) -> Unit,
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
    val corrected by viewModel.corrected.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    CloseWhenDone(uiState, corrected, finished, viewModel.correctsHistory, navigation)
    LoggerHaptics(viewModel.tickOutcomes, uiState)

    var dialog by rememberSaveable { mutableStateOf<LoggerDialog?>(null) }
    val showDialog = { value: LoggerDialog ->
        // The gym is switched from the pad, which stays open to show the plates at the new gym.
        if (value != LoggerDialog.Gym) viewModel.closePad()
        dialog = value
    }
    val onDone: () -> Unit = {
        (uiState as? LoggerUiState.Active)?.let { active ->
            val question = askBeforeLeaving(active)
            if (question != null) showDialog(question) else viewModel.finishCorrections()
        }
    }

    // Back closes the pad before it leaves the logger, and leaves a finished workout like Done.
    val editing = (uiState as? LoggerUiState.Active)?.edit != null
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = editing || viewModel.correctsHistory,
        onBackCompleted = { if (editing) viewModel.closePad() else onDone() },
    )

    val state = uiState
    // A finished workout that's gone closes, and one just finished makes way for its summary; there is
    // nothing to show meanwhile.
    val leaving = viewModel.correctsHistory || finished != null
    if (state == LoggerUiState.Loading || (state == LoggerUiState.NoWorkout && leaving)) return
    LoggerContent(
        state = state as? LoggerUiState.Active,
        actions = loggerActions(viewModel, navigation, onShowDialog = showDialog, onDone = onDone),
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
                    onUseGym = viewModel::useGym,
                    corrections =
                        CorrectionActions(
                            onMoveTo = viewModel::moveTo,
                            onStartAt = viewModel::startAt,
                            onEndAt = viewModel::endAt,
                            onFinish = viewModel::finishCorrections,
                            onDelete = viewModel::deleteWorkout,
                        ),
                    onDismiss = { dialog = null },
                ),
        )
    }
}

/**
 * Closes the logger once the database confirms the workout ended, a finished one is gone, or its
 * corrections are saved; a workout finished here opens its summary instead, once the finish has landed.
 * Closing right away would clear the ViewModel and cancel the write before it lands.
 */
@Composable
private fun CloseWhenDone(
    uiState: LoggerUiState,
    corrected: Boolean,
    finished: Finishing?,
    correctsHistory: Boolean,
    navigation: LoggerNavigationActions,
) {
    var hadWorkout by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(false) }
    val currentNavigation by rememberUpdatedState(navigation)
    LaunchedEffect(uiState, corrected, finished) {
        if (uiState is LoggerUiState.Active) hadWorkout = true
        val gone = uiState == LoggerUiState.NoWorkout && (hadWorkout || correctsHistory)
        // Once only: the state can still change while the screen leaves, and a second close would go too far.
        if (closed) return@LaunchedEffect
        if (finished != null) {
            // The workout is gone as soon as the finish writes it; the summary waits for the whole write.
            if (finished.landed) {
                closed = true
                currentNavigation.onFinished(finished.workoutId)
            }
        } else if (corrected || gone) {
            closed = true
            currentNavigation.onClose()
        }
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
    /** Leaves a finished workout being corrected. */
    val onDone: () -> Unit,
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
    onDone: () -> Unit,
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
            onNoteChange = { kind, id, note ->
                if (kind == NoteKind.Exercise) viewModel.setExerciseNote(id, note) else viewModel.setNotes(id, note)
            },
            onAddSets = { id, warmups -> if (warmups) viewModel.addWarmups(id) else viewModel.addSet(id) },
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
    onDone = onDone,
)

@Composable
private fun LoggerContent(
    state: LoggerUiState.Active?,
    actions: LoggerActions,
    modifier: Modifier = Modifier,
) {
    LioraScaffold(
        modifier = modifier,
        topBar = {
            val finished = state?.isFinished == true
            LioraTopAppBar(
                title = workoutDisplayName(state?.workout?.name),
                navigationIcon = {
                    if (finished) {
                        BackButton(onClick = actions.onDone)
                    } else {
                        LioraIconButton(
                            LioraIcons.ExpandDown,
                            stringResource(Res.string.cd_minimize),
                            actions.navigation.onClose,
                        )
                    }
                },
                actions = {
                    if (state != null) {
                        LioraIconButton(
                            LioraIcons.Edit,
                            stringResource(Res.string.cd_rename),
                            { actions.onShowDialog(LoggerDialog.Rename) },
                        )
                    }
                },
                primaryAction = {
                    if (state != null) {
                        LioraButton(
                            onClick = { if (finished) actions.onDone() else actions.onShowDialog(LoggerDialog.Finish) },
                            modifier = Modifier.padding(end = 8.dp).testTag(LoggerTags.FINISH),
                        ) {
                            Text(stringResource(if (finished) Res.string.edit_done else Res.string.logger_finish))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state == null) {
            EmptyState(
                illustration = Illustration.Workout,
                title = stringResource(Res.string.logger_no_workout_title),
                body = stringResource(Res.string.logger_no_workout_body),
                modifier = Modifier.padding(padding),
            )
            return@LioraScaffold
        }
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            state.endedAt?.let { endedAt ->
                WorkoutTimeChips(
                    startedAt = state.workout.startedAt,
                    endedAt = endedAt,
                    onShowDialog = actions.onShowDialog,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            WorkoutStats(state, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val list = rememberWorkoutListPlace(state)
            val layout = currentWindowLayout()
            when {
                layout.tabletop -> {
                    TabletopLogger(state, actions, list, Modifier.weight(1f))
                }

                layout.twoPane -> {
                    Row(Modifier.weight(1f)) {
                        WorkoutList(state, actions, list, Modifier.weight(1f).fillMaxHeight())
                        VerticalDivider()
                        FocusPane(state, actions, Modifier.width(FocusPaneWidth).fillMaxHeight())
                    }
                }

                else -> {
                    CompactLogger(state, actions, list, Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Phones and the Fold's cover screen: the list, with the rest timer and the pad docked below it. With
 * Liquid Glass the rest timer floats over the end of the list instead, which scrolls on beneath it.
 */
@Composable
private fun CompactLogger(
    state: LoggerUiState.Active,
    actions: LoggerActions,
    list: WorkoutListPlace,
    modifier: Modifier = Modifier,
) {
    val glass = LioraTheme.glass
    Column(modifier) {
        val rest = rememberRestProgress(state.restTimer)
        if (glass.enabled) {
            val backdrop = rememberGlassBackdrop()
            var restHeight by remember { mutableIntStateOf(0) }
            Box(Modifier.weight(1f)) {
                // The aurora again, under the list: the glass bends both, as the screen shows them.
                Box(Modifier.fillMaxSize().glassSource(backdrop).aurora(glass)) {
                    WorkoutList(
                        state,
                        actions,
                        list,
                        Modifier.fillMaxSize(),
                        endPadding = if (rest != null) with(LocalDensity.current) { restHeight.toDp() } else 0.dp,
                    )
                }
                rest?.let {
                    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                        RestTimerBar(
                            it,
                            onAdjust = actions.rest.onAdjust,
                            onSkip = actions.rest.onSkip,
                            modifier =
                                Modifier.align(Alignment.BottomCenter).onSizeChanged { size ->
                                    restHeight =
                                        size.height
                                },
                        )
                    }
                }
            }
        } else {
            WorkoutList(state, actions, list, Modifier.weight(1f))
            rest?.let { RestTimerBar(it, onAdjust = actions.rest.onAdjust, onSkip = actions.rest.onSkip) }
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
    val fields = trackingType?.let(state::fieldsOf).orEmpty()
    val field = cell?.field ?: fields.firstOrNull()
    // Clear focus from a note being typed, so the system keyboard and the pad never both show.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(cell) { if (cell != null) focusManager.clearFocus() }
    Column(modifier) {
        val exerciseId = ref?.let { state.workout.exercises[it.exerciseIndex].exerciseId }
        PadInfo(
            plates = ref?.let(state::platesFor),
            takesPlates = exerciseId != null && state.takesPlates(exerciseId),
            gym = state.gym,
            canSwitchGym = state.gyms.size > 1,
            onSwitchGym = { actions.onShowDialog(LoggerDialog.Gym) },
        )
        NumberPad(
            decimals = trackingType != null && field?.inputKind(trackingType)?.takesDecimals == true,
            nextLogsSet = field != null && field == fields.lastOrNull(),
            onKey = actions.pad.onKey,
            canHide = canHide,
        )
    }
}

@Composable
internal fun WorkoutList(
    state: LoggerUiState.Active,
    actions: LoggerActions,
    place: WorkoutListPlace,
    modifier: Modifier = Modifier,
    /** Room at the end for what floats over it: the rest timer, with Liquid Glass. */
    endPadding: Dp = 0.dp,
) {
    val numbers = rememberNumberFormatter()
    val listState = place.listState
    ScrollToEditedExercise(state, listState)
    LazyColumn(
        state = listState,
        modifier = modifier.readableWidth(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp + endPadding),
    ) {
        if (state.workout.exercises.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    illustration = Illustration.Workout,
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
                    onOpenExercise = actions.navigation.onOpenExercise,
                    numbers = numbers,
                    place = place,
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
                LioraOutlinedButton(onClick = actions.navigation.onAddExercises, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(LioraIcons.Add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.logger_add_exercises))
                }
                // A finished workout is deleted from history, not discarded.
                if (!state.isFinished) {
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
    // A finished workout's clock has stopped.
    val duration = state.endedAt?.let { it - state.workout.startedAt } ?: rememberElapsedTime(state.workout.startedAt)
    val numbers = rememberNumberFormatter()
    // Each figure rolls as it changes: the clock every second, volume and sets with each set logged.
    StatRow(modifier) {
        Stat(
            value = duration.formatAsClock(),
            label = stringResource(Res.string.logger_stat_duration),
            modifier = Modifier.weight(1f),
            highlight = !state.isFinished,
            motion = StatMotion.Roll,
        )
        Stat(
            value = volumeText(state.volumeKg),
            label = stringResource(Res.string.logger_stat_volume),
            modifier = Modifier.weight(1f),
            motion = StatMotion.Roll,
        )
        Stat(
            value = numbers.format(state.completedSets),
            label = stringResource(Res.string.logger_stat_sets),
            modifier = Modifier.weight(1f),
            motion = StatMotion.Roll,
        )
    }
}

/**
 * Answers ticking a set off with a haptic once it's known how it went: a tick when it's logged, a
 * buzz when a value is missing, and a double beat when it sets a personal record.
 */
@Composable
private fun LoggerHaptics(
    outcomes: Flow<TickOutcome>,
    state: LoggerUiState,
) {
    val haptics = rememberLioraHaptics()
    LaunchedEffect(outcomes, haptics) {
        outcomes.collect { outcome ->
            when (outcome) {
                TickOutcome.Logged -> haptics.setLogged()
                TickOutcome.Missing -> haptics.rejected()
            }
        }
    }
    // Records show in the state a moment after their set is logged; those already there when the
    // logger opens don't count.
    val records = (state as? LoggerUiState.Active)?.newRecords?.keys
    var known by remember { mutableStateOf<Set<String>?>(null) }
    var lastRecord by remember { mutableStateOf<TimeMark?>(null) }
    LaunchedEffect(records) {
        if (records == null) return@LaunchedEffect
        val before = known
        known = records
        if (before != null && !before.containsAll(records)) {
            haptics.record()
            lastRecord = TimeSource.Monotonic.markNow()
        }
    }
    // Steps earned the same way. They're worked out a moment after their set's record, and a record at the same
    // tick is the bigger moment, so it has the say.
    val steps = (state as? LoggerUiState.Active)?.earned?.steps?.keys
    var knownSteps by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(steps) {
        if (steps == null) return@LaunchedEffect
        val before = knownSteps
        knownSteps = steps
        val recordJustNow = lastRecord?.let { it.elapsedNow() < SAME_TICK } == true
        if (before != null && !before.containsAll(steps) && !recordJustNow) haptics.stepEarned()
    }
}

/** How close a step comes after a record to belong to the same tick. */
private val SAME_TICK = 1.seconds

private val FocusPaneWidth = 360.dp

/** Test tags for the logger's value cells, number pad and buttons. */
object LoggerTags {
    const val CELL = "logger.cell"
    const val PAD = "logger.pad"

    /** The time left in the rest pill. */
    const val REST_TIME = "logger.rest.time"

    /** The plates per side above the pad. */
    const val PLATES = "logger.plates"
    const val TABLETOP = "logger.tabletop"

    /** Finish, or Done on a finished workout. */
    const val FINISH = "logger.finish"
    const val FINISH_CONFIRM = "logger.finish.confirm"
    const val DATE = "logger.date"

    /** Confirms leaving a finished workout: drop the sets not logged, or delete it. */
    const val CORRECTIONS_CONFIRM = "logger.corrections.confirm"
}
