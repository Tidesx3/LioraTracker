package app.liora.core.domain.importing

import app.liora.core.model.LengthUnit
import app.liora.core.model.Mass
import app.liora.core.model.SetType
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Strong's workout export, one row per set, in either of its layouts:
 *
 * - `Date, Workout Name, Duration, Exercise Name, Set Order, Weight, Reps, Distance, Seconds, Notes,
 *   Workout Notes, RPE`: weights and distances in whatever units Strong was set to, which the file
 *   doesn't say.
 * - The older `Date; Workout Name; Exercise Name; Set Order; Weight; Weight Unit; Reps; RPE; Distance;
 *   Distance Unit; Seconds; Notes; Workout Notes; Workout Duration`, with each row's units.
 *
 * Dates are local, like `2026-09-30 17:39:18`, and durations read `1h 9m`. Set Order counts the
 * working sets and marks the others W (warm-up), D (drop set) or F (failure); rows that aren't sets,
 * such as rest timers, are skipped. Values a set doesn't have are written as 0.
 */
internal object StrongCsv {
    fun isWorkouts(table: CsvTable) = table.has(EXERCISE) && table.has(SET_ORDER)

    /** Reads the file, taking weights and distances that don't say their unit to be in [units]. */
    fun workouts(
        table: CsvTable,
        units: Units,
        zone: TimeZone,
    ): WorkoutFile {
        val duration = table.firstOf("duration", "workout duration")
        val workouts =
            table.rows
                .groupBy { it[DATE] to it[WORKOUT] }
                .mapNotNull { (key, rows) ->
                    val (date, name) = key
                    val startedAt = date?.let(ExportDates::parse)?.toInstant(zone) ?: return@mapNotNull null
                    val length = rows.firstNotNullOfOrNull { it[duration] }?.let(ExportDates::duration)
                    val exercises = exercises(rows, units)
                    if (exercises.isEmpty()) return@mapNotNull null
                    ImportedWorkout(
                        name = name,
                        startedAt = startedAt,
                        endedAt = startedAt + (length?.takeIf { it.isPositive() } ?: Duration.ZERO),
                        notes = rows.firstNotNullOfOrNull { it["workout notes"] },
                        exercises = exercises,
                    )
                }
        return WorkoutFile(ImportSource.Strong, workouts, unitsStated = table.has(WEIGHT_UNIT))
    }

    private fun exercises(
        rows: List<CsvTable.Row>,
        units: Units,
    ): List<ImportedExercise> {
        val blocks = mutableListOf<MutableList<CsvTable.Row>>()
        for (row in rows) {
            if (setType(row[SET_ORDER]) == null) continue
            val current = blocks.lastOrNull()
            if (current != null && current.first()[EXERCISE] == row[EXERCISE]) {
                current += row
            } else {
                blocks += mutableListOf(row)
            }
        }
        return blocks.mapNotNull { block ->
            val name = block.first()[EXERCISE] ?: return@mapNotNull null
            val sets = block.mapNotNull { row -> set(row, units).takeIf { it.fields != SetFields.None } }
            if (sets.isEmpty()) return@mapNotNull null
            ImportedExercise(name = name, notes = block.firstNotNullOfOrNull { it["notes"] }, sets = sets)
        }
    }

    private fun set(
        row: CsvTable.Row,
        units: Units,
    ): ImportedSet {
        val weightUnit =
            when (row[WEIGHT_UNIT]?.lowercase()) {
                "kg", "kgs" -> WeightUnit.Kilogram
                "lb", "lbs" -> WeightUnit.Pound
                else -> units.weight
            }
        val distanceUnit = row["distance unit"]?.lowercase()?.let(DISTANCE_UNITS::get) ?: units.distance.long
        return ImportedSet(
            type = setType(row[SET_ORDER]) ?: SetType.Normal,
            weight = positive(row["weight"])?.let { Mass.of(it, weightUnit) },
            reps = positive(row["reps"])?.roundToInt(),
            duration = positive(row["seconds"])?.roundToInt()?.seconds,
            distanceMeters = positive(row["distance"])?.let(distanceUnit::toMeters),
            rpe = positive(row["rpe"]),
        )
    }

    /** A set's type by its Set Order; null for rows that aren't sets. */
    private fun setType(order: String?): SetType? =
        when {
            order == null -> {
                null
            }

            order.toIntOrNull() != null -> {
                SetType.Normal
            }

            else -> {
                when (order.uppercase()) {
                    "W" -> SetType.Warmup
                    "D" -> SetType.Drop
                    "F" -> SetType.Failure
                    else -> null
                }
            }
        }

    private fun positive(text: String?): Double? = parseNumber(text)?.takeIf { it > 0.0 }

    private const val DATE = "date"
    private const val WORKOUT = "workout name"
    private const val EXERCISE = "exercise name"
    private const val SET_ORDER = "set order"
    private const val WEIGHT_UNIT = "weight unit"

    private val DISTANCE_UNITS =
        mapOf(
            "km" to LengthUnit.Kilometer,
            "m" to LengthUnit.Meter,
            "mi" to LengthUnit.Mile,
            "miles" to LengthUnit.Mile,
            "yd" to LengthUnit.Yard,
        )
}
