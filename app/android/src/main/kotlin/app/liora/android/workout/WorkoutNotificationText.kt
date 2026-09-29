package app.liora.android.workout

import android.content.Context
import app.liora.android.R
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.model.LoggedSet
import app.liora.core.model.TrackingType
import app.liora.core.model.WeightUnit
import java.text.NumberFormat

/** The workout notification's wording, in the app's language with its number format ("82,5 kg" in German). */
internal class WorkoutNotificationText(
    private val context: Context,
) {
    /** "Barbell Bench Press · Set 3 of 4 · 80 kg × 8". */
    fun describe(next: NextSet): String =
        listOfNotNull(
            next.exerciseName.ifEmpty { null },
            context.getString(R.string.notif_set_of, next.setNumber, next.setCount),
            values(next.values, next.trackingType),
        ).joinToString(SEPARATOR)

    /** A set's values in the columns its exercise uses: "80 kg × 8", "12 reps", "5 km · 25:00". */
    private fun values(
        set: LoggedSet,
        trackingType: TrackingType,
    ): String? {
        val numbers =
            NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
                maximumFractionDigits =
                    2
            }
        val weight =
            set.weight
                ?.takeIf {
                    trackingType.usesWeight
                }?.let { "${numbers.format(it.kilograms)} ${WeightUnit.Kilogram.symbol}" }
        val reps = set.reps?.takeIf { trackingType.usesReps }
        val distance =
            set.distanceMeters?.takeIf { trackingType.usesDistance }?.let {
                if (trackingType ==
                    TrackingType.DistanceDuration
                ) {
                    "${numbers.format(it / METERS_PER_KM)} km"
                } else {
                    "${numbers.format(it)} m"
                }
            }
        val duration = set.duration?.takeIf { trackingType.usesDuration }?.formatAsClock()
        return when {
            weight != null && reps != null -> "$weight × ${numbers.format(reps)}"
            reps != null -> context.resources.getQuantityString(R.plurals.notif_reps, reps, numbers.format(reps))
            else -> listOfNotNull(weight, distance, duration).joinToString(SEPARATOR).ifEmpty { null }
        }
    }

    private companion object {
        const val SEPARATOR = " · "
        const val METERS_PER_KM = 1_000.0
    }
}
