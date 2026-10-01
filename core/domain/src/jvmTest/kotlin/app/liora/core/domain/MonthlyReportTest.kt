package app.liora.core.domain

import app.liora.core.model.FinishedWorkout
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.Muscle
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.WorkoutExercise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** A month in numbers, against the month before, with its records, favourite exercises and muscles. */
class MonthlyReportTest {
    private val vienna = TimeZone.of("Europe/Vienna")
    private val august = YearMonth(2026, Month.AUGUST)

    private val workouts =
        listOf(
            // July: the baseline.
            workout("jul", "2026-07-27T16:00:00Z", "bench" to listOf(set(80.0, 8), set(80.0, 8), set(80.0, 8))),
            workout(
                "aug1",
                "2026-08-03T16:00:00Z",
                "bench" to listOf(set(60.0, 10, SetType.Warmup), set(80.0, 8), set(80.0, 8), set(80.0, 8)),
                "flyes" to listOf(set(20.0, 12), set(20.0, 12)),
            ),
            // A heavier bench: records.
            workout("aug2", "2026-08-10T16:00:00Z", "bench" to listOf(set(85.0, 8), set(85.0, 8), set(85.0, 8))),
            // Half past midnight in Vienna on 1 September, still 31 August in UTC: September's.
            workout("sep", "2026-08-31T22:30:00Z", "bench" to listOf(set(85.0, 8))),
        )

    private val report =
        MonthlyReports.of(
            workouts = workouts,
            month = august,
            zone = vienna,
            trackingTypeOf = { TrackingType.WeightReps },
            targetsOf = { id ->
                when (id) {
                    "bench" -> MuscleTargets(setOf(Muscle.Chest), setOf(Muscle.Triceps))
                    "flyes" -> MuscleTargets(setOf(Muscle.Chest), emptySet())
                    else -> null
                }
            },
        )

    @Test
    fun totalsAgainstTheMonthBefore() {
        assertEquals(
            MonthTotals(workouts = 2, trainingDays = 2, duration = 2.hours, volumeKg = 4_440.0, sets = 8),
            report.totals,
        )
        assertEquals(
            MonthTotals(workouts = 1, trainingDays = 1, duration = 1.hours, volumeKg = 1_920.0, sets = 3),
            report.previous,
        )
        assertEquals(setOf(LocalDate(2026, 8, 3), LocalDate(2026, 8, 10)), report.trainingDays)
    }

    @Test
    fun recordsTopExercisesAndMuscles() {
        // The heavier bench broke records; the flyes' first session is a baseline.
        assertEquals(listOf("bench"), report.records.keys.toList())
        assertEquals(true, RecordKey(RecordType.HeaviestWeight) in report.records.getValue("bench"))
        assertEquals(
            listOf(ExerciseShare("bench", 6, 3_960.0), ExerciseShare("flyes", 2, 480.0)),
            report.topExercises,
        )
        assertEquals(mapOf(Muscle.Chest to 8.0, Muscle.Triceps to 3.0), report.setsPerMuscle)
    }

    private var nextId = 0

    private fun set(
        kg: Double,
        reps: Int,
        type: SetType = SetType.Normal,
    ) = LoggedSet("s${nextId++}", type, Mass(kg), reps)

    private fun workout(
        id: String,
        start: String,
        vararg exercises: Pair<String, List<LoggedSet>>,
    ): FinishedWorkout {
        val startedAt = Instant.parse(start)
        return FinishedWorkout(
            id = id,
            name = null,
            startedAt = startedAt,
            endedAt = startedAt + 1.hours,
            exercises =
                exercises.map { (exerciseId, sets) ->
                    WorkoutExercise("$id-$exerciseId", exerciseId, sets = sets.map { it.copy(completedAt = startedAt) })
                },
        )
    }
}
