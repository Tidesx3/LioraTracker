package app.liora.core.domain.importing

import app.liora.core.model.DistanceUnit
import app.liora.core.model.LengthUnit
import app.liora.core.model.Mass
import app.liora.core.model.MeasurementType
import app.liora.core.model.SetType
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Hevy's and Strong's exports, in synthetic files laid out like the real ones. */
class ImportReaderTest {
    private val zone = TimeZone.of("Europe/Berlin")

    @Test
    fun hevyWorkoutsFromAGermanPhone() {
        val file = assertIs<WorkoutFile>(read(HEVY_WORKOUTS))
        assertEquals(ImportSource.Hevy, file.source)
        assertTrue(file.unitsStated)
        // In the file's order, newest first as Hevy writes them.
        val (pull, push) = file.workouts
        assertEquals("Push", push.name)
        assertEquals(local(2026, 9, 12, 18, 5), push.startedAt)
        assertEquals(local(2026, 9, 12, 19, 10), push.endedAt)
        assertEquals("Felt strong", push.notes)
        assertEquals(
            listOf("Rudermaschine", "Bankdrücken (Langhantel)", "Seitheben (Kurzhantel)", "Trizepsdrücken (Kabel)"),
            push.exercises.map { it.name },
        )

        // Cardio as a timed warm-up.
        assertEquals(listOf(ImportedSet(SetType.Warmup, duration = 300.seconds)), push.exercises[0].sets)

        val bench = push.exercises[1]
        assertEquals("Seat on 4\ngrip wide", bench.notes)
        assertEquals(
            listOf(
                ImportedSet(SetType.Warmup, Mass(40.0), 10),
                ImportedSet(SetType.Normal, Mass(80.0), 8, rpe = 8.5),
                ImportedSet(SetType.Drop, Mass(60.0), 12),
            ),
            bench.sets,
        )
        assertNull(bench.supersetGroup)

        // A superset, and a failure set with a decimal weight.
        assertEquals(listOf(1, 1), push.exercises.drop(2).map { it.supersetGroup })
        assertEquals(ImportedSet(SetType.Failure, Mass(25.5), 12), push.exercises[3].sets.single())

        // The same exercise again later in the workout is a block of its own; bodyweight sets have no weight.
        assertEquals(local(2026, 9, 30, 17, 39), pull.startedAt)
        assertEquals(listOf("Latzug Studio Nord", "Klimmzug", "Latzug Studio Nord"), pull.exercises.map { it.name })
        assertEquals(listOf(ImportedSet(reps = 10), ImportedSet(reps = 8)), pull.exercises[1].sets)
        assertEquals(1, pull.exercises[2].sets.size)
    }

    @Test
    fun hevyInPoundsAndMiles() {
        val file =
            assertIs<WorkoutFile>(
                read(
                    """
                    title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_lbs,reps,distance_miles,duration_seconds,rpe
                    Legs,"30 Sep 2026, 17:39","30 Sep 2026, 18:39",,Squat (Barbell),,,0,normal,225,5,,,
                    Legs,"30 Sep 2026, 17:39","30 Sep 2026, 18:39",,Running,,,0,normal,,,2,900,
                    """.trimIndent(),
                ),
            )
        val (squat, run) = file.workouts.single().exercises
        assertEquals(Mass.of(225.0, WeightUnit.Pound), squat.sets.single().weight)
        assertEquals(LengthUnit.Mile.toMeters(2.0), run.sets.single().distanceMeters)
        assertEquals(900.seconds, run.sets.single().duration)
    }

    @Test
    fun hevyMeasurements() {
        val file = assertIs<MeasurementFile>(read(HEVY_MEASUREMENTS))
        assertEquals(
            listOf(
                ImportedMeasurement(LocalDate(2026, 5, 14), MeasurementType.Bodyweight, 74.5),
                ImportedMeasurement(LocalDate(2026, 5, 15), MeasurementType.Bodyweight, 74.2),
                ImportedMeasurement(LocalDate(2026, 5, 15), MeasurementType.BodyFat, 18.5),
                ImportedMeasurement(
                    LocalDate(2026, 5, 15),
                    MeasurementType.BicepsLeft,
                    LengthUnit.Centimeter.toMeters(35.5),
                ),
                ImportedMeasurement(
                    LocalDate(2026, 5, 15),
                    MeasurementType.Waist,
                    LengthUnit.Centimeter.toMeters(82.0),
                ),
            ),
            file.measurements,
        )
    }

    @Test
    fun strongInItsCurrentLayout() {
        val units = Units(weight = WeightUnit.Pound, distance = DistanceUnit.Mile)
        val file = assertIs<WorkoutFile>(ImportReader.read(STRONG, units, zone))
        assertEquals(ImportSource.Strong, file.source)
        // The file doesn't say its units: they're the ones asked for.
        assertFalse(file.unitsStated)
        val workout = file.workouts.single()
        assertEquals("Push Day", workout.name)
        assertEquals(LocalDateTime(2026, 9, 30, 17, 39, 18).toInstant(zone), workout.startedAt)
        assertEquals(1.hours + 9.minutes, workout.endedAt - workout.startedAt)
        assertEquals("Long day", workout.notes)
        val (bench, run) = workout.exercises
        assertEquals("Bench Press (Barbell)", bench.name)
        assertEquals("Paused, 1s", bench.notes)
        // The rest timer row isn't a set.
        assertEquals(
            listOf(
                ImportedSet(SetType.Warmup, Mass.of(45.0, WeightUnit.Pound), 10),
                ImportedSet(SetType.Normal, Mass.of(185.0, WeightUnit.Pound), 8, rpe = 8.0),
                ImportedSet(SetType.Failure, Mass.of(135.0, WeightUnit.Pound), 12),
            ),
            bench.sets,
        )
        assertEquals(
            ImportedSet(duration = 1_200.seconds, distanceMeters = LengthUnit.Mile.toMeters(3.2)),
            run.sets.single(),
        )
    }

    @Test
    fun strongInItsOlderLayoutWithUnitsAndDecimalCommas() {
        val file = assertIs<WorkoutFile>(read(STRONG_OLD))
        assertTrue(file.unitsStated)
        val workout = file.workouts.single()
        assertEquals(45.minutes, workout.endedAt - workout.startedAt)
        assertEquals(
            listOf(Mass(100.0), Mass.of(220.5, WeightUnit.Pound)),
            workout.exercises
                .single()
                .sets
                .map { it.weight },
        )
    }

    @Test
    fun otherFilesAreNotExports() {
        assertNull(read("name,value\na,1"))
        assertNull(read(""))
    }

    private fun read(text: String) = ImportReader.read(text, Units(), zone)

    private fun local(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ) = LocalDateTime(year, month, day, hour, minute).toInstant(zone)

    private companion object {
        val HEVY_WORKOUTS =
            """
            "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
            "Pull","30 Sept. 2026, 17:39","30 Sept. 2026, 19:17","","Latzug Studio Nord",,"",0,"normal",75,12,,,
            "Pull","30 Sept. 2026, 17:39","30 Sept. 2026, 19:17","","Latzug Studio Nord",,"",1,"normal",75,10,,,
            "Pull","30 Sept. 2026, 17:39","30 Sept. 2026, 19:17","","Klimmzug",,"",0,"normal",,10,,,
            "Pull","30 Sept. 2026, 17:39","30 Sept. 2026, 19:17","","Klimmzug",,"",1,"normal",,8,,,
            "Pull","30 Sept. 2026, 17:39","30 Sept. 2026, 19:17","","Latzug Studio Nord",,"",0,"normal",60,15,,,
            "Push","12 Sept. 2026, 18:05","12 Sept. 2026, 19:10","Felt strong","Rudermaschine",,"",0,"warmup",,,,300,
            "Push","12 Sept. 2026, 18:05","12 Sept. 2026, 19:10","Felt strong","Bankdrücken (Langhantel)",,"Seat on 4
            grip wide",0,"warmup",40,10,,,
            "Push","12 Sept. 2026, 18:05","12 Sept. 2026, 19:10","Felt strong","Bankdrücken (Langhantel)",,"Seat on 4
            grip wide",1,"normal",80,8,,,8.5
            "Push","12 Sept. 2026, 18:05","12 Sept. 2026, 19:10","Felt strong","Bankdrücken (Langhantel)",,"Seat on 4
            grip wide",2,"dropset",60,12,,,
            "Push","12 Sept. 2026, 18:05","12 Sept. 2026, 19:10","Felt strong","Seitheben (Kurzhantel)",0,"",0,"normal",10,15,,,
            "Push","12 Sept. 2026, 18:05","12 Sept. 2026, 19:10","Felt strong","Trizepsdrücken (Kabel)",0,"",0,"failure",25.5,12,,,
            """.trimIndent()

        val HEVY_MEASUREMENTS =
            """
            "date","weight_kg","fat_percent","neck_cm","shoulder_cm","chest_cm","left_bicep_cm","right_bicep_cm","left_forearm_cm","right_forearm_cm","abdomen_cm","waist_cm","hips_cm","left_thigh_cm","right_thigh_cm","left_calf_cm","right_calf_cm"
            "14 Mai 2026, 00:00",74.5,,,,,,,,,,,,,,,
            "15 Mai 2026, 00:00",73.9,,,,,,,,,,,,,,,
            "15 Mai 2026, 00:00",74.2,18.5,,,,35.5,,,,,82,,,,,
            """.trimIndent()

        val STRONG =
            """
            Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE
            2026-09-30 17:39:18,"Push Day",1h 9m,"Bench Press (Barbell)",W,45,10,0,0,,"Long day",
            2026-09-30 17:39:18,"Push Day",1h 9m,"Bench Press (Barbell)",1,185,8,0,0,"Paused, 1s","Long day",8
            2026-09-30 17:39:18,"Push Day",1h 9m,"Bench Press (Barbell)",Rest Timer,0,0,0,90,,"Long day",
            2026-09-30 17:39:18,"Push Day",1h 9m,"Bench Press (Barbell)",F,135,12,0,0,,"Long day",
            2026-09-30 17:39:18,"Push Day",1h 9m,"Running (Treadmill)",1,0,0,3.2,1200,,"Long day",
            """.trimIndent()

        val STRONG_OLD =
            """
            Date;Workout Name;Exercise Name;Set Order;Weight;Weight Unit;Reps;RPE;Distance;Distance Unit;Seconds;Notes;Workout Notes;Workout Duration
            2020-01-05 10:00:00;Legs;Squat (Barbell);1;100;kg;5;;0;;0;;;45m
            2020-01-05 10:00:00;Legs;Squat (Barbell);2;220,5;lbs;5;;0;;0;;;45m
            """.trimIndent()
    }
}
