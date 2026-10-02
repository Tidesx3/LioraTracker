package app.liora.core.model

import kotlin.time.Instant

/** A body measurement: bodyweight, body fat or a circumference, taken at one moment. */
data class Measurement(
    val id: String,
    val type: MeasurementType,
    val takenAt: Instant,
    /** In the type's canonical unit: kilograms, percent or metres (see [MeasurementKind]). */
    val value: Double,
)

/** How a measurement is measured, and the unit it's stored in. */
enum class MeasurementKind {
    /** Kilograms. */
    Mass,

    /** Percent, 0–100. */
    Percent,

    /** Metres; circumferences are shown in centimetres. */
    Length,
}

/**
 * What can be measured, in the order the app lists it. Keys are persisted and synced, so they must
 * never change. Left and right sides are separate types, since they're tracked separately.
 */
enum class MeasurementType(
    val key: String,
    val kind: MeasurementKind,
) {
    Bodyweight("bodyweight", MeasurementKind.Mass),
    BodyFat("body_fat", MeasurementKind.Percent),
    Neck("neck", MeasurementKind.Length),
    Shoulders("shoulders", MeasurementKind.Length),
    Chest("chest", MeasurementKind.Length),
    BicepsLeft("biceps_left", MeasurementKind.Length),
    BicepsRight("biceps_right", MeasurementKind.Length),
    ForearmLeft("forearm_left", MeasurementKind.Length),
    ForearmRight("forearm_right", MeasurementKind.Length),
    Abdomen("abdomen", MeasurementKind.Length),
    Waist("waist", MeasurementKind.Length),
    Hips("hips", MeasurementKind.Length),
    ThighLeft("thigh_left", MeasurementKind.Length),
    ThighRight("thigh_right", MeasurementKind.Length),
    CalfLeft("calf_left", MeasurementKind.Length),
    CalfRight("calf_right", MeasurementKind.Length),
    ;

    companion object {
        fun fromKey(key: String): MeasurementType? = entries.firstOrNull { it.key == key }
    }
}
