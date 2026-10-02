package app.liora.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.model.LengthUnit
import app.liora.core.model.Mass
import app.liora.core.model.TrackingType
import app.liora.core.model.Units

// Weights and distances in the units the user chose. Values are stored in kilograms and metres; screens
// convert only to show them, and the logger and editors convert what's typed back.

/**
 * The units the user chose in Settings. The app provides it at its root; screens read it rather than
 * assuming kilograms.
 */
val LocalUnits = staticCompositionLocalOf { Units() }

/** Runs, rides and rows are long distances (km or mi); carries and sled pushes are short ones (m or yd). */
val TrackingType.longDistance: Boolean get() = this == TrackingType.DistanceDuration

/** The unit [trackingType]'s distances are shown and typed in. */
fun Units.distanceFor(trackingType: TrackingType): LengthUnit =
    if (trackingType.longDistance) distance.long else distance.short

/** A weight in the chosen unit, the way the locale writes it: "82,5 kg", "180 lb". */
@Composable
fun weightText(
    weight: Mass,
    maxFractionDigits: Int = 2,
): String {
    val unit = LocalUnits.current.weight
    return "${rememberNumberFormatter().format(weight.inUnit(unit), maxFractionDigits)} ${unit.symbol}"
}

/** Volume lifted, to the whole kilo or pound: "12.345 kg". */
@Composable
fun volumeText(kilograms: Double): String = weightText(Mass(kilograms), maxFractionDigits = 0)

/** A distance in the unit [trackingType] uses: "5 km", "3,1 mi", "40 m", "44 yd". */
@Composable
fun distanceText(
    meters: Double,
    trackingType: TrackingType,
): String {
    val unit = LocalUnits.current.distanceFor(trackingType)
    return "${rememberNumberFormatter().format(unit.fromMeters(meters))} ${unit.symbol}"
}
