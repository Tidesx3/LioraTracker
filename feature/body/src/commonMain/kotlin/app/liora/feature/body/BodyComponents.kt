package app.liora.feature.body

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
import org.jetbrains.compose.resources.stringResource

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

/** Test tags for the Body screens. */
object BodyTags {
    const val CHART = "body.chart"
}
