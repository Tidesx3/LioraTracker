package app.liora.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.domain.TrainingCalendar
import app.liora.core.model.Muscle
import app.liora.core.ui.BodyHeatmap
import app.liora.core.ui.MuscleLoad
import app.liora.core.ui.label
import app.liora.core.ui.metricLabel
import app.liora.core.ui.muscleLoadColor
import app.liora.core.ui.unit
import app.liora.core.ui.valueText
import app.liora.feature.progress.resources.Res
import app.liora.feature.progress.resources.cd_next_week
import app.liora.feature.progress.resources.cd_previous_week
import app.liora.feature.progress.resources.progress_date_range
import app.liora.feature.progress.resources.progress_last_week
import app.liora.feature.progress.resources.progress_legend
import app.liora.feature.progress.resources.progress_legend_full
import app.liora.feature.progress.resources.progress_legend_light
import app.liora.feature.progress.resources.progress_legend_moderate
import app.liora.feature.progress.resources.progress_legend_none
import app.liora.feature.progress.resources.progress_metric_date
import app.liora.feature.progress.resources.progress_no_sets
import app.liora.feature.progress.resources.progress_sets_count
import app.liora.feature.progress.resources.progress_show_all
import app.liora.feature.progress.resources.progress_stalled
import app.liora.feature.progress.resources.progress_this_week
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The last [WEEKS] weeks, a column each, a square per day: filled where there was training. Reads like
 * a habit tracker: gaps stand out at once.
 */
@Composable
internal fun ConsistencyGrid(
    trainingDays: Set<LocalDate>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    val weeks = TrainingCalendar.recentWeeks(today, dates.firstDayOfWeek, WEEKS)
    val trained = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier.testTag(ProgressTags.CONSISTENCY), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
            weeks.forEach { week ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                    week.forEach { day ->
                        val shape = RoundedCornerShape(3.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(shape)
                                .then(
                                    when {
                                        day > today -> Modifier
                                        day in trainingDays -> Modifier.background(trained)
                                        else -> Modifier.background(rest)
                                    },
                                ).then(
                                    if (day == today) {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }
        Text(
            text =
                stringResource(
                    Res.string.progress_date_range,
                    dates.shortDate(weeks.first().first()),
                    dates.shortDate(today),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Sets per muscle in one week: paging between weeks, the body shaded by load, and the numbers. */
@Composable
internal fun MuscleWeek(
    state: ProgressUiState.Loaded,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WeekSwitcher(state, onPreviousWeek, onNextWeek)
        BodyHeatmap(state.setsPerMuscle, Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        Legend()
        val worked =
            state.setsPerMuscle
                .filterValues { it > 0.0 }
                .entries
                .sortedByDescending { it.value }
        if (worked.isEmpty()) {
            Text(
                text = stringResource(Res.string.progress_no_sets),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        worked.forEach { (muscle, sets) -> MuscleRow(muscle, sets, worked.first().value) }
    }
}

@Composable
private fun WeekSwitcher(
    state: ProgressUiState.Loaded,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    val lastWeek = TrainingCalendar.weekStart(state.today, dates.firstDayOfWeek).plus(-DAYS_PER_WEEK, DateTimeUnit.DAY)
    val label =
        when {
            state.isCurrentWeek -> {
                stringResource(Res.string.progress_this_week)
            }

            state.muscleWeek == lastWeek -> {
                stringResource(Res.string.progress_last_week)
            }

            else -> {
                stringResource(
                    Res.string.progress_date_range,
                    dates.shortDate(state.muscleWeek),
                    dates.shortDate(state.muscleWeek.plus(DAYS_PER_WEEK - 1, DateTimeUnit.DAY)),
                )
            }
        }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Arrows only where there is somewhere to go: back to training, forward up to this week.
        Box(Modifier.size(ArrowSize)) {
            if (state.hasEarlierWeeks) {
                LioraIconButton(LioraIcons.ChevronLeft, stringResource(Res.string.cd_previous_week), onPreviousWeek)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(ArrowSize)) {
            if (!state.isCurrentWeek) {
                LioraIconButton(LioraIcons.ChevronRight, stringResource(Res.string.cd_next_week), onNextWeek)
            }
        }
    }
}

/** What the shades mean, in sets a week. */
@Composable
private fun Legend(modifier: Modifier = Modifier) {
    val labels =
        mapOf(
            MuscleLoad.None to Res.string.progress_legend_none,
            MuscleLoad.Light to Res.string.progress_legend_light,
            MuscleLoad.Moderate to Res.string.progress_legend_moderate,
            MuscleLoad.Full to Res.string.progress_legend_full,
        )
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.progress_legend),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        labels.forEach { (load, label) ->
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(muscleLoadColor(load)))
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp, end = 10.dp),
            )
        }
    }
}

/** A muscle, a bar as long as its share of the busiest one, and its sets. */
@Composable
private fun MuscleRow(
    muscle: Muscle,
    sets: Double,
    most: Double,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberNumberFormatter()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(muscle.label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(MuscleNameWidth),
        )
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))) {
            Box(
                Modifier
                    .fillMaxWidth((sets / most).toFloat().coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(muscleLoadColor(MuscleLoad.of(sets))),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            // Half sets come from secondary muscles; 1.5 sets is "other" in both languages.
            text =
                pluralStringResource(
                    Res.plurals.progress_sets_count,
                    if (sets ==
                        1.0
                    ) {
                        1
                    } else {
                        2
                    },
                    numbers.format(sets, 1),
                ),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            textAlign = TextAlign.End,
            modifier = Modifier.width(SetsWidth),
        )
    }
}

/** Each trained exercise's standing best, newest first; stalls flagged; the rest after "show all". */
@Composable
internal fun RecordsBoard(
    rows: List<BoardRow>,
    selectedExerciseId: String?,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        (if (showAll) rows else rows.take(BOARD_ROWS)).forEach { row ->
            BoardRowItem(
                row = row,
                selected = row.entry.exerciseId == selectedExerciseId,
                onClick = { onOpenExercise(row.entry.exerciseId) },
            )
        }
        if (!showAll && rows.size > BOARD_ROWS) {
            TextButton(onClick = { showAll = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(pluralStringResource(Res.plurals.progress_show_all, rows.size, rows.size))
            }
        }
    }
}

@Composable
private fun BoardRowItem(
    row: BoardRow,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = row.entry
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { if (selected) this.selected = true }
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                ).clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text =
                    stringResource(
                        Res.string.progress_metric_date,
                        metricLabel(entry.metric),
                        rememberDateFormatter().shortDate(entry.best.at.localDate()),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.stallWeeks?.let { StalledTag(it) }
        }
        Text(
            text = valueText(entry.metric.unit, entry.best.value, row.trackingType),
            style = MaterialTheme.typography.titleMedium.tabularNumbers(),
        )
    }
}

@Composable
private fun StalledTag(
    weeks: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(top = 2.dp),
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = pluralStringResource(Res.plurals.progress_stalled, weeks, weeks),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun kotlin.time.Instant.localDate(): LocalDate = toLocalDateTime(TimeZone.currentSystemDefault()).date

/** Test tags for the Progress tab. */
object ProgressTags {
    const val CONSISTENCY = "progress.consistency"
}

private const val WEEKS = 16
private const val DAYS_PER_WEEK = 7
private const val BOARD_ROWS = 8
private val CELL_GAP = 3.dp
private val ArrowSize = 48.dp
private val MuscleNameWidth = 112.dp
private val SetsWidth = 72.dp
