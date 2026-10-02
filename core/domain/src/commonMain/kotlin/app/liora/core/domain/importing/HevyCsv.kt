package app.liora.core.domain.importing

import app.liora.core.model.LengthUnit
import app.liora.core.model.Mass
import app.liora.core.model.MeasurementKind
import app.liora.core.model.MeasurementType
import app.liora.core.model.SetType
import app.liora.core.model.WeightUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/**
 * Hevy's two CSV exports (Settings › Export & Import Data), in any language: column names are the same
 * everywhere, but dates are written in the phone's language (`30 Sept. 2026, 17:39`), in local time.
 *
 * - **Workouts:** one row per set, with `title, start_time, end_time, description, exercise_title,
 *   superset_id, exercise_notes, set_index, set_type, weight_kg, reps, distance_km, duration_seconds, rpe`.
 *   Weights are `weight_lbs` and distances `distance_miles` for people who log in pounds and miles.
 * - **Measurements:** one row per day, with `date`, `weight_kg`, `fat_percent` and circumferences such
 *   as `waist_cm` (or `_in`).
 */
internal object HevyCsv {
    fun isWorkouts(table: CsvTable) = table.has(EXERCISE) && table.has(START)

    fun isMeasurements(table: CsvTable) =
        table.has(DATE) && !table.has(EXERCISE) && MEASUREMENT_COLUMNS.any { (column, _) -> table.has(column) }

    fun workouts(
        table: CsvTable,
        zone: TimeZone,
    ): WorkoutFile {
        val weight = table.firstOf("weight_kg", "weight_lbs", "weight_lb")
        val weightUnit = if (weight == "weight_kg") WeightUnit.Kilogram else WeightUnit.Pound
        val distance = table.firstOf("distance_km", "distance_miles", "distance_mi", "distance_meters", "distance_m")
        val distanceUnit =
            when (distance) {
                "distance_km" -> LengthUnit.Kilometer
                "distance_miles", "distance_mi" -> LengthUnit.Mile
                else -> LengthUnit.Meter
            }
        // A workout's rows share its title and times, and come one exercise after another.
        val workouts =
            table.rows
                .groupBy { Triple(it[TITLE], it[START], it[END]) }
                .mapNotNull { (key, rows) ->
                    val (title, start, end) = key
                    val startedAt = start?.let(ExportDates::parse)?.toInstant(zone) ?: return@mapNotNull null
                    val endedAt = end?.let(ExportDates::parse)?.toInstant(zone)
                    val exercises = exercises(rows, weight, weightUnit, distance, distanceUnit)
                    if (exercises.isEmpty()) return@mapNotNull null
                    ImportedWorkout(
                        name = title,
                        startedAt = startedAt,
                        endedAt = endedAt?.takeIf { it >= startedAt } ?: startedAt,
                        notes = rows.firstNotNullOfOrNull { it["description"] },
                        exercises = exercises,
                    )
                }
        return WorkoutFile(ImportSource.Hevy, workouts)
    }

    private fun exercises(
        rows: List<CsvTable.Row>,
        weight: String?,
        weightUnit: WeightUnit,
        distance: String?,
        distanceUnit: LengthUnit,
    ): List<ImportedExercise> {
        // An exercise's sets follow each other; its name, superset or a restarting set count ends it.
        val blocks = mutableListOf<MutableList<CsvTable.Row>>()
        var previous: CsvTable.Row? = null
        for (row in rows) {
            val last = previous
            val continues =
                last != null &&
                    last[EXERCISE] == row[EXERCISE] &&
                    last[SUPERSET] == row[SUPERSET] &&
                    (parseNumber(row[SET_INDEX]) ?: 0.0) > (parseNumber(last[SET_INDEX]) ?: -1.0)
            if (continues) blocks.last() += row else blocks += mutableListOf(row)
            previous = row
        }
        val supersets = blocks.mapNotNull { it.first()[SUPERSET] }.distinct()
        return blocks.mapNotNull { block ->
            val first = block.first()
            val name = first[EXERCISE] ?: return@mapNotNull null
            val sets =
                block.mapNotNull { row ->
                    ImportedSet(
                        type = setType(row["set_type"]),
                        weight = positive(row[weight])?.let { Mass.of(it, weightUnit) },
                        reps = positive(row["reps"])?.roundToInt(),
                        duration = positive(row["duration_seconds"])?.roundToInt()?.seconds,
                        distanceMeters = positive(row[distance])?.let(distanceUnit::toMeters),
                        rpe = positive(row["rpe"]),
                    ).takeIf { it.fields != SetFields.None }
                }
            if (sets.isEmpty()) return@mapNotNull null
            ImportedExercise(
                name = name,
                notes = block.firstNotNullOfOrNull { it["exercise_notes"] },
                supersetGroup = first[SUPERSET]?.let { supersets.indexOf(it) + 1 },
                sets = sets,
            )
        }
    }

    fun measurements(table: CsvTable): MeasurementFile {
        val columns = MEASUREMENT_COLUMNS.filter { (column, _) -> table.has(column) }
        val measurements =
            table.rows
                .flatMap { row ->
                    val day = row[DATE]?.let(ExportDates::parse)?.date ?: return@flatMap emptyList()
                    columns.mapNotNull { (column, type) ->
                        positive(row[column])?.let { ImportedMeasurement(day, type, stored(it, column, type)) }
                    }
                }.associateBy { it.day to it.type }
                .values
                .sortedBy { it.day }
        return MeasurementFile(ImportSource.Hevy, measurements)
    }

    /** [value] in the type's stored unit, from the unit its column is named in. */
    private fun stored(
        value: Double,
        column: String,
        type: MeasurementType,
    ): Double =
        when (type.kind) {
            MeasurementKind.Mass -> {
                Mass.of(value, if (column.endsWith("_kg")) WeightUnit.Kilogram else WeightUnit.Pound).kilograms
            }

            MeasurementKind.Percent -> {
                value
            }

            MeasurementKind.Length -> {
                (if (column.endsWith("_cm")) LengthUnit.Centimeter else LengthUnit.Inch).toMeters(value)
            }
        }

    private fun setType(key: String?): SetType =
        when (key?.lowercase()) {
            "warmup", "warm_up" -> SetType.Warmup
            "dropset", "drop_set", "drop" -> SetType.Drop
            "failure" -> SetType.Failure
            else -> SetType.Normal
        }

    /** A value above zero; Hevy writes nothing, or sometimes 0, for values a set doesn't have. */
    private fun positive(text: String?): Double? = parseNumber(text)?.takeIf { it > 0.0 }

    private const val TITLE = "title"
    private const val START = "start_time"
    private const val END = "end_time"
    private const val EXERCISE = "exercise_title"
    private const val SUPERSET = "superset_id"
    private const val SET_INDEX = "set_index"
    private const val DATE = "date"

    /** Hevy's measurement columns, metric and imperial, with what each measures. */
    private val MEASUREMENT_COLUMNS: List<Pair<String, MeasurementType>> =
        listOf(
            "weight_kg" to MeasurementType.Bodyweight,
            "weight_lbs" to MeasurementType.Bodyweight,
            "weight_lb" to MeasurementType.Bodyweight,
            "fat_percent" to MeasurementType.BodyFat,
        ) +
            listOf(
                "neck" to MeasurementType.Neck,
                "shoulder" to MeasurementType.Shoulders,
                "chest" to MeasurementType.Chest,
                "left_bicep" to MeasurementType.BicepsLeft,
                "right_bicep" to MeasurementType.BicepsRight,
                "left_forearm" to MeasurementType.ForearmLeft,
                "right_forearm" to MeasurementType.ForearmRight,
                "abdomen" to MeasurementType.Abdomen,
                "waist" to MeasurementType.Waist,
                "hips" to MeasurementType.Hips,
                "left_thigh" to MeasurementType.ThighLeft,
                "right_thigh" to MeasurementType.ThighRight,
                "left_calf" to MeasurementType.CalfLeft,
                "right_calf" to MeasurementType.CalfRight,
            ).flatMap { (part, type) -> listOf("${part}_cm" to type, "${part}_in" to type) }
}
