package app.liora.feature.progress.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.DateFormatter
import app.liora.core.designsystem.util.NumberFormatter
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.domain.MonthlyReport
import app.liora.core.domain.TrainingCalendar
import app.liora.core.model.WeightUnit
import app.liora.core.ui.BodyHeatmap
import app.liora.core.ui.headlineRecords
import app.liora.core.ui.label
import app.liora.core.ui.recordLabel
import app.liora.feature.progress.resources.Res
import app.liora.feature.progress.resources.progress_sets_count
import app.liora.feature.progress.resources.report_exercise_line
import app.liora.feature.progress.resources.report_muscle_legend
import app.liora.feature.progress.resources.report_record_line
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The month as a calendar, training days filled in; no wider than a phone's, so days stay small. */
@Composable
internal fun MonthDays(
    report: MonthlyReport,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    val numbers = rememberNumberFormatter()
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        MonthGrid(report, dates, numbers)
    }
}

@Composable
private fun MonthGrid(
    report: MonthlyReport,
    dates: DateFormatter,
    numbers: NumberFormatter,
) {
    Column(
        Modifier.widthIn(max = CalendarMaxWidth).testTag(ReportTags.CALENDAR),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row {
            TrainingCalendar.weekdays(dates.firstDayOfWeek).forEach { day ->
                Text(
                    text = dates.weekdayInitial(day),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        TrainingCalendar.weeks(report.month, dates.firstDayOfWeek).forEach { week ->
            Row {
                week.forEach { day ->
                    Box(Modifier.weight(1f).padding(2.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val trained = day in report.trainingDays
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .then(
                                        if (trained) {
                                            Modifier.background(
                                                MaterialTheme.colorScheme.primary,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = numbers.format(day.day),
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                        if (trained) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The records set this month, an exercise a line, counted and named like a workout's. */
@Composable
internal fun RecordLines(
    state: MonthlyReportUiState.Loaded,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.report.records.forEach { (exerciseId, keys) ->
            Text(
                text =
                    stringResource(
                        Res.string.report_record_line,
                        state.nameOf(exerciseId),
                        headlineRecords(keys).map { recordLabel(it) }.joinToString(", "),
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The exercises with the most working sets this month. */
@Composable
internal fun TopExercises(
    state: MonthlyReportUiState.Loaded,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberNumberFormatter()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.report.topExercises.forEach { share ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.nameOf(share.exerciseId),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                val sets = pluralStringResource(Res.plurals.progress_sets_count, share.sets, numbers.format(share.sets))
                Text(
                    text =
                        if (share.volumeKg > 0.0) {
                            stringResource(
                                Res.string.report_exercise_line,
                                sets,
                                "${numbers.format(
                                    share.volumeKg,
                                    maxFractionDigits = 0,
                                )} ${WeightUnit.Kilogram.symbol}",
                            )
                        } else {
                            sets
                        },
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The muscles the month worked, shaded by its average week so the colors mean what they do on the
 * Progress tab, then each muscle's sets over the whole month.
 */
@Composable
internal fun MonthMuscles(
    report: MonthlyReport,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberNumberFormatter()
    val weeks = report.month.numberOfDays / DAYS_PER_WEEK
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            BodyHeatmap(
                report.setsPerMuscle.mapValues { it.value / weeks },
                Modifier.widthIn(max = CalendarMaxWidth).fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
        Text(
            text = stringResource(Res.string.report_muscle_legend),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        report.setsPerMuscle.entries.sortedByDescending { it.value }.forEach { (muscle, sets) ->
            Row {
                Text(
                    stringResource(muscle.label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
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
                )
            }
        }
    }
}

/** Test tags for the monthly report. */
object ReportTags {
    const val CALENDAR = "report.calendar"
}

private const val DAYS_PER_WEEK = 7.0

// About a phone's width: day circles and the body figure stay at a size that reads at a glance.
private val CalendarMaxWidth = 360.dp
