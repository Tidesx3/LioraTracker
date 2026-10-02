package app.liora.core.model

/**
 * The units weights, distances and body measurements are shown and typed in. Only a display choice:
 * values are stored in kilograms and metres, so switching never rewrites history.
 */
data class Units(
    val weight: WeightUnit = WeightUnit.Kilogram,
    val distance: DistanceUnit = DistanceUnit.Kilometer,
    /** Circumferences such as the waist: [LengthUnit.Centimeter] or [LengthUnit.Inch]. */
    val bodyLength: LengthUnit = LengthUnit.Centimeter,
) {
    companion object {
        /** The units a body measurement can be shown in. */
        val BODY_LENGTHS: List<LengthUnit> = listOf(LengthUnit.Centimeter, LengthUnit.Inch)
    }
}

/** A unit of length, for distances and body measurements, which are stored in metres. */
enum class LengthUnit(
    val metersPerUnit: Double,
    val symbol: String,
) {
    Meter(metersPerUnit = 1.0, symbol = "m"),
    Kilometer(metersPerUnit = 1_000.0, symbol = "km"),
    Centimeter(metersPerUnit = 0.01, symbol = "cm"),
    Mile(metersPerUnit = 1_609.344, symbol = "mi"),
    Yard(metersPerUnit = 0.9144, symbol = "yd"),
    Inch(metersPerUnit = 0.0254, symbol = "in"),
    ;

    fun fromMeters(meters: Double): Double = meters / metersPerUnit

    fun toMeters(value: Double): Double = value * metersPerUnit
}

/** How distances read: a run in kilometres and a carry in metres, or a run in miles and a carry in yards. */
enum class DistanceUnit(
    /** Runs, rides and rows. */
    val long: LengthUnit,
    /** Carries, sled pushes and other distances of a few dozen steps. */
    val short: LengthUnit,
) {
    Kilometer(long = LengthUnit.Kilometer, short = LengthUnit.Meter),
    Mile(long = LengthUnit.Mile, short = LengthUnit.Yard),
}
