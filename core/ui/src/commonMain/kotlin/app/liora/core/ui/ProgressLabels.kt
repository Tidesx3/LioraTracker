package app.liora.core.ui

import androidx.compose.runtime.Composable
import app.liora.core.designsystem.util.NumberFormatter
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.domain.ProgressMetric
import app.liora.core.domain.RecordType
import app.liora.core.model.LengthUnit
import app.liora.core.model.Mass
import app.liora.core.model.TrackingType
import app.liora.core.model.Units
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

/**
 * A stored value in the unit it's shown in: kilograms as pounds where chosen, metres as kilometres,
 * miles, metres or yards, and a pace per kilometre as one per mile. Charts plot shown values, so their
 * axis steps are round numbers in the unit read.
 */
fun ValueUnit.toShown(
    value: Double,
    units: Units,
    trackingType: TrackingType,
): Double =
    when (this) {
        ValueUnit.Kilograms -> Mass(value).inUnit(units.weight)
        ValueUnit.Reps, ValueUnit.Seconds -> value
        ValueUnit.Meters -> units.distanceFor(trackingType).fromMeters(value)
        ValueUnit.SecondsPerKilometer -> value * units.distance.long.metersPerUnit / LengthUnit.Kilometer.metersPerUnit
    }

/**
 * A stored [value] with its unit, as chosen and the way the locale writes it: "116,7 kg", "12 Wdh.",
 * "1:30", "5 km", "5:00 /km".
 */
@Composable
fun valueText(
    unit: ValueUnit,
    value: Double,
    trackingType: TrackingType,
): String {
    val numbers = rememberNumberFormatter()
    val units = LocalUnits.current
    val number = axisText(unit, unit.toShown(value, units, trackingType), numbers, trackingType)
    return when (unit) {
        ValueUnit.Reps -> {
            val reps = value.roundToInt()
            pluralStringResource(Res.plurals.reps_count, reps, number)
        }

        ValueUnit.Kilograms -> {
            "$number ${units.weight.symbol}"
        }

        ValueUnit.Meters -> {
            "$number ${units.distanceFor(trackingType).symbol}"
        }

        ValueUnit.Seconds -> {
            number
        }

        ValueUnit.SecondsPerKilometer -> {
            "$number /${units.distance.long.symbol}"
        }
    }
}

/**
 * A value already in the unit it's shown in ([toShown]), without the unit, for a chart axis whose
 * title names it: "116,7", "12", "1:30".
 */
fun axisText(
    unit: ValueUnit,
    shown: Double,
    numbers: NumberFormatter,
    trackingType: TrackingType,
): String =
    when (unit) {
        // A tonne of volume needs no decimals; a one-rep max reads to the half kilo.
        ValueUnit.Kilograms -> {
            numbers.format(shown, maxFractionDigits = if (shown >= THOUSAND) 0 else 1)
        }

        ValueUnit.Reps -> {
            numbers.format(shown.roundToInt())
        }

        // A run to the ten metres, a carry to the metre.
        ValueUnit.Meters -> {
            if (trackingType.longDistance) {
                numbers.format(
                    shown,
                    maxFractionDigits = 2,
                )
            } else {
                numbers.format(shown.roundToInt())
            }
        }

        ValueUnit.Seconds, ValueUnit.SecondsPerKilometer -> {
            shown.roundToInt().seconds.formatAsClock()
        }
    }

private const val THOUSAND = 1_000.0
