package app.liora.feature.body

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.liora.core.designsystem.component.ChartPoint
import app.liora.core.designsystem.component.LineChart
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.domain.DayValue
import app.liora.core.domain.MeasurementChange
import app.liora.core.model.MeasurementType
import app.liora.core.ui.measurementAxisText
import app.liora.core.ui.measurementChangeText
import app.liora.feature.body.resources.Res
import app.liora.feature.body.resources.body_change_since
import app.liora.feature.body.resources.dialog_cancel
import app.liora.feature.body.resources.dialog_ok
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** A measurement day by day. Callers show it from two days on; one day makes no line. */
@Composable
internal fun MeasurementChart(
    type: MeasurementType,
    days: List<DayValue>,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberNumberFormatter()
    LineChart(
        points = remember(days) { days.map { ChartPoint(it.day, it.value) } },
        formatValue = remember(numbers, type) { { measurementAxisText(type, it, numbers) } },
        modifier = modifier.testTag(BodyTags.CHART),
    )
}

/** "−1,5 kg since Sep 1". */
@Composable
internal fun changeSinceText(
    type: MeasurementType,
    change: MeasurementChange,
): String =
    stringResource(
        Res.string.body_change_since,
        measurementChangeText(type, change.amount),
        rememberDateFormatter().dayAndMonth(change.since),
    )

/** Progress photos are taken upright, so they're shown in portrait frames. */
internal const val PHOTO_ASPECT = 3f / 4f

/** Test tags for the Body screens. */
object BodyTags {
    const val CHART = "body.chart"
    const val PHOTO = "body.photo"
}

/** Picks the day something was measured or photographed; days still to come can't have anything. */
@Composable
internal fun DayPicker(
    day: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The picker works in UTC midnights.
    val latest = remember(today) { today.utcMillis() }
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = day.utcMillis(),
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= latest
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { picked ->
                        onPick(Instant.fromEpochMilliseconds(picked).toLocalDateTime(TimeZone.UTC).date)
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text(stringResource(Res.string.dialog_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

private fun LocalDate.utcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
