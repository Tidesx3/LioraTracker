package app.liora.core.domain.importing

import app.liora.core.model.Mass
import app.liora.core.model.MeasurementType
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Instant

/** The app a file was exported from. */
enum class ImportSource { Hevy, Strong }

/** What a file exported from another app holds: read, but not matched to exercises or saved yet. */
sealed interface ImportFile {
    val source: ImportSource
}

/**
 * Workouts, in the order the file has them. When the file doesn't say which units its weights and
 * distances are in (Strong's newer layout), they were read in the units asked for, and [unitsStated]
 * is false.
 */
data class WorkoutFile(
    override val source: ImportSource,
    val workouts: List<ImportedWorkout>,
    val unitsStated: Boolean = true,
) : ImportFile

/** Body measurements, at most one per type and day (the file's last). */
data class MeasurementFile(
    override val source: ImportSource,
    val measurements: List<ImportedMeasurement>,
) : ImportFile

data class ImportedWorkout(
    val name: String?,
    val startedAt: Instant,
    val endedAt: Instant,
    val notes: String? = null,
    val exercises: List<ImportedExercise>,
)

/** An exercise as the other app named it, with its sets in that workout. */
data class ImportedExercise(
    val name: String,
    val notes: String? = null,
    /** Exercises next to each other with the same number were a superset. */
    val supersetGroup: Int? = null,
    val sets: List<ImportedSet>,
)

/** A logged set, in stored units; values the set didn't have are null. */
data class ImportedSet(
    val type: SetType = SetType.Normal,
    val weight: Mass? = null,
    val reps: Int? = null,
    val duration: Duration? = null,
    val distanceMeters: Double? = null,
    val rpe: Double? = null,
) {
    val fields: SetFields
        get() = SetFields(weight != null, reps != null, duration != null, distanceMeters != null)
}

/** A measurement in its type's stored unit (kilograms, percent or metres). */
data class ImportedMeasurement(
    val day: LocalDate,
    val type: MeasurementType,
    val value: Double,
)

/** Which values an exercise's imported sets hold: what its tracking type needs columns for. */
data class SetFields(
    val weight: Boolean = false,
    val reps: Boolean = false,
    val duration: Boolean = false,
    val distance: Boolean = false,
) {
    operator fun plus(other: SetFields) =
        SetFields(
            weight = weight || other.weight,
            reps = reps || other.reps,
            duration = duration || other.duration,
            distance = distance || other.distance,
        )

    /** Whether [type] has a column for every value. */
    fun fit(type: TrackingType): Boolean =
        (!weight || type.usesWeight) &&
            (!reps || type.usesReps) &&
            (!duration || type.usesDuration) &&
            (!distance || type.usesDistance)

    /**
     * The tracking type for a new exercise based on one tracked as [base]: the base's own when it has
     * a column for everything, a weighted one for bodyweight exercises logged with weight, else the
     * type that keeps the most.
     */
    fun trackingTypeFor(base: TrackingType?): TrackingType =
        when {
            base != null && fit(base) -> base

            base == TrackingType.BodyweightReps &&
                fit(
                    TrackingType.WeightedBodyweight,
                )
            -> TrackingType.WeightedBodyweight

            else -> keepingMost() ?: base ?: TrackingType.WeightReps
        }

    /** The tracking type with columns for the most of these values; null when there are none. */
    private fun keepingMost(): TrackingType? =
        when {
            distance && weight -> {
                TrackingType.WeightDistance
            }

            distance -> {
                TrackingType.DistanceDuration
            }

            duration && weight && !reps -> {
                TrackingType.DurationWeight
            }

            duration && !reps -> {
                TrackingType.Duration
            }

            weight -> {
                TrackingType.WeightReps
            }

            reps -> {
                TrackingType.BodyweightReps
            }

            else -> {
                null
            }
        }

    companion object {
        val None = SetFields()
    }
}
