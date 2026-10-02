package app.liora.core.domain

import app.liora.core.model.FinishedWorkout
import app.liora.core.model.Units
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Instant

/**
 * Finished workouts as CSV for spreadsheets: one row per logged set, oldest workout first. It follows
 * RFC 4180 (commas, CRLF, quotes only where needed) with dot decimals, so any tool reads it the same
 * whatever its language. Weights and distances are in the user's units, named in the header
 * (`weight_kg` or `weight_lb`, `distance_km` or `distance_mi`); times are local, as `2026-10-02 18:05`.
 * A byte order mark leads, so Excel reads umlauts right.
 */
object WorkoutCsv {
    /**
     * [names] are the exercises' names by id, in the language the user reads; an exercise missing from
     * it shows its id. Unnamed workouts are called [untitled].
     */
    fun format(
        workouts: List<FinishedWorkout>,
        names: Map<String, String>,
        units: Units,
        zone: TimeZone,
        untitled: String,
    ): String =
        buildString {
            append(BYTE_ORDER_MARK)
            appendRow(header(units))
            for (workout in workouts.sortedBy { it.startedAt }) {
                for (exercise in workout.exercises) {
                    exercise.sets.filter { it.isCompleted }.forEachIndexed { index, set ->
                        appendRow(
                            listOf(
                                localTime(workout.startedAt, zone),
                                localTime(workout.endedAt, zone),
                                workout.name ?: untitled,
                                names[exercise.exerciseId] ?: exercise.exerciseId,
                                exercise.supersetGroup?.toString(),
                                (index + 1).toString(),
                                set.type.key,
                                set.weight?.let { decimal(it.inUnit(units.weight), WEIGHT_DECIMALS) },
                                set.reps?.toString(),
                                set.distanceMeters?.let {
                                    decimal(
                                        units.distance.long.fromMeters(it),
                                        DISTANCE_DECIMALS,
                                    )
                                },
                                set.duration?.inWholeSeconds?.toString(),
                                set.rpe?.let { decimal(it, RPE_DECIMALS) },
                                exercise.notes,
                                workout.notes,
                            ),
                        )
                    }
                }
            }
        }

    private fun header(units: Units) =
        listOf(
            "start",
            "end",
            "workout",
            "exercise",
            "superset",
            "set",
            "set_type",
            "weight_${units.weight.symbol}",
            "reps",
            "distance_${units.distance.long.symbol}",
            "duration_s",
            "rpe",
            "exercise_notes",
            "workout_notes",
        )

    private fun StringBuilder.appendRow(fields: List<String?>) {
        fields.forEachIndexed { index, field ->
            if (index > 0) append(',')
            append(quoted(field.orEmpty()))
        }
        append("\r\n")
    }

    /** A field as RFC 4180 has it: in quotes, with quotes doubled, when it holds a comma, quote or line break. */
    private fun quoted(field: String): String =
        if (field.any { it in NEEDS_QUOTES }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    private fun localTime(
        instant: Instant,
        zone: TimeZone,
    ): String {
        val local = instant.toLocalDateTime(zone)
        val hour = local.hour.toString().padStart(2, '0')
        val minute = local.minute.toString().padStart(2, '0')
        return "${local.date} $hour:$minute"
    }

    /** [value] with up to [decimals] places and a dot, without trailing zeros: 82.5, 100, 0.04. */
    private fun decimal(
        value: Double,
        decimals: Int,
    ): String {
        val factor = 10.0.pow(decimals).roundToLong()
        val scaled = (abs(value) * factor).roundToLong()
        val fraction = (scaled % factor).toString().padStart(decimals, '0').trimEnd('0')
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        return sign + (scaled / factor) + if (fraction.isEmpty()) "" else ".$fraction"
    }

    private val BYTE_ORDER_MARK = Char(0xFEFF)
    private val NEEDS_QUOTES = setOf(',', '"', '\n', '\r')

    /** Pounds typed as 225 are stored in kilograms; two places bring them back to 225. */
    private const val WEIGHT_DECIMALS = 2

    /** A metre in kilometres, about two yards in miles. */
    private const val DISTANCE_DECIMALS = 3
    private const val RPE_DECIMALS = 1
}
