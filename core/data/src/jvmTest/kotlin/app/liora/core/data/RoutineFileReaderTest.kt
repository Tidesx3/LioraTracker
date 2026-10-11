package app.liora.core.data

import app.liora.core.data.importing.CustomExercisePlan
import app.liora.core.data.importing.FolderPlan
import app.liora.core.data.importing.ProblemPlace
import app.liora.core.data.importing.RoutineFile
import app.liora.core.data.importing.RoutineFileProblem
import app.liora.core.data.importing.RoutineFileProblem.Kind
import app.liora.core.data.importing.RoutineFileReader
import app.liora.core.data.importing.RoutineFileResult
import app.liora.core.data.importing.SetPlan
import app.liora.core.model.Equipment
import app.liora.core.model.Mass
import app.liora.core.model.Muscle
import app.liora.core.model.ProgressionRule
import app.liora.core.model.RepRange
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Routine files as AIs write them: every value in the forms people write it, the shortcuts, and every
 * problem reported with where it is, so whoever wrote the file can fix it.
 */
class RoutineFileReaderTest {
    @Test
    fun aSmallRoutine() {
        val file =
            read(
                """
                {
                  "format": "liora-routine",
                  "version": 1,
                  "weightUnit": "kg",
                  "routines": [
                    {
                      "name": "Full Body",
                      "notes": "Twice a week.",
                      "exercises": [
                        { "exercise": "Barbell Squat", "sets": 3, "reps": "5-8", "weight": 80, "rest": "3:00", "progression": "double" },
                        { "exercise": "Pull-Up", "sets": 3, "reps": "6-10" },
                        { "exercise": "Plank", "sets": 2, "duration": "1:00" }
                      ]
                    }
                  ]
                }
                """,
            )
        val routine = file.routines.single()
        assertEquals("Full Body", routine.name)
        assertEquals("Twice a week.", routine.notes)
        val (squat, pullUp, plank) = routine.exercises
        assertEquals("Barbell Squat", squat.exercise)
        assertEquals(180, squat.restSeconds)
        assertEquals(ProgressionRule.DoubleProgression, squat.progression)
        assertEquals(List(3) { SetPlan(weight = Mass(80.0), reps = RepRange(5, 8)) }, squat.sets)
        assertEquals(List(3) { SetPlan(reps = RepRange(6, 10)) }, pullUp.sets)
        assertEquals(List(2) { SetPlan(duration = 1.minutes) }, plank.sets)
        assertNull(file.folder)
        assertEquals(emptyList(), file.warnings)
    }

    @Test
    fun valuesInTheFormsPeopleWriteThem() {
        val sets =
            read(
                """
                {"format": "liora-routine", "version": 1, "weightUnit": "lb", "routines": [{"name": "A", "exercises": [
                  {"exercise": "X", "sets": [
                    {"weight": 135, "reps": "8–12"},
                    {"weight": "60 kg", "reps": "8 to 12", "rpe": 7.5},
                    {"weight": "82,5 kg", "reps": 10},
                    {"duration": "90s", "distance": "5 km"},
                    {"duration": "2 min", "distance": "1 mi"},
                    {"duration": "1:00:00", "distance": 400},
                    {"duration": 45, "distance": "50 yd"}
                  ]}
                ]}]}
                """,
            ).routines
                .single()
                .exercises
                .single()
                .sets
        assertEquals(Mass.of(135.0, WeightUnit.Pound), sets[0].weight)
        assertEquals(RepRange(8, 12), sets[0].reps)
        assertEquals(Mass(60.0), sets[1].weight)
        assertEquals(RepRange(8, 12), sets[1].reps)
        assertEquals(7.5, sets[1].rpe)
        assertEquals(Mass(82.5), sets[2].weight)
        assertEquals(RepRange(10), sets[2].reps)
        assertEquals(90.seconds, sets[3].duration)
        assertEquals(5_000.0, sets[3].distanceMeters)
        assertEquals(2.minutes, sets[4].duration)
        assertEquals(1_609.344, sets[4].distanceMeters)
        assertEquals(60.minutes, sets[5].duration)
        assertEquals(400.0, sets[5].distanceMeters)
        assertEquals(45.seconds, sets[6].duration)
        assertEquals(45.72, sets[6].distanceMeters!!, 1e-9)
    }

    @Test
    fun setsInAListRepeatAndTakeTheExercisesTargets() {
        val sets =
            read(
                """
                {"format": "liora-routine", "version": 1, "weightUnit": "kg", "routines": [{"name": "A", "exercises": [
                  {"exercise": "Bench", "reps": "6-8", "weight": 80, "sets": [
                    {"type": "warmup", "weight": 40, "reps": 10},
                    {"count": 3},
                    {"type": "drop", "weight": 60}
                  ]}
                ]}]}
                """,
            ).routines
                .single()
                .exercises
                .single()
                .sets
        assertEquals(
            listOf(SetPlan(SetType.Warmup, Mass(40.0), RepRange(10))) +
                List(3) { SetPlan(weight = Mass(80.0), reps = RepRange(6, 8)) } +
                SetPlan(SetType.Drop, Mass(60.0), RepRange(6, 8)),
            sets,
        )
    }

    @Test
    fun aProgramInARotationWithItsOwnExercises() {
        val file =
            read(
                """
                {"format": "liora-routine", "version": 1, "weightUnit": "kg",
                 "folder": {"name": "Upper / Lower", "rotation": true},
                 "customExercises": [
                   {"name": "Cable Y-Raise", "variationOf": "Cable Lateral Raise", "primaryMuscles": ["shoulders"],
                    "secondaryMuscles": ["traps"], "note": "Thumbs up."},
                   {"name": "Studio Leg Press", "tracking": "weight_reps", "equipment": "machine", "loadStep": "5 kg"}
                 ],
                 "routines": [
                   {"name": "Upper", "exercises": [
                     {"exercise": "Row", "superset": "A", "rest": 0, "sets": 3, "reps": 10},
                     {"exercise": "cable y-raise", "superset": "A", "rest": "90s", "note": "Slow.", "sets": 3, "reps": 15}
                   ]},
                   {"name": "Lower", "exercises": [{"exercise": "Studio Leg Press", "sets": 3, "reps": 12}]}
                 ]}
                """,
            )
        assertEquals(FolderPlan("Upper / Lower", rotation = true), file.folder)
        assertEquals(
            CustomExercisePlan(
                name = "Cable Y-Raise",
                variationOf = "Cable Lateral Raise",
                primaryMuscles = setOf(Muscle.Shoulders),
                secondaryMuscles = setOf(Muscle.Traps),
                note = "Thumbs up.",
            ),
            file.customExercises[0],
        )
        val press = file.customExercises[1]
        assertEquals(TrackingType.WeightReps, press.trackingType)
        assertEquals(Equipment.Machine, press.equipment)
        assertEquals(Mass(5.0), press.loadStep)
        val (row, raise) = file.routines[0].exercises
        assertEquals("A", row.superset)
        assertEquals(0, row.restSeconds)
        assertEquals(90, raise.restSeconds)
        assertEquals("Slow.", raise.note)
        // A routine names a custom exercise in any case.
        assertEquals(file.customExercises[0], file.customExercise(raise.exercise))
    }

    @Test
    fun theFileInsideAnAIsReply() {
        val file =
            read(
                """
                Here's your routine:

                ```json
                {
                  // Liora reads comments and trailing commas too.
                  "format": "liora-routine",
                  "version": 1,
                  "routines": [{"name": "Arms", "exercises": [{"exercise": "Curl", "sets": 2, "reps": 12,},],},],
                }
                ```

                Have fun!
                """,
            )
        assertEquals("Arms", file.routines.single().name)
    }

    @Test
    fun aRoutineOnItsOwn() {
        val file = read("""{"name": "Arms", "exercises": [{"exercise": "Curl", "sets": 2, "reps": 12}]}""")
        assertEquals("Arms", file.routines.single().name)
        assertEquals(emptyList(), file.warnings)
    }

    @Test
    fun onlyExercises() {
        val file =
            read(
                """{"format": "liora-routine", "version": 1,
                    "customExercises": [{"name": "Studio Row", "variationOf": "Seated Cable Row"}]}""",
            )
        assertEquals(emptyList(), file.routines)
        assertEquals("Studio Row", file.customExercises.single().name)
    }

    @Test
    fun whatItLeavesOutIsWorthKnowing() {
        val file =
            read(
                """
                {"format": "liora-routine", "version": 1, "author": "An AI", "routines": [{"name": "A", "exercises": [
                  {"exercise": "Bench", "superset": "S", "tempo": "3-1-1", "sets": 3, "reps": 8, "progression": "double"},
                  {"exercise": "Row", "sets": 3, "reps": "8-12"},
                  {"exercise": "Fly", "superset": "S", "sets": [{"reps": "10-15", "tempo": "slow"}]}
                ]}]}
                """,
            )
        val routine = ProblemPlace(routine = "A")
        assertEquals(
            listOf(
                RoutineFileProblem(Kind.UnknownField, ProblemPlace(), "author"),
                RoutineFileProblem(Kind.UnknownField, routine.copy(exercise = "Bench"), "tempo"),
                RoutineFileProblem(Kind.ProgressionNeedsRanges, routine.copy(exercise = "Bench")),
                RoutineFileProblem(Kind.UnknownField, routine.copy(exercise = "Fly", set = 1), "tempo"),
                RoutineFileProblem(Kind.SupersetApart, routine, "S"),
            ),
            file.warnings,
        )
    }

    @Test
    fun problemsSayWhereTheyAre() {
        val problems =
            problems(
                """
                {"format": "liora-routine", "version": 1, "routines": [
                  {"exercises": [{"exercise": "Curl", "sets": 2}]},
                  {"name": "Push", "exercises": [
                    {"exercise": "Bench", "sets": [{"weight": 80, "reps": "eight"}, {"type": "superset", "rpe": 8.3}]},
                    {"sets": 3},
                    {"exercise": "Dip", "sets": 0},
                    {"exercise": "Fly"}
                  ]}
                ],
                "customExercises": [{"name": "Thing"}, {"name": "thing", "tracking": "weights"}]}
                """,
            )
        val push = ProblemPlace(routine = "Push")
        val bench = push.copy(exercise = "Bench")
        assertEquals(
            listOf(
                RoutineFileProblem(Kind.Missing, ProblemPlace(exercise = "Thing", field = "tracking")),
                RoutineFileProblem(Kind.Invalid, ProblemPlace(exercise = "thing", field = "tracking"), "weights"),
                RoutineFileProblem(Kind.DuplicateExercise, value = "Thing"),
                RoutineFileProblem(Kind.Missing, ProblemPlace(routine = "#1", field = "name")),
                RoutineFileProblem(Kind.NoWeightUnit, bench.copy(set = 1, field = "weight"), "80"),
                RoutineFileProblem(Kind.Invalid, bench.copy(set = 1, field = "reps"), "eight"),
                RoutineFileProblem(Kind.Invalid, bench.copy(set = 2, field = "type"), "superset"),
                RoutineFileProblem(Kind.Invalid, bench.copy(set = 2, field = "rpe"), "8.3"),
                RoutineFileProblem(Kind.Missing, push.copy(exercise = "#2", field = "exercise")),
                RoutineFileProblem(Kind.Invalid, push.copy(exercise = "Dip", field = "sets"), "0"),
                RoutineFileProblem(Kind.Missing, push.copy(exercise = "Fly", field = "sets")),
            ),
            problems,
        )
    }

    @Test
    fun whatIsntARoutineFile() {
        assertEquals(listOf(RoutineFileProblem(Kind.NotJson)), problems("Sure! Here's a push day: bench, dips."))
        assertEquals(listOf(RoutineFileProblem(Kind.NotJson)), problems("""{"routines": [{"name": "A",}"""))
        assertEquals(listOf(RoutineFileProblem(Kind.NotARoutineFile)), problems("""{"format": "liora-backup"}"""))
        assertEquals(listOf(RoutineFileProblem(Kind.NotARoutineFile)), problems("""{"workouts": []}"""))
        assertEquals(
            listOf(RoutineFileProblem(Kind.NewerVersion, value = "2")),
            problems("""{"format": "liora-routine", "version": 2, "routines": []}"""),
        )
        assertEquals(
            listOf(RoutineFileProblem(Kind.Missing, ProblemPlace(field = "routines"))),
            problems("""{"format": "liora-routine", "version": 1, "routines": []}"""),
        )
    }

    @Test
    fun valuesOutOfReach() {
        val bench = ProblemPlace(routine = "A", exercise = "Bench")
        assertEquals(
            listOf(
                RoutineFileProblem(Kind.Invalid, bench.copy(field = "rest"), "2 hours"),
                RoutineFileProblem(Kind.Invalid, bench.copy(set = 1, field = "weight"), "2000 kg"),
                RoutineFileProblem(Kind.Invalid, bench.copy(set = 2, field = "reps"), "0"),
                RoutineFileProblem(Kind.Invalid, bench.copy(set = 3, field = "count"), "50"),
                RoutineFileProblem(Kind.Invalid, bench.copy(set = 4, field = "duration"), "1:75"),
                RoutineFileProblem(Kind.Invalid, bench.copy(set = 5, field = "weight"), "80 stone"),
            ),
            problems(
                """
                {"format": "liora-routine", "version": 1, "routines": [{"name": "A", "exercises": [
                  {"exercise": "Bench", "rest": "2 hours", "sets": [
                    {"weight": "2000 kg"}, {"reps": 0}, {"count": 50}, {"duration": "1:75"}, {"weight": "80 stone"}
                  ]}
                ]}]}
                """,
            ),
        )
    }

    private fun read(text: String): RoutineFile =
        when (val result = RoutineFileReader.read(text.trimIndent())) {
            is RoutineFileResult.Read -> result.file
            is RoutineFileResult.Invalid -> fail("Not read: ${result.problems}")
        }

    private fun problems(text: String): List<RoutineFileProblem> {
        val result = RoutineFileReader.read(text.trimIndent())
        assertTrue(result is RoutineFileResult.Invalid, "Read although it shouldn't be")
        return result.problems
    }
}
