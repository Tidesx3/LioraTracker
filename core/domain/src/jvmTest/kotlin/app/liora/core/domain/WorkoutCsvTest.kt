package app.liora.core.domain

import app.liora.core.model.DistanceUnit
import app.liora.core.model.FinishedWorkout
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.SetType
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import app.liora.core.model.WorkoutExercise
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The CSV export: one row per logged set, in the user's units, readable by any spreadsheet. */
class WorkoutCsvTest {
    private var counter = 0
    private val done = Instant.parse("2026-10-01T16:30:00Z")

    /** What the file starts with, so Excel reads it as UTF-8. */
    private val bom = Char(0xFEFF).toString()
    private val berlin = TimeZone.of("Europe/Berlin")
    private val names = mapOf("bench" to "Bankdrücken", "row" to "Rudern, sitzend", "run" to "Laufen")

    private val push =
        FinishedWorkout(
            id = "push",
            name = "Push",
            startedAt = Instant.parse("2026-10-01T16:05:00Z"),
            endedAt = Instant.parse("2026-10-01T17:12:00Z"),
            notes = "Gute Einheit\nSchulter ok",
            exercises =
                listOf(
                    WorkoutExercise(
                        id = "we1",
                        exerciseId = "bench",
                        supersetGroup = 1,
                        notes = "Griff \"eng\"",
                        sets =
                            listOf(
                                set(SetType.Warmup, weight = 40.0, reps = 10),
                                set(SetType.Normal, weight = 82.5, reps = 8, rpe = 8.5),
                                // Never ticked off: not part of the export.
                                LoggedSet("planned", weight = Mass(82.5), reps = 8),
                                set(SetType.Failure, weight = 82.5, reps = 6),
                            ),
                    ),
                    WorkoutExercise(
                        id = "we2",
                        exerciseId = "row",
                        supersetGroup = 1,
                        sets = listOf(set(weight = 60.0, reps = 12)),
                    ),
                ),
        )

    private val run =
        FinishedWorkout(
            id = "run",
            name = null,
            startedAt = Instant.parse("2026-09-30T05:30:00Z"),
            endedAt = Instant.parse("2026-09-30T06:00:00Z"),
            exercises =
                listOf(
                    WorkoutExercise(
                        id = "we3",
                        exerciseId = "run",
                        sets =
                            listOf(
                                LoggedSet("r", distanceMeters = 5_000.0, duration = 1_620.seconds, completedAt = done),
                            ),
                    ),
                    WorkoutExercise(id = "we4", exerciseId = "gone", sets = listOf(set(reps = 20))),
                ),
        )

    @Test
    fun oneRowPerLoggedSetOldestWorkoutFirst() {
        val csv = WorkoutCsv.format(listOf(push, run), names, Units(), berlin, untitled = "Training")

        assertTrue(csv.startsWith(bom))
        assertEquals(
            listOf(
                "start,end,workout,exercise,superset,set,set_type,weight_kg,reps,distance_km,duration_s,rpe," +
                    "exercise_notes,workout_notes",
                // Unnamed workouts get the default name, and an exercise that's gone shows its id.
                "2026-09-30 07:30,2026-09-30 08:00,Training,Laufen,,1,normal,,,5,1620,,,",
                "2026-09-30 07:30,2026-09-30 08:00,Training,gone,,1,normal,,20,,,,,",
                // Commas, quotes and line breaks are quoted; local time; sets counted per exercise.
                "2026-10-01 18:05,2026-10-01 19:12,Push,Bankdrücken,1,1,warmup,40,10,,,,\"Griff \"\"eng\"\"\"," +
                    "\"Gute Einheit\nSchulter ok\"",
                "2026-10-01 18:05,2026-10-01 19:12,Push,Bankdrücken,1,2,normal,82.5,8,,,8.5,\"Griff \"\"eng\"\"\"," +
                    "\"Gute Einheit\nSchulter ok\"",
                "2026-10-01 18:05,2026-10-01 19:12,Push,Bankdrücken,1,3,failure,82.5,6,,,,\"Griff \"\"eng\"\"\"," +
                    "\"Gute Einheit\nSchulter ok\"",
                "2026-10-01 18:05,2026-10-01 19:12,Push,\"Rudern, sitzend\",1,1,normal,60,12,,,,," +
                    "\"Gute Einheit\nSchulter ok\"",
                "",
            ),
            csv.removePrefix(bom).split("\r\n"),
        )
    }

    @Test
    fun weightsAndDistancesAreInTheUsersUnits() {
        val lifted =
            push.copy(
                exercises =
                    listOf(
                        WorkoutExercise(
                            id = "we",
                            exerciseId = "bench",
                            sets =
                                listOf(
                                    LoggedSet(
                                        "s",
                                        weight = Mass.of(225.0, WeightUnit.Pound),
                                        reps = 5,
                                        completedAt = done,
                                    ),
                                ),
                        ),
                    ),
            )
        val imperial = Units(WeightUnit.Pound, DistanceUnit.Mile)
        val csv = WorkoutCsv.format(listOf(lifted, run), names, imperial, berlin, "Training")
        val rows = csv.removePrefix(bom).split("\r\n")

        assertTrue(rows[0].contains(",weight_lb,reps,distance_mi,"))
        // 5 km is 3.107 miles; 225 lb stored as kilograms comes back as 225.
        assertTrue(rows[1].contains(",Laufen,,1,normal,,,3.107,1620,"))
        assertTrue(rows[3].contains(",Bankdrücken,,1,normal,225,5,"))
    }

    private fun set(
        type: SetType = SetType.Normal,
        weight: Double? = null,
        reps: Int? = null,
        rpe: Double? = null,
    ) = LoggedSet(
        id = "set-${counter++}",
        type = type,
        weight = weight?.let(::Mass),
        reps = reps,
        rpe = rpe,
        completedAt = done,
    )
}
