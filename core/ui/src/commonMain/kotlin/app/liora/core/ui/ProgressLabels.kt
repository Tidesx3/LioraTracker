package app.liora.core.ui

import androidx.compose.runtime.Composable
import app.liora.core.designsystem.util.NumberFormatter
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.domain.ProgressMetric
import app.liora.core.domain.RecordType
import app.liora.core.model.TrackingType
import app.liora.core.model.WeightUnit
import app.liora.core.ui.resources.Res
import app.liora.core.ui.resources.metric_best_pace
import app.liora.core.ui.resources.metric_estimated_1rm
import app.liora.core.ui.resources.metric_heaviest_weight
import app.liora.core.ui.resources.metric_least_assistance
import app.liora.core.ui.resources.metric_longest_distance
import app.liora.core.ui.resources.metric_longest_duration
import app.liora.core.ui.resources.metric_most_reps
import app.liora.core.ui.resources.metric_total_duration
import app.liora.core.ui.resources.metric_total_reps
import app.liora.core.ui.resources.metric_volume
import app.liora.core.ui.resources.reps_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/** "Est. 1RM", "Volume", …: the short name of what a progress chart shows. */
@Composable
fun metricLabel(metric: ProgressMetric): String =
    stringResource(
        when (metric) {
            ProgressMetric.EstimatedOneRepMax -> Res.string.metric_estimated_1rm
            ProgressMetric.HeaviestWeight -> Res.string.metric_heaviest_weight
            ProgressMetric.Volume -> Res.string.metric_volume
            ProgressMetric.MostReps -> Res.string.metric_most_reps
            ProgressMetric.TotalReps -> Res.string.metric_total_reps
            ProgressMetric.LongestDuration -> Res.string.metric_longest_duration
            ProgressMetric.TotalDuration -> Res.string.metric_total_duration
            ProgressMetric.LongestDistance -> Res.string.metric_longest_distance
            ProgressMetric.BestPace -> Res.string.metric_best_pace
            ProgressMetric.LeastAssistance -> Res.string.metric_least_assistance
        },
    )

/** How a progress value or record is measured. */
enum class ValueUnit { Kilograms, Reps, Seconds, Meters, SecondsPerKilometer }

val ProgressMetric.unit: ValueUnit
    get() =
        when (this) {
            ProgressMetric.EstimatedOneRepMax,
            ProgressMetric.HeaviestWeight,
            ProgressMetric.Volume,
            ProgressMetric.LeastAssistance,
            -> ValueUnit.Kilograms

            ProgressMetric.MostReps, ProgressMetric.TotalReps -> ValueUnit.Reps

            ProgressMetric.LongestDuration, ProgressMetric.TotalDuration -> ValueUnit.Seconds

            ProgressMetric.LongestDistance -> ValueUnit.Meters

            ProgressMetric.BestPace -> ValueUnit.SecondsPerKilometer
        }

val RecordType.unit: ValueUnit
    get() =
        when (this) {
            RecordType.HeaviestWeight,
            RecordType.LeastAssistance,
            RecordType.EstimatedOneRepMax,
            RecordType.BestSetVolume,
            RecordType.RepMax,
            -> ValueUnit.Kilograms

            RecordType.MostReps -> ValueUnit.Reps

            RecordType.LongestDuration -> ValueUnit.Seconds

            RecordType.LongestDistance -> ValueUnit.Meters

            RecordType.BestPace -> ValueUnit.SecondsPerKilometer
        }

/** [value] with its unit, the way the locale writes it: "116,7 kg", "12 Wdh.", "1:30", "5 km", "5:00 /km". */
@Composable
fun valueText(
    unit: ValueUnit,
    value: Double,
    trackingType: TrackingType,
): String {
    val numbers = rememberNumberFormatter()
    return when (unit) {
        ValueUnit.Reps -> {
            val reps = value.roundToInt()
            pluralStringResource(Res.plurals.reps_count, reps, numbers.format(reps))
        }

        ValueUnit.Kilograms -> {
            "${axisText(unit, value, numbers, trackingType)} ${WeightUnit.Kilogram.symbol}"
        }

        ValueUnit.Meters -> {
            "${axisText(unit, value, numbers, trackingType)} ${if (trackingType.distanceInKilometers) "km" else "m"}"
        }

        ValueUnit.Seconds -> {
            axisText(unit, value, numbers, trackingType)
        }

        ValueUnit.SecondsPerKilometer -> {
            "${axisText(unit, value, numbers, trackingType)} /km"
        }
    }
}

/** [value] without its unit, for a chart axis whose title names it: "116,7", "12", "1:30". */
fun axisText(
    unit: ValueUnit,
    value: Double,
    numbers: NumberFormatter,
    trackingType: TrackingType,
): String =
    when (unit) {
        // A tonne of volume needs no decimals; a one-rep max reads to the half kilo.
        ValueUnit.Kilograms -> {
            numbers.format(value, maxFractionDigits = if (value >= THOUSAND) 0 else 1)
        }

        ValueUnit.Reps -> {
            numbers.format(value.roundToInt())
        }

        ValueUnit.Meters -> {
            if (trackingType.distanceInKilometers) {
                numbers.format(value / THOUSAND, maxFractionDigits = 2)
            } else {
                numbers.format(value.roundToInt())
            }
        }

        ValueUnit.Seconds, ValueUnit.SecondsPerKilometer -> {
            value.roundToInt().seconds.formatAsClock()
        }
    }

private const val THOUSAND = 1_000.0
