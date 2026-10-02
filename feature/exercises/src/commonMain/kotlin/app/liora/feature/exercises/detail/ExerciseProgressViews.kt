package app.liora.feature.exercises.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.ChartPoint
import app.liora.core.designsystem.component.LineChart
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.designsystem.util.workoutDisplayName
import app.liora.core.domain.ExerciseProgress
import app.liora.core.domain.PersonalRecord
import app.liora.core.domain.ProgressMetric
import app.liora.core.domain.ProgressPoint
import app.liora.core.model.ExerciseSession
import app.liora.core.model.RepRange
import app.liora.core.model.TrackingType
import app.liora.core.ui.LocalUnits
import app.liora.core.ui.axisText
import app.liora.core.ui.metricLabel
import app.liora.core.ui.recordLabel
import app.liora.core.ui.setSummary
import app.liora.core.ui.toShown
import app.liora.core.ui.unit
import app.liora.core.ui.valueText
import app.liora.feature.exercises.resources.Res
import app.liora.feature.exercises.resources.detail_best
import app.liora.feature.exercises.resources.detail_latest
import app.liora.feature.exercises.resources.detail_one_session
import app.liora.feature.exercises.resources.detail_rep_max
import app.liora.feature.exercises.resources.detail_rep_maxes
import app.liora.feature.exercises.resources.detail_show_all
import app.liora.feature.exercises.resources.detail_stalled
import app.liora.feature.exercises.resources.detail_stalled_body
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** "Stalled for 4 weeks", in the tertiary color so it stands out without alarming. */
@Composable
internal fun StallBadge(
    weeks: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag(ExerciseDetailTags.STALLED),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = pluralStringResource(Res.plurals.detail_stalled, weeks, weeks),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** One metric over the sessions: chips to pick it, the best and latest values, and the chart. */
@Composable
internal fun ProgressChart(
    state: ExerciseProgressState,
    onSelectMetric: (ProgressMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = state.metric.unit
    val trackingType = state.trackingType
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.metrics.size > 1) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.metrics.forEach { metric ->
                    FilterChip(
                        selected = metric == state.metric,
                        onClick = { onSelectMetric(metric) },
                        label = { Text(metricLabel(metric)) },
                    )
                }
            }
        }
        val best = state.best
        val latest = state.points.lastOrNull()
        if (best != null && latest != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                PointStat(stringResource(Res.string.detail_best), best, state.metric, trackingType)
                PointStat(stringResource(Res.string.detail_latest), latest, state.metric, trackingType)
            }
        }
        state.stall?.let { stall ->
            Text(
                text =
                    stringResource(
                        Res.string.detail_stalled_body,
                        rememberDateFormatter().shortDate(stall.best.at.localDate()),
                        valueText(stall.metric.unit, stall.best.value, trackingType),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val units = LocalUnits.current
        val points =
            remember(state.points, state.metric, units, trackingType) {
                chartPoints(
                    state.points,
                    state.metric,
                ).map { it.copy(value = unit.toShown(it.value, units, trackingType)) }
            }
        if (points.size < 2) {
            Text(
                text = stringResource(Res.string.detail_one_session),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val numbers = rememberNumberFormatter()
            LineChart(
                points = points,
                formatValue = remember(numbers, unit, trackingType) { { axisText(unit, it, numbers, trackingType) } },
                modifier = Modifier.fillMaxWidth().height(ChartHeight).testTag(ExerciseDetailTags.CHART),
            )
        }
    }
}

@Composable
private fun PointStat(
    label: String,
    point: ProgressPoint,
    metric: ProgressMetric,
    trackingType: TrackingType,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = valueText(metric.unit, point.value, trackingType),
            style = MaterialTheme.typography.titleLarge.tabularNumbers(),
        )
        Text(
            text = rememberDateFormatter().shortDate(point.at.localDate()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The exercise's records, each with its value and the day it was set; rep maxes as a compact row. */
@Composable
internal fun RecordList(
    state: ExerciseProgressState,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.records.forEach { record ->
            Row {
                Text(
                    recordLabel(record.key),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = valueText(record.key.type.unit, record.value, state.trackingType),
                        style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
                    )
                    Text(
                        text = dates.shortDate(record.achievedAt.localDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.repMaxes.isNotEmpty()) RepMaxes(state.repMaxes, state.trackingType)
    }
}

@Composable
private fun RepMaxes(
    repMaxes: List<PersonalRecord>,
    trackingType: TrackingType,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberNumberFormatter()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(Res.string.detail_rep_maxes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repMaxes.forEach { record ->
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        text =
                            stringResource(
                                Res.string.detail_rep_max,
                                numbers.format(record.key.reps ?: 1),
                                valueText(record.key.type.unit, record.value, trackingType),
                            ),
                        style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** Past sessions, newest first; a long history folds after the most recent ones. */
@Composable
internal fun SessionList(
    state: ExerciseProgressState,
    onOpenWorkout: (workoutId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) state.sessions else state.sessions.take(RECENT_SESSIONS)
    Column(modifier) {
        shown.forEach { session ->
            SessionRow(session, state.trackingType, onClick = { onOpenWorkout(session.workoutId) })
        }
        if (!showAll && state.sessions.size > RECENT_SESSIONS) {
            TextButton(onClick = { showAll = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(pluralStringResource(Res.plurals.detail_show_all, state.sessions.size, state.sessions.size))
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: ExerciseSession,
    trackingType: TrackingType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The work done; warm-ups only when nothing else was logged.
    val sets = session.sets.filter { it.isWorkingSet }.ifEmpty { session.sets }
    val summary =
        sets
            .map { set ->
                setSummary(trackingType, set.weight, set.reps?.let(::RepRange), set.duration, set.distanceMeters)
            }.joinToString(" · ")
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = workoutDisplayName(session.workoutName),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = rememberDateFormatter().longDate(session.startedAt.localDate()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(summary, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Points by day for the chart, which spaces them by date: two sessions on one day become one point,
 * the better of the two.
 */
private fun chartPoints(
    points: List<ProgressPoint>,
    metric: ProgressMetric,
): List<ChartPoint> =
    points
        .groupBy { it.at.localDate() }
        .map { (day, onDay) ->
            val best =
                onDay.reduce {
                    best,
                    point,
                    ->
                    if (ExerciseProgress.improves(metric, point.value, best.value)) point else best
                }
            ChartPoint(day, best.value)
        }

private fun Instant.localDate(): LocalDate = toLocalDateTime(TimeZone.currentSystemDefault()).date

private const val RECENT_SESSIONS = 10
private val ChartHeight = 200.dp

/** Test tags for the exercise page's progress parts. */
object ExerciseDetailTags {
    const val CHART = "exercise.chart"
    const val STALLED = "exercise.stalled"
}
