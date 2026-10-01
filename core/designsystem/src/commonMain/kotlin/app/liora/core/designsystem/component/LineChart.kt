package app.liora.core.designsystem.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.util.rememberDateFormatter
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.multiplatform.cartesian.Zoom
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.multiplatform.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.ProvideVicoTheme
import com.patrykandpatrick.vico.multiplatform.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.multiplatform.m3.common.rememberM3VicoTheme
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** One point of a [LineChart]: a value on a day. */
@Immutable
data class ChartPoint(
    val day: LocalDate,
    val value: Double,
)

/**
 * A value over time, in the theme's primary color: an exercise's progress, a bodyweight curve. Points
 * sit at their dates, so a break shows as a gap, and the whole range fits the width. The value axis
 * hugs the data instead of starting at zero, since progress lives in the top few percent.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val days = points.map { it.day.toEpochDays().toDouble() }
    val values = points.map { it.value }
    val model =
        remember(points) {
            CartesianChartModel(LineCartesianLayerModel.build { series(x = days, y = values) })
        }
    val dates = rememberDateFormatter()
    val primary = MaterialTheme.colorScheme.primary
    val dateLabels =
        remember(dates) {
            CartesianValueFormatter { _, x, _ -> dates.dayAndMonth(LocalDate.fromEpochDays(x.toLong())) }
        }
    val valueLabels = remember(formatValue) { CartesianValueFormatter { _, y, _ -> formatValue(y) } }
    // A few date labels whatever the range, in steps a calendar knows: days, weeks, months.
    val labelSpacing = DATE_STEPS.firstOrNull { it >= (days.max() - days.min()) / DATE_LABELS } ?: DATE_STEPS.last()
    val axis = remember(values) { ValueAxis.of(values) }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        val line =
            LineCartesianLayer.rememberLine(
                fill = remember(primary) { LineCartesianLayer.LineFill.single(Fill(primary)) },
                pointProvider =
                    LineCartesianLayer.PointProvider.single(
                        LineCartesianLayer.Point(
                            rememberShapeComponent(fill = Fill(primary), shape = CircleShape),
                            size = 8.dp,
                        ),
                    ),
            )
        val chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(line),
                    rangeProvider =
                        remember(
                            axis,
                        ) { CartesianLayerRangeProvider.fixed(minY = axis.min, maxY = axis.max) },
                ),
                startAxis =
                    VerticalAxis.rememberStart(
                        valueFormatter = valueLabels,
                        itemPlacer = remember(axis) { VerticalAxis.ItemPlacer.step({ axis.step }) },
                    ),
                bottomAxis =
                    HorizontalAxis.rememberBottom(
                        valueFormatter = dateLabels,
                        itemPlacer = remember(labelSpacing) { HorizontalAxis.ItemPlacer.aligned({ labelSpacing }) },
                    ),
                // A step of one day keeps the dates' real spacing.
                getXStep = { _, _, _ -> 1.0 },
            )
        CartesianChartHost(
            chart = chart,
            model = model,
            modifier = modifier,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
    }
}

/**
 * The value axis: room above and below the data, never below zero, labelled in round steps such as
 * 90, 95, 100, 105 rather than 92.5, 96, 99.5, 103.1.
 */
private data class ValueAxis(
    val min: Double,
    val max: Double,
    val step: Double,
) {
    companion object {
        fun of(values: List<Double>): ValueAxis {
            val min = values.min()
            val max = values.max()
            val spread = if (max > min) max - min else max.coerceAtLeast(1.0) * RANGE_PADDING
            val step = niceStep(spread / (VALUE_LABELS - 1))
            val low = (floor(min / step) * step).coerceAtLeast(0.0)
            return ValueAxis(low, low + step * ceil((max - low) / step).coerceAtLeast(1.0), step)
        }

        /** The round step at or above [raw]: 1, 2, 2.5 or 5 times a power of ten. */
        private fun niceStep(raw: Double): Double {
            val magnitude = 10.0.pow(floor(log10(raw)))
            // Ten times the magnitude is always at least raw, so one of these fits.
            return NICE_STEPS.map { it * magnitude }.first { it >= raw }
        }
    }
}

private const val DATE_LABELS = 4.0
private const val VALUE_LABELS = 4
private const val RANGE_PADDING = 0.1
private val NICE_STEPS = listOf(1.0, 2.0, 2.5, 5.0, 10.0)

/** Days between date labels: a day, two, a week, two, four, eight, a quarter, half a year, a year. */
private val DATE_STEPS = listOf(1, 2, 7, 14, 28, 56, 91, 182, 364)
