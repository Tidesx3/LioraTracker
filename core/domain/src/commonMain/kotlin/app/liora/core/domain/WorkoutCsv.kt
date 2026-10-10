package app.liora.core.domain

import app.liora.core.model.FinishedWorkout
import app.liora.core.model.Measurement
import app.liora.core.model.Units
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Instant

/** What a CSV export holds: the workouts, the body measurements, or both in one file. */
enum class CsvContent { Workouts, Body, Both }

/**
 * Finished workouts as CSV for spreadsheets (see [CsvWriter]): one row per logged set, oldest workout
 * first. Weights and distances are in the user's units, named in the header (`weight_kg` or `weight_lb`,
 * `distance_km` or `distance_mi`); times are local, as `2026-10-02 18:05`.
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
    ): String {
        val csv = CsvWriter()
        csv.row(header(units))
        rows(workouts, names, units, zone, untitled).forEach { csv.row(it.fields) }
        return csv.toString()
    }

    /**
     * Workouts and body measurements in one file, in the order they happened: a `record` column says
     * what each row is, `set` (the columns of [format]) or `body` (a day, in the columns of [BodyCsv]),
     * and leaves the other's columns empty. A day's measurements come before that day's workouts.
     */
    fun formatWithBody(
        workouts: List<FinishedWorkout>,
        measurements: List<Measurement>,
        names: Map<String, String>,
        units: Units,
        zone: TimeZone,
        untitled: String,
    ): String {
        val setHeader = header(units)
        val types = BodyCsv.measuredTypes(measurements)
        val bodyHeader = BodyCsv.valueHeader(types, units)
        val days =
            BodyCsv.days(measurements, types, units, zone).map { day ->
                Row(
                    at = day.date.atStartOfDayIn(zone),
                    fields =
                        listOf(BODY_RECORD, CsvWriter.date(day.date)) +
                            List(setHeader.size - 1) { null } +
                            day.values,
                )
            }
        val sets =
            rows(workouts, names, units, zone, untitled).map { set ->
                set.copy(fields = listOf(SET_RECORD) + set.fields + List(bodyHeader.size) { null })
            }
        val csv = CsvWriter()
        csv.row(listOf(RECORD) + setHeader + bodyHeader)
        // Sorting keeps the order on a tie, so a day's measurements, at its first moment, stay first.
        (days + sets).sortedBy { it.at }.forEach { csv.row(it.fields) }
        return csv.toString()
    }

    private fun rows(
        workouts: List<FinishedWorkout>,
        names: Map<String, String>,
        units: Units,
        zone: TimeZone,
        untitled: String,
    ): List<Row> =
        workouts.sortedBy { it.startedAt }.flatMap { workout ->
            workout.exercises.flatMap { exercise ->
                exercise.sets.filter { it.isCompleted }.mapIndexed { index, set ->
                    Row(
                        at = workout.startedAt,
                        fields =
                            listOf(
                                CsvWriter.time(workout.startedAt, zone),
                                CsvWriter.time(workout.endedAt, zone),
                                workout.name ?: untitled,
                                names[exercise.exerciseId] ?: exercise.exerciseId,
                                exercise.supersetGroup?.toString(),
                                (index + 1).toString(),
                                set.type.key,
                                set.weight?.let { CsvWriter.decimal(it.inUnit(units.weight), WEIGHT_DECIMALS) },
                                set.reps?.toString(),
                                set.distanceMeters?.let {
                                    CsvWriter.decimal(units.distance.long.fromMeters(it), DISTANCE_DECIMALS)
                                },
                                set.duration?.inWholeSeconds?.toString(),
                                set.rpe?.let { CsvWriter.decimal(it, RPE_DECIMALS) },
                                exercise.notes,
                                workout.notes,
                            ),
                    )
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

    /** A row and when it happened, to put a combined file in order. */
    private data class Row(
        val at: Instant,
        val fields: List<String?>,
    )

    private const val RECORD = "record"
    private const val SET_RECORD = "set"
    private const val BODY_RECORD = "body"

    /** Pounds typed as 225 are stored in kilograms; two places bring them back to 225. */
    private const val WEIGHT_DECIMALS = 2

    /** A metre in kilometres, about two yards in miles. */
    private const val DISTANCE_DECIMALS = 3
    private const val RPE_DECIMALS = 1
}
