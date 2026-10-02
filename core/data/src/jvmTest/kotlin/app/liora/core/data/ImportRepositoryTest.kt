package app.liora.core.data

import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.backup.ArchiveWriter
import app.liora.core.data.backup.ExportFileException
import app.liora.core.data.backup.ExportFiles
import app.liora.core.data.body.OfflineBodyRepository
import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.importing.ImportPreview
import app.liora.core.data.importing.OfflineImportRepository
import app.liora.core.data.importing.UnknownImportException
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.seed.ExerciseCatalogSeeder
import app.liora.core.data.seed.ExerciseSeedSource
import app.liora.core.data.settings.OfflineSettingsRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.workout.LocalRestTimerRepository
import app.liora.core.data.workout.OfflineWorkoutHistoryRepository
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.domain.importing.ExerciseChoice
import app.liora.core.domain.importing.ImportSource
import app.liora.core.model.Equipment
import app.liora.core.model.LengthUnit
import app.liora.core.model.Mass
import app.liora.core.model.MeasurementType
import app.liora.core.model.Muscle
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Imports from Hevy and Strong: what a file holds is shown first, then saved with each exercise where
 * the user chose, and importing a newer export adds only what's new.
 */
class ImportRepositoryTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val clock = TestClock()
    private val database = inMemoryLioraDatabase()
    private val transactions = TransactionRunner(database)
    private val ids = IdGenerator(clock)
    private val stamper = SyncStamper(HybridLogicalClock(clock) { "phone" }, clock)
    private val files = FakeFiles()
    private val exercises = OfflineExerciseRepository(database.exerciseDao(), transactions, ids, stamper)
    private val settings = OfflineSettingsRepository(database.preferenceDao(), transactions, stamper)
    private val history =
        OfflineWorkoutHistoryRepository(
            database.workoutDao(),
            database.exerciseDao(),
            OfflineRoutineRepository(database.routineDao(), transactions, ids, stamper),
            LocalRestTimerRepository(database.localMetaDao(), clock),
            transactions,
            ids,
            stamper,
        )
    private val body = OfflineBodyRepository(database.measurementDao(), transactions, ids, stamper, clock)
    private val imports =
        OfflineImportRepository(
            files,
            exercises,
            settings,
            database.workoutDao(),
            database.exerciseDao(),
            database.measurementDao(),
            transactions,
            ids,
            stamper,
            clock,
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
    fun hevyWorkoutsWithAMachineFromTheUsersGym() =
        runTest {
            files.texts[HEVY] = HEVY_WORKOUTS
            val preview = imports.read(HEVY, zone)
            assertEquals(ImportSource.Hevy, preview.source)
            assertEquals(2, preview.workouts.size)
            assertEquals(0, preview.workoutsHere)
            assertEquals(9, preview.sets)
            assertNull(preview.assumedWeightUnit)
            assertEquals(
                mapOf(
                    "Bankdrücken (Langhantel)" to ExerciseChoice.Existing(BENCH),
                    "Klimmzug" to ExerciseChoice.Existing(WEIGHTED_PULL_UP),
                    "Latzug Studio Nord" to ExerciseChoice.New(basedOn = LAT),
                    "Rudermaschine" to ExerciseChoice.Existing(ROWING),
                    "Seitheben (Kurzhantel)" to ExerciseChoice.New(basedOn = null),
                ),
                preview.choices(),
            )

            val result = imports.import(preview, emptyMap())
            assertEquals(2, result.workouts)
            assertEquals(9, result.sets)
            assertEquals(2, result.newExercises)

            val custom =
                exercises
                    .observeExercises("de")
                    .first()
                    .filter { it.isCustom }
                    .associateBy { it.name }
            val machine = custom.getValue("Latzug Studio Nord")
            // A variation of the lat pulldown, with its muscles and equipment.
            assertEquals(LAT, machine.variationOf)
            assertEquals(setOf(Muscle.Lats), machine.primaryMuscles)
            assertEquals(Equipment.Cable, machine.equipment)
            assertEquals(TrackingType.WeightReps, machine.trackingType)
            // Nothing like it here: a new exercise of its own.
            assertNull(custom.getValue("Seitheben (Kurzhantel)").variationOf)

            val pull = history.workouts.first().first()
            assertEquals(listOf(machine.id, WEIGHTED_PULL_UP), pull.exercises.map { it.exerciseId })
            assertEquals(
                Mass(10.0),
                pull.exercises[1]
                    .sets
                    .single()
                    .weight,
            )
        }

    @Test
    fun importedWorkoutsLookLoggedHere() =
        runTest {
            files.texts[HEVY] = HEVY_WORKOUTS
            imports.import(imports.read(HEVY, zone), emptyMap())

            val push = history.workouts.first().last()
            assertEquals("Push", push.name)
            assertEquals(local(2026, 9, 12, 18, 5), push.startedAt)
            assertEquals(local(2026, 9, 12, 19, 10), push.endedAt)
            assertEquals("Felt strong", push.notes)
            val raise =
                exercises
                    .observeExercises(
                        "de",
                    ).first()
                    .single { it.isCustom && it.name.startsWith("Seitheben") }
            assertEquals(listOf(ROWING, BENCH, raise.id, BENCH), push.exercises.map { it.exerciseId })
            assertEquals(listOf(null, null, 1, 1), push.exercises.map { it.supersetGroup })
            val rowing = push.exercises[0].sets.single()
            assertEquals(SetType.Warmup, rowing.type)
            assertEquals(300.seconds, rowing.duration)
            val bench = push.exercises[1]
            assertEquals("Seat on 4", bench.notes)
            assertEquals(listOf(Mass(80.0), Mass(60.0)), bench.sets.map { it.weight })
            assertEquals(listOf(8, 12), bench.sets.map { it.reps })
            assertEquals(listOf(8.5, null), bench.sets.map { it.rpe })
            assertEquals(listOf(SetType.Normal, SetType.Drop), bench.sets.map { it.type })
            // Ticked off one after another over the workout, the last at its end.
            val ticks = push.exercises.flatMap { exercise -> exercise.sets.map { it.completedAt!! } }
            assertEquals(ticks.sorted(), ticks)
            assertTrue(ticks.first() > push.startedAt)
            assertEquals(push.endedAt, ticks.last())
        }

    @Test
    fun aNewerExportAddsOnlyWhatsNew() =
        runTest {
            val header = HEVY_WORKOUTS.lines().first()
            val pull = HEVY_WORKOUTS.lines().filter { it.startsWith("\"Pull\"") }
            files.texts[HEVY] = (listOf(header) + pull).joinToString("\n")
            imports.import(imports.read(HEVY, zone), emptyMap())
            val machine = exercises.observeExercises("de").first().single { it.isCustom }

            // Hevy writes the newest workout first.
            val again = pull.map { it.replace("Pull", "Pull again").replace("30 Sept.", "3 Okt.") }
            files.texts[HEVY] = (listOf(header) + again + pull).joinToString("\n")
            val preview = imports.read(HEVY, zone)
            assertEquals(1, preview.workoutsHere)
            assertEquals(listOf("Pull again"), preview.workouts.map { it.name })
            // The machine made last time is the same one now.
            assertEquals(
                mapOf(
                    "Klimmzug" to ExerciseChoice.Existing(WEIGHTED_PULL_UP),
                    "Latzug Studio Nord" to ExerciseChoice.Existing(machine.id),
                ),
                preview.choices(),
            )
            val result = imports.import(preview, emptyMap())
            assertEquals(1, result.workouts)
            assertEquals(0, result.newExercises)
            assertEquals(2, history.workouts.first().size)

            // Importing what was read once more adds nothing.
            assertEquals(0, imports.import(preview, emptyMap()).workouts)
            assertEquals(2, history.workouts.first().size)
            assertTrue(imports.read(HEVY, zone).isEmpty)
        }

    @Test
    fun theUsersChoicesComeFirst() =
        runTest {
            files.texts[HEVY] = HEVY_WORKOUTS
            val preview = imports.read(HEVY, zone)
            val result =
                imports.import(
                    preview,
                    mapOf(
                        // Merged into the lat pulldown after all.
                        "Latzug Studio Nord" to ExerciseChoice.Existing(LAT),
                        // A pull-up has no weight column: the reps come over, the weight doesn't.
                        "Klimmzug" to ExerciseChoice.Existing(PULL_UP),
                        // A bench press of its own.
                        "Bankdrücken (Langhantel)" to ExerciseChoice.New(basedOn = BENCH),
                    ),
                )
            assertEquals(2, result.newExercises)

            val pull = history.workouts.first().first()
            assertEquals(listOf(LAT, PULL_UP), pull.exercises.map { it.exerciseId })
            val pullUp = pull.exercises[1].sets.single()
            assertNull(pullUp.weight)
            assertEquals(6, pullUp.reps)
            val bench = exercises.observeExercises("en").first().single { it.name == "Bankdrücken (Langhantel)" }
            assertTrue(bench.isCustom)
            assertEquals(BENCH, bench.variationOf)
        }

    @Test
    fun strongWithoutUnitsInTheUnitChosen() =
        runTest {
            files.texts[STRONG] = STRONG_WORKOUTS
            val inKilograms = imports.read(STRONG, zone)
            assertEquals(ImportSource.Strong, inKilograms.source)
            // The file doesn't say: the app's own unit until the user picks another.
            assertEquals(WeightUnit.Kilogram, inKilograms.assumedWeightUnit)
            assertEquals(ExerciseChoice.Existing(BENCH), inKilograms.choices()["Bench Press (Barbell)"])

            val inPounds = imports.withWeightUnit(inKilograms, WeightUnit.Pound)
            assertEquals(WeightUnit.Pound, inPounds.assumedWeightUnit)
            imports.import(inPounds, emptyMap())

            val sets =
                history.workouts
                    .first()
                    .single()
                    .exercises
                    .single()
                    .sets
            assertEquals(
                listOf(Mass.of(135.0, WeightUnit.Pound), Mass.of(185.0, WeightUnit.Pound)),
                sets.map { it.weight },
            )
            assertEquals(listOf(SetType.Warmup, SetType.Normal), sets.map { it.type })
        }

    @Test
    fun hevyMeasurementsFillTheDaysThatHaveNone() =
        runTest {
            body.saveDay(LocalDate(2026, 5, 15), mapOf(MeasurementType.Bodyweight to 80.0), zone)
            files.texts[HEVY] = HEVY_MEASUREMENTS
            val preview = imports.read(HEVY, zone)
            assertEquals(1, preview.measurementsHere)
            assertEquals(2, preview.measurements.size)
            assertTrue(preview.exercises.isEmpty())

            assertEquals(2, imports.import(preview, emptyMap()).measurements)
            val byDay =
                body.measurements.first().associate {
                    (it.type to it.takenAt.toLocalDateTime(zone).date) to
                        it.value
                }
            assertEquals(
                mapOf(
                    (MeasurementType.Bodyweight to LocalDate(2026, 5, 14)) to 74.5,
                    (MeasurementType.Bodyweight to LocalDate(2026, 5, 15)) to 80.0,
                    (MeasurementType.Waist to LocalDate(2026, 5, 15)) to LengthUnit.Centimeter.toMeters(82.0),
                ),
                byDay,
            )
        }

    @Test
    fun filesThatArentExports() =
        runTest {
            files.texts[HEVY] = "name,value\nsomething,1"
            assertFailsWith<UnknownImportException> { imports.read(HEVY, zone) }
            assertFailsWith<ExportFileException> { imports.read("content://gone.csv", zone) }
        }

    private fun ImportPreview.choices() = exercises.associate { it.name to it.suggestion.choice }

    private fun local(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ) = LocalDateTime(year, month, day, hour, minute).toInstant(zone)

    private class FakeFiles : ExportFiles {
        val texts = mutableMapOf<String, String>()

        override suspend fun writeText(
            destination: String,
            text: String,
        ) {
            texts[destination] = text
        }

        override suspend fun readText(source: String): String =
            texts[source] ?: throw ExportFileException(IllegalStateException("No file at $source"))

        override suspend fun writeArchive(
            destination: String,
            entries: suspend ArchiveWriter.() -> Unit,
        ) = error("Imports are text")

        override suspend fun readArchive(
            source: String,
            names: Set<String>,
            onEntry: suspend (name: String, bytes: ByteArray) -> Unit,
        ) = error("Imports are text")
    }

    private class TestClock(
        var now: Instant = Instant.parse("2026-10-02T16:00:00Z"),
    ) : Clock {
        override fun now() = now
    }

    private companion object {
        const val HEVY = "content://workout_data.csv"
        const val STRONG = "content://strong.csv"
        const val BENCH = "fedb.bench"
        const val LAT = "fedb.lat"
        const val PULL_UP = "fedb.pull_up"
        const val WEIGHTED_PULL_UP = "fedb.weighted_pull_up"
        const val ROWING = "fedb.rowing"

        val SEED =
            """
            {
              "version": 1,
              "source": "test",
              "exercises": [
                {"id":"fedb.bench","names":{"en":"Barbell Bench Press","de":"Bankdrücken (Langhantel)"},
                 "aliases":{"en":["Bench Press"],"de":["Bankdrücken"]},"tracking":"weight_reps","equipment":"barbell",
                 "category":"strength","primary":["chest"],"secondary":["triceps"],"rank":1},
                {"id":"fedb.lat","names":{"en":"Lat Pulldown","de":"Latziehen (Kabelzug)"},
                 "aliases":{"de":["Latzug (Kabel)", "Latzug"]},"tracking":"weight_reps","equipment":"cable",
                 "category":"strength","primary":["lats"],"rank":4},
                {"id":"fedb.pull_up","names":{"en":"Pull-Up","de":"Klimmzug"},"tracking":"bodyweight_reps",
                 "equipment":"bodyweight","category":"strength","primary":["lats"],"rank":3},
                {"id":"fedb.weighted_pull_up","names":{"en":"Weighted Pull-Up","de":"Klimmzug mit Gewicht"},
                 "aliases":{"de":["Klimmzug (Gewichtet)"]},"tracking":"weighted_bodyweight","equipment":"other",
                 "category":"strength","primary":["lats"]},
                {"id":"fedb.rowing","names":{"en":"Rowing Machine","de":"Rudergerät"},"aliases":{"de":["Rudermaschine"]},
                 "tracking":"distance_duration","equipment":"machine","category":"cardio","primary":["middle_back"]}
              ]
            }
            """.trimIndent()

        val HEVY_WORKOUTS =
            """
            "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
            "Pull","30 Sept. 2026, 17:39","30 Sept. 2026, 19:17","","Latzug Studio Nord",,"",0,"normal",75,12,,,
            "Pull","30 Sept. 2026, 17:39","30 Sept. 2026, 19:17","","Latzug Studio Nord",,"",1,"normal",75,10,,,
            "Pull","30 Sept. 2026, 17:39","30 Sept. 2026, 19:17","","Klimmzug",,"",0,"normal",10,6,,,
            "Push","12 Sept. 2026, 18:05","12 Sept. 2026, 19:10","Felt strong","Rudermaschine",,"",0,"warmup",,,,300,
            "Push","12 Sept. 2026, 18:05","12 Sept. 2026, 19:10","Felt strong","Bankdrücken (Langhantel)",,"Seat on 4",0,"normal",80,8,,,8.5
            "Push","12 Sept. 2026, 18:05","12 Sept. 2026, 19:10","Felt strong","Bankdrücken (Langhantel)",,"Seat on 4",1,"dropset",60,12,,,
            "Push","12 Sept. 2026, 18:05","12 Sept. 2026, 19:10","Felt strong","Seitheben (Kurzhantel)",0,"",0,"normal",10,15,,,
            "Push","12 Sept. 2026, 18:05","12 Sept. 2026, 19:10","Felt strong","Bankdrücken (Langhantel)",0,"",0,"normal",70,10,,,
            "Push","12 Sept. 2026, 18:05","12 Sept. 2026, 19:10","Felt strong","Bankdrücken (Langhantel)",0,"",1,"normal",70,9,,,
            """.trimIndent()

        val STRONG_WORKOUTS =
            """
            Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE
            2026-09-30 17:39:18,"Push Day",1h 9m,"Bench Press (Barbell)",W,135,10,0,0,,,
            2026-09-30 17:39:18,"Push Day",1h 9m,"Bench Press (Barbell)",1,185,8,0,0,,,
            """.trimIndent()

        val HEVY_MEASUREMENTS =
            """
            "date","weight_kg","fat_percent","waist_cm"
            "14 Mai 2026, 00:00",74.5,,
            "15 Mai 2026, 00:00",74.2,,82
            """.trimIndent()
    }
}
