package app.liora.core.domain

import app.liora.core.model.FinishedWorkout
import app.liora.core.model.LengthUnit
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.Measurement
import app.liora.core.model.MeasurementType
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import app.liora.core.model.WorkoutExercise
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** The body measurements export, on its own and in one file with the workouts. */
class BodyCsvTest {
    private val bom = Char(0xFEFF).toString()
    private val berlin = TimeZone.of("Europe/Berlin")
    private var counter = 0

    private val measurements =
        listOf(
            measured(MeasurementType.Bodyweight, "2026-09-30T05:10:00Z", 82.4),
            // Weighed again that evening: the day shows the last value, as on Body.
            measured(MeasurementType.Bodyweight, "2026-09-30T19:00:00Z", 83.0),
            measured(MeasurementType.Waist, "2026-09-30T05:12:00Z", 0.845),
            measured(MeasurementType.BodyFat, "2026-10-02T06:00:00Z", 15.25),
            measured(MeasurementType.Bodyweight, "2026-10-02T06:00:00Z", 82.1),
            // Shortly after midnight in Berlin, still the evening before in UTC: it counts for 2 October.
            measured(MeasurementType.Waist, "2026-10-01T22:30:00Z", 0.84),
        )

    @Test
    fun oneRowPerDayWithAColumnForEachMeasurementTaken() {
        val csv = BodyCsv.format(measurements, Units(), berlin)

        assertEquals(
            listOf(
                // Only what was measured, in Body's order, each named with its unit.
                "date,bodyweight_kg,body_fat_percent,waist_cm",
                "2026-09-30,83,,84.5",
                "2026-10-02,82.1,15.25,84",
                "",
            ),
            csv.removePrefix(bom).split("\r\n"),
        )
    }

    @Test
    fun valuesAreInTheUsersUnits() {
        val typedInPounds =
            listOf(
                measured(
                    MeasurementType.Bodyweight,
                    "2026-10-02T06:00:00Z",
                    Mass.of(181.5, WeightUnit.Pound).kilograms,
                ),
                measured(MeasurementType.Waist, "2026-10-02T06:00:00Z", LengthUnit.Inch.toMeters(33.25)),
            )
        val csv = BodyCsv.format(typedInPounds, Units(weight = WeightUnit.Pound, bodyLength = LengthUnit.Inch), berlin)

        assertEquals(
            listOf("date,bodyweight_lb,waist_in", "2026-10-02,181.5,33.25", ""),
            csv.removePrefix(bom).split("\r\n"),
        )
    }

    @Test
    fun nothingMeasuredIsJustTheDateColumn() {
        assertEquals("${bom}date\r\n", BodyCsv.format(emptyList(), Units(), berlin))
    }

    @Test
    fun bothInOneFileInTheOrderTheyHappened() {
        val push =
            FinishedWorkout(
                id = "push",
                name = "Push",
                startedAt = Instant.parse("2026-10-01T16:05:00Z"),
                endedAt = Instant.parse("2026-10-01T17:12:00Z"),
                exercises =
                    listOf(
                        WorkoutExercise(
                            id = "we",
                            exerciseId = "bench",
                            sets =
                                listOf(
                                    LoggedSet(
                                        "s",
                                        weight = Mass(82.5),
                                        reps = 8,
                                        completedAt = Instant.parse("2026-10-01T16:10:00Z"),
                                    ),
                                ),
                        ),
                    ),
            )
        val legs =
            push.copy(
                id = "legs",
                name = "Legs",
                startedAt = Instant.parse("2026-10-02T15:00:00Z"),
                endedAt = Instant.parse("2026-10-02T16:00:00Z"),
            )

        val csv =
            WorkoutCsv.formatWithBody(
                listOf(legs, push),
                measurements,
                mapOf("bench" to "Bench Press"),
                Units(),
                berlin,
                untitled = "Workout",
            )

        assertEquals(
            listOf(
                "record,start,end,workout,exercise,superset,set,set_type,weight_kg,reps,distance_km,duration_s,rpe," +
                    "exercise_notes,workout_notes,bodyweight_kg,body_fat_percent,waist_cm",
                "body,2026-09-30,,,,,,,,,,,,,,83,,84.5",
                "set,2026-10-01 18:05,2026-10-01 19:12,Push,Bench Press,,1,normal,82.5,8,,,,,,,,",
                // The day's measurements first, then that day's workout.
                "body,2026-10-02,,,,,,,,,,,,,,82.1,15.25,84",
                "set,2026-10-02 17:00,2026-10-02 18:00,Legs,Bench Press,,1,normal,82.5,8,,,,,,,,",
                "",
            ),
            csv.removePrefix(bom).split("\r\n"),
        )
    }

    private fun measured(
        type: MeasurementType,
        at: String,
        value: Double,
    ) = Measurement(id = "m-${counter++}", type = type, takenAt = Instant.parse(at), value = value)
}
