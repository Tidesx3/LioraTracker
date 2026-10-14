package app.liora.feature.logger.done

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.LioraBottomBar
import app.liora.core.designsystem.component.LioraButton
import app.liora.core.designsystem.component.LioraCard
import app.liora.core.designsystem.component.LioraCardDefaults
import app.liora.core.designsystem.component.LioraScaffold
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.component.Stat
import app.liora.core.designsystem.component.StatMotion
import app.liora.core.designsystem.component.StatRow
import app.liora.core.designsystem.glass.GlassDefaults
import app.liora.core.designsystem.glass.LocalGlassBackdrop
import app.liora.core.designsystem.glass.glass
import app.liora.core.designsystem.glass.glassPane
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.illustration.Illustration
import app.liora.core.designsystem.illustration.LioraIllustration
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.designsystem.util.workoutDisplayName
import app.liora.core.domain.Earned
import app.liora.core.model.LoggedSet
import app.liora.core.model.RepRange
import app.liora.core.ui.ExerciseThumbnail
import app.liora.core.ui.RecordsCard
import app.liora.core.ui.WeekCard
import app.liora.core.ui.currentLanguage
import app.liora.core.ui.durationLabel
import app.liora.core.ui.setSummary
import app.liora.core.ui.volumeText
import app.liora.core.ui.weightText
import app.liora.feature.logger.RaceChips
import app.liora.feature.logger.StepBead
import app.liora.feature.logger.glassShine
import app.liora.feature.logger.rememberShine
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.cd_personal_record
import app.liora.feature.logger.resources.done_best_sets
import app.liora.feature.logger.resources.done_close
import app.liora.feature.logger.resources.done_count
import app.liora.feature.logger.resources.done_date_time
import app.liora.feature.logger.resources.done_first
import app.liora.feature.logger.resources.done_maxed_out
import app.liora.feature.logger.resources.done_ready_alone
import app.liora.feature.logger.resources.done_ready_also
import app.liora.feature.logger.resources.done_ready_title
import app.liora.feature.logger.resources.done_sets
import app.liora.feature.logger.resources.done_steps
import app.liora.feature.logger.resources.done_title
import app.liora.feature.logger.resources.finish_records
import app.liora.feature.logger.resources.logger_stat_duration
import app.liora.feature.logger.resources.logger_stat_sets
import app.liora.feature.logger.resources.logger_stat_volume
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Right after a workout ends: a medal and how many workouts are in the books now, then what this one
 * came to, the records it set (with the burst), and each exercise's best set. Done goes back to where the
 * workout was started from.
 */
@Composable
internal fun WorkoutDoneScreen(
    viewModel: WorkoutDoneViewModel,
    onClose: () -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = currentLanguage()
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val firstDayOfWeek = rememberDateFormatter().firstDayOfWeek
    LaunchedEffect(firstDayOfWeek) { viewModel.setFirstDayOfWeek(firstDayOfWeek) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Deleted meanwhile, on another device say: there is nothing left to sum up.
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(uiState) { if (uiState == WorkoutDoneUiState.Gone) currentOnClose() }

    val loaded = uiState as? WorkoutDoneUiState.Loaded ?: return
    WorkoutDoneContent(loaded, onClose, onOpenExercise, modifier)
}

@Composable
private fun WorkoutDoneContent(
    state: WorkoutDoneUiState.Loaded,
    onClose: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LioraScaffold(
        modifier = modifier,
        bottomBar = {
            LioraBottomBar {
                LioraButton(
                    onClick = onClose,
                    modifier =
                        Modifier
                            .navigationBarsPadding()
                            .readableWidth()
                            .padding(16.dp)
                            .height(52.dp)
                            .testTag(WorkoutDoneTags.CLOSE),
                ) {
                    Text(stringResource(Res.string.done_close))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.readableWidth(),
            contentPadding =
                PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item(key = "hero") { Hero(state.number, Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
            item(key = "summary") {
                Summary(state, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                // In the same item, so the week arriving a moment later doesn't push the list about.
                state.week?.takeUnless { it.isBlank }?.let { week ->
                    WeekCard(
                        week,
                        afterWorkout = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (state.steps.isNotEmpty() || state.readyForMore.isNotEmpty()) {
                item(key = "steps") {
                    StepsCard(state.steps, state.readyForMore, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            if (state.recordEntries.isNotEmpty()) {
                item(key = "records") {
                    RecordsCard(
                        title = pluralStringResource(Res.plurals.finish_records, state.recordCount, state.recordCount),
                        entries = state.recordEntries,
                        celebrate = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item(key = "best-sets") {
                Column {
                    SectionHeader(stringResource(Res.string.done_best_sets))
                    BestSets(state.highlights, onOpenExercise, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
    }
}

/** The medal, and the count it's for: the first workout gets a line of its own. */
@Composable
private fun Hero(
    number: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LioraIllustration(Illustration.Medal, Modifier.width(220.dp))
        Text(
            text = stringResource(Res.string.done_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text =
                if (number <= 1) {
                    stringResource(Res.string.done_first)
                } else {
                    pluralStringResource(Res.plurals.done_count, number, number)
                },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The workout's name and start, and its totals rolling in. */
@Composable
private fun Summary(
    state: WorkoutDoneUiState.Loaded,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    val numbers = rememberNumberFormatter()
    val workout = state.workout
    val start = workout.startedAt.toLocalDateTime(TimeZone.currentSystemDefault())
    LioraCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(workoutDisplayName(workout.name), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(Res.string.done_date_time, dates.longDate(start.date), dates.time(start.time)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatRow(Modifier.padding(top = 12.dp)) {
                Stat(
                    value = durationLabel(workout.duration),
                    label = stringResource(Res.string.logger_stat_duration),
                    modifier = Modifier.weight(1f),
                    motion = StatMotion.RollIn,
                )
                // Bodyweight and cardio days move no counted load: a dash, as "0 kg" would look like a mistake.
                Stat(
                    value = if (state.volumeKg > 0.0) volumeText(state.volumeKg) else NONE,
                    label = stringResource(Res.string.logger_stat_volume),
                    modifier = Modifier.weight(1f),
                    motion = StatMotion.RollIn,
                )
                Stat(
                    value = numbers.format(state.sets),
                    label = stringResource(Res.string.logger_stat_sets),
                    modifier = Modifier.weight(1f),
                    motion = StatMotion.RollIn,
                )
                // The records are counted in their card's title, right below.
            }
            RaceChips(state.race, Modifier.padding(top = 12.dp))
        }
    }
}

/**
 * The steps up the workout earned, each from its weight to the next in a pill of the accent's glass, and,
 * quieter, the exercises without progression that reached the top of every range. A violet pane, as the records'
 * card is gold: its arrow pops in on a bead of glass and light sweeps across it once.
 */
@Composable
private fun StepsCard(
    steps: List<StepEntry>,
    readyForMore: List<String>,
    modifier: Modifier = Modifier,
) {
    val glass = LioraTheme.glass
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val shine = rememberShine(celebrate = steps.isNotEmpty())
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (glass.enabled) {
                        Modifier.glassPane(shape, fill = glass.accentControl, rim = glass.cardRim)
                    } else {
                        Modifier.background(colors.primary.copy(alpha = STEPS_TINT), shape)
                    },
                ).glassShine({ shine.value }, shape)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepBead(onViolet = false, burst = false, pop = steps.isNotEmpty())
            Text(
                text =
                    if (steps.isNotEmpty()) {
                        pluralStringResource(Res.plurals.done_steps, steps.size, steps.size)
                    } else {
                        stringResource(Res.string.done_ready_title)
                    },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        steps.forEach { StepRow(it) }
        if (readyForMore.isNotEmpty()) {
            val names = readyForMore.joinToString(", ")
            Text(
                text =
                    if (steps.isNotEmpty()) {
                        stringResource(Res.string.done_ready_also, names)
                    } else {
                        stringResource(Res.string.done_ready_alone, names)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** An exercise's step: its name, and the weight lifted leading to the next, or how far the gym goes. */
@Composable
private fun StepRow(step: StepEntry) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = step.exerciseName,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 4.dp),
        )
        when (val earned = step.earned) {
            is Earned.StepUp -> {
                Text(
                    text = weightText(earned.from),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = colors.onSurfaceVariant,
                )
                Icon(
                    painterResource(LioraIcons.Next),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp),
                )
                WeightPill(weightText(earned.to))
            }

            is Earned.TopOfTheStack -> {
                WeightPill(stringResource(Res.string.done_maxed_out, weightText(earned.weight)))
            }
        }
    }
}

/** The weight a step goes to, on a pill of the accent's glass like the app's main buttons. */
@Composable
private fun WeightPill(text: String) {
    val glass = LioraTheme.glass
    val colors = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.tabularNumbers(),
        color = if (glass.enabled) glass.onAccent else colors.onPrimary,
        modifier =
            Modifier
                .then(
                    if (glass.enabled) {
                        Modifier.glass(LocalGlassBackdrop.current, CircleShape, GlassDefaults.accent())
                    } else {
                        Modifier.background(colors.primary, CircleShape)
                    },
                ).padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** A row per exercise: its photo, name and sets, and its best set, with a trophy if it set a record. */
@Composable
private fun BestSets(
    highlights: List<ExerciseHighlight>,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LioraCard(modifier.fillMaxWidth()) {
        highlights.forEach { highlight ->
            ListItem(
                headlineContent = { Text(highlight.exercise?.name.orEmpty()) },
                supportingContent = {
                    Text(pluralStringResource(Res.plurals.done_sets, highlight.sets, highlight.sets))
                },
                leadingContent = highlight.exercise?.let { exercise -> { ExerciseThumbnail(exercise, size = 40.dp) } },
                trailingContent = highlight.best?.let { best -> { BestSet(highlight, best) } },
                colors = LioraCardDefaults.listItemColors(),
                modifier =
                    Modifier.clickable(enabled = highlight.exercise != null) {
                        onOpenExercise(highlight.done.exerciseId)
                    },
            )
        }
    }
}

@Composable
private fun BestSet(
    highlight: ExerciseHighlight,
    best: LoggedSet,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (highlight.bestIsRecord) {
            Icon(
                painterResource(LioraIcons.Record),
                contentDescription = stringResource(Res.string.cd_personal_record),
                tint = LioraTheme.colors.personalRecord,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text =
                setSummary(
                    highlight.trackingType,
                    best.weight,
                    best.reps?.let(::RepRange),
                    best.duration,
                    best.distanceMeters,
                ),
            style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val NONE = "–"

/** As much violet as the records' card has gold. */
private const val STEPS_TINT = 0.12f

object WorkoutDoneTags {
    const val CLOSE = "done.close"
}
