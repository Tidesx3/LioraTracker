package app.liora.core.data

import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.backup.ArchiveWriter
import app.liora.core.data.backup.ExportFiles
import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.importing.OfflineRoutineImportRepository
import app.liora.core.data.importing.ProblemPlace
import app.liora.core.data.importing.RoutineFileProblem
import app.liora.core.data.importing.RoutineFileProblem.Kind
import app.liora.core.data.importing.RoutineImportPreview
import app.liora.core.data.importing.RoutineImportReading
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.seed.ExerciseCatalogSeeder
import app.liora.core.data.seed.ExerciseSeedSource
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.domain.importing.ExerciseChoice
import app.liora.core.model.Equipment
import app.liora.core.model.Exercise
import app.liora.core.model.Mass
import app.liora.core.model.Muscle
import app.liora.core.model.ProgressionRule
import app.liora.core.model.RepRange
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Routines from routine files: which exercise each name becomes, then the folder, routines, new exercises
 * and notes they bring, with what an exercise can't hold left out.
 */
class RoutineImportRepositoryTest {
    private val clock = TestClock()
    private val database = inMemoryLioraDatabase()
    private val transactions = TransactionRunner(database)
    private val ids = IdGenerator(clock)
    private val stamper = SyncStamper(HybridLogicalClock(clock) { "phone" }, clock)
    private val files = FakeFiles()
    private val exercises = OfflineExerciseRepository(database.exerciseDao(), transactions, ids, stamper)
    private val routines = OfflineRoutineRepository(database.routineDao(), transactions, ids, stamper)
    private val imports =
        OfflineRoutineImportRepository(
            files,
            exercises,
            database.exerciseDao(),
            database.routineDao(),
            transactions,
            ids,
            stamper,
        )

    init {
        runBlocking {
            ExerciseCatalogSeeder(
                ExerciseSeedSource { SEED },
                database.exerciseDao(),
                database.localMetaDao(),
                transactions,
                clock,
            ).seedIfNeeded()
        }
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun aProgramLandsInARotation() =
        runTest {
            files.texts[FILE] = PROGRAM
            val preview = ready(imports.readFile(FILE))
            assertEquals(
                listOf(
                    "Bench Press" to ExerciseChoice.Existing(BENCH),
                    "Lat Pulldown" to ExerciseChoice.Existing(LAT),
                    "Cable Y-Raise" to ExerciseChoice.New(basedOn = LATERAL_RAISE),
                    "Plank" to ExerciseChoice.Existing(PLANK),
                    SQUAT to ExerciseChoice.Existing(SQUAT),
                    // Weight on a bodyweight exercise: its weighted variant.
                    "Pull-Up" to ExerciseChoice.Existing(WEIGHTED_PULL_UP),
                ),
                preview.exercises.map { it.name to it.suggestion.choice },
            )
            assertEquals(listOf(false, false, false, false, true, false), preview.exercises.map { it.fixed })
            assertEquals(listOf(5, 3, 3, 2, 3, 3), preview.exercises.map { it.sets })

            val result = imports.import(preview, emptyMap())
            assertEquals(1, result.newExercises)

            val folder = routines.folders.first().single()
            assertEquals("Upper / Lower", folder.name)
            assertTrue(folder.rotation)
            val all = routines.routines.first()
            assertEquals(result.routineIds, all.map { it.id })
            assertEquals(listOf("Upper", "Lower"), all.map { it.name })
            assertTrue(all.all { it.folderId == folder.id })
            assertEquals("Heavy day.", all[0].notes)

            val raise = custom().single()
            assertEquals("Cable Y-Raise", raise.name)
            assertEquals(LATERAL_RAISE, raise.variationOf)
            assertEquals(TrackingType.WeightReps, raise.trackingType)
            assertEquals(Equipment.Cable, raise.equipment)
            assertEquals(setOf(Muscle.Shoulders), raise.primaryMuscles)
            assertEquals(setOf(Muscle.Traps), raise.secondaryMuscles)
            assertEquals("Thumbs up.", raise.settings.stickyNote)

            val upper = all[0].exercises
            val (bench, lat, yRaise) = upper
            val plank = upper[3]
            assertEquals(listOf(BENCH, LAT, raise.id, PLANK), upper.map { it.exerciseId })
            assertEquals(listOf(null, 1, 1, null), all[0].exercises.map { it.supersetGroup })
            assertEquals(listOf(180, 0, 90, null), all[0].exercises.map { it.restSeconds })
            assertEquals(ProgressionRule.DoubleProgression, bench.progression)
            assertEquals(
                listOf(SetType.Warmup, SetType.Warmup, SetType.Normal, SetType.Normal, SetType.Normal),
                bench.sets.map { it.type },
            )
            assertEquals(listOf(40.0, 60.0, 80.0, 80.0, 80.0), bench.sets.map { it.weight?.kilograms })
            assertEquals(RepRange(6, 8), bench.sets.last().reps)
            assertEquals(8.0, bench.sets.last().rpe)
            assertNull(lat.sets.first().weight)
            assertEquals(RepRange(12, 15), yRaise.sets.first().reps)
            // A plank has no column for weight, and progression doesn't fit it.
            assertEquals(listOf(60.seconds, 60.seconds), plank.sets.map { it.duration })
            assertEquals(listOf(null, null), plank.sets.map { it.weight })
            assertNull(plank.progression)

            val (squat, pullUp) = all[1].exercises
            assertEquals(SQUAT, squat.exerciseId)
            assertEquals(WEIGHTED_PULL_UP, pullUp.exerciseId)
            assertEquals(Mass(10.0), pullUp.sets.first().weight)

            // The bench note became the exercise's own.
            assertEquals("Arch, feet down.", exercise(BENCH).settings.stickyNote)
        }

    @Test
    fun notesNeverReplaceTheUsersOwn() =
        runTest {
            exercises.setNote(BENCH, "Seat on 4")
            files.texts[FILE] = PROGRAM
            imports.import(ready(imports.readFile(FILE)), emptyMap())
            assertEquals("Seat on 4", exercise(BENCH).settings.stickyNote)
        }

    @Test
    fun importingTwiceReusesItsExercises() =
        runTest {
            files.texts[FILE] = PROGRAM
            imports.import(ready(imports.readFile(FILE)), emptyMap())
            val raise = custom().single()

            val again = ready(imports.readFile(FILE))
            assertEquals(
                ExerciseChoice.Existing(raise.id),
                again.exercises
                    .single { it.name == "Cable Y-Raise" }
                    .suggestion.choice,
            )
            val result = imports.import(again, emptyMap())
            assertEquals(0, result.newExercises)
            assertEquals(listOf(raise.id), custom().map { it.id })
            assertEquals(4, routines.routines.first().size)
            // Each import is its own program.
            assertEquals(2, routines.folders.first().size)
        }

    @Test
    fun theUserDecidesWhereAnExerciseGoes() =
        runTest {
            val preview = ready(imports.read(MYSTERY))
            val mystery = preview.exercises.single()
            assertEquals(ExerciseChoice.New(basedOn = LAT), mystery.suggestion.choice)

            imports.import(preview, mapOf(mystery.name to ExerciseChoice.Existing(LAT)))
            assertEquals(
                LAT,
                routines.routines
                    .first()
                    .single()
                    .exercises
                    .single()
                    .exerciseId,
            )
            assertEquals(emptyList(), custom())
        }

    @Test
    fun aNameNothingHereHasBecomesAVariation() =
        runTest {
            imports.import(ready(imports.read(MYSTERY)), emptyMap())
            val machine = custom().single()
            assertEquals("Lat Pulldown Studio North", machine.name)
            assertEquals(LAT, machine.variationOf)
            assertEquals(Equipment.Cable, machine.equipment)
            assertEquals(
                machine.id,
                routines.routines
                    .first()
                    .single()
                    .exercises
                    .single()
                    .exerciseId,
            )
            // A routine outside any folder.
            assertNull(
                routines.routines
                    .first()
                    .single()
                    .folderId,
            )
            assertEquals(emptyList(), routines.folders.first())
        }

    @Test
    fun anIdThatNamesNothingIsAMistake() =
        runTest {
            val reading =
                imports.read(
                    """{"format": "liora-routine", "version": 1, "routines": [
                        {"name": "A", "exercises": [{"exercise": "fedb.nope", "sets": 3, "reps": 5}]}]}""",
                )
            assertEquals(
                RoutineImportReading.Invalid(
                    listOf(
                        RoutineFileProblem(
                            Kind.Invalid,
                            ProblemPlace(exercise = "fedb.nope", field = "exercise"),
                            "fedb.nope",
                        ),
                    ),
                ),
                reading,
            )
        }

    @Test
    fun aVariationOfNothingHereStandsOnItsOwn() =
        runTest {
            val preview =
                ready(
                    imports.read(
                        """{"format": "liora-routine", "version": 1,
                            "customExercises": [{"name": "Zercher Carry", "variationOf": "Zercher Walk",
                              "tracking": "weight_distance", "equipment": "barbell", "loadStep": "2.5 kg"}]}""",
                    ),
                )
            assertEquals(
                listOf(RoutineFileProblem(Kind.BaseNotFound, ProblemPlace(exercise = "Zercher Carry"), "Zercher Walk")),
                preview.warnings,
            )
            val result = imports.import(preview, emptyMap())
            assertEquals(1, result.newExercises)
            assertEquals(emptyList(), result.routineIds)
            val carry = custom().single()
            assertEquals(TrackingType.WeightDistance, carry.trackingType)
            assertEquals(Equipment.Barbell, carry.equipment)
            assertNull(carry.variationOf)
            assertEquals(Mass(2.5), carry.settings.loadStep)
        }

    @Test
    fun aBrokenFileSaysWhatsWrong() =
        runTest {
            val reading = imports.read("""{"format": "liora-routine", "version": 1, "routines": [{"name": "A"}]}""")
            assertEquals(
                RoutineImportReading.Invalid(
                    listOf(RoutineFileProblem(Kind.Missing, ProblemPlace(routine = "A", field = "exercises"))),
                ),
                reading,
            )
        }

    private fun ready(reading: RoutineImportReading): RoutineImportPreview =
        (reading as? RoutineImportReading.Ready)?.preview ?: fail("Not ready: $reading")

    private suspend fun custom(): List<Exercise> = exercises.observeExercises("en").first().filter { it.isCustom }

    private suspend fun exercise(id: String): Exercise = exercises.observeExercise(id, "en").first()!!

    private class FakeFiles : ExportFiles {
        val texts = mutableMapOf<String, String>()

        override suspend fun writeText(
            destination: String,
            text: String,
        ) {
            texts[destination] = text
        }

        override suspend fun readText(source: String): String = texts.getValue(source)

        override suspend fun writeArchive(
            destination: String,
            entries: suspend ArchiveWriter.() -> Unit,
        ) = error("Not used")

        override suspend fun readArchive(
            source: String,
            names: Set<String>,
            onEntry: suspend (name: String, bytes: ByteArray) -> Unit,
        ) = error("Not used")
    }

    private class TestClock(
        var now: Instant = Instant.parse("2026-10-11T16:00:00Z"),
    ) : Clock {
        override fun now() = now
    }

    private companion object {
        const val FILE = "content://downloads/upper-lower.liora.json"
        const val BENCH = "fedb.bench"
        const val LAT = "fedb.lat"
        const val LATERAL_RAISE = "liora.cable_lateral_raise"
        const val PLANK = "fedb.plank"
        const val SQUAT = "fedb.squat"
        const val WEIGHTED_PULL_UP = "fedb.weighted_pull_up"

        val PROGRAM =
            """
            {
              "format": "liora-routine",
              "version": 1,
              "weightUnit": "kg",
              "folder": { "name": "Upper / Lower", "rotation": true },
              "customExercises": [
                { "name": "Cable Y-Raise", "variationOf": "Cable Lateral Raise", "secondaryMuscles": ["traps"],
                  "note": "Thumbs up." }
              ],
              "routines": [
                {
                  "name": "Upper",
                  "notes": "Heavy day.",
                  "exercises": [
                    { "exercise": "Bench Press", "rest": "3:00", "progression": "double", "note": "Arch, feet down.",
                      "sets": [
                        { "type": "warmup", "weight": 40, "reps": 10 },
                        { "type": "warmup", "weight": 60, "reps": 5 },
                        { "count": 3, "weight": 80, "reps": "6-8", "rpe": 8 }
                      ] },
                    { "exercise": "Lat Pulldown", "superset": "A", "rest": 0, "sets": 3, "reps": "8-12" },
                    { "exercise": "cable y-raise", "superset": "A", "rest": 90, "sets": 3, "reps": "12-15" },
                    { "exercise": "Plank", "progression": "double", "sets": 2, "duration": "1:00", "weight": 10 }
                  ]
                },
                {
                  "name": "Lower",
                  "exercises": [
                    { "exercise": "fedb.squat", "sets": 3, "reps": 5 },
                    { "exercise": "Pull-Up", "sets": 3, "reps": 6, "weight": 10 }
                  ]
                }
              ]
            }
            """.trimIndent()

        val MYSTERY =
            """
            {"format": "liora-routine", "version": 1, "routines": [
              {"name": "Back", "exercises": [{"exercise": "Lat Pulldown Studio North", "sets": 3, "reps": 10}]}
            ]}
            """.trimIndent()

        val SEED =
            """
            {
              "version": 1,
              "source": "test",
              "exercises": [
                {"id":"fedb.bench","names":{"en":"Barbell Bench Press","de":"Bankdrücken (Langhantel)"},
                 "aliases":{"en":["Bench Press"],"de":["Bankdrücken"]},"tracking":"weight_reps","equipment":"barbell",
                 "category":"strength","primary":["chest"],"secondary":["triceps"],"rank":1},
                {"id":"fedb.squat","names":{"en":"Barbell Squat","de":"Kniebeuge (Langhantel)"},"tracking":"weight_reps",
                 "equipment":"barbell","category":"strength","primary":["quadriceps"],"rank":2},
                {"id":"fedb.lat","names":{"en":"Lat Pulldown","de":"Latziehen (Kabelzug)"},
                 "aliases":{"de":["Latzug"]},"tracking":"weight_reps","equipment":"cable",
                 "category":"strength","primary":["lats"],"rank":4},
                {"id":"liora.cable_lateral_raise","names":{"en":"Cable Lateral Raise","de":"Seitheben (Kabelzug)"},
                 "tracking":"weight_reps","equipment":"cable","category":"strength","primary":["shoulders"],"rank":20},
                {"id":"fedb.plank","names":{"en":"Plank","de":"Unterarmstütz"},"tracking":"duration",
                 "equipment":"bodyweight","category":"strength","primary":["abdominals"],"rank":10},
                {"id":"fedb.pull_up","names":{"en":"Pull-Up","de":"Klimmzug"},"tracking":"bodyweight_reps",
                 "equipment":"bodyweight","category":"strength","primary":["lats"],"rank":3},
                {"id":"fedb.weighted_pull_up","names":{"en":"Weighted Pull-Up","de":"Klimmzug mit Gewicht"},
                 "tracking":"weighted_bodyweight","equipment":"other","category":"strength","primary":["lats"]}
              ]
            }
            """.trimIndent()
    }
}
