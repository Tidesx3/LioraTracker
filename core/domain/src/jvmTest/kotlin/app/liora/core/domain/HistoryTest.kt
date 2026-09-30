package app.liora.core.domain

import app.liora.core.model.FinishedWorkout
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineSet
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.WorkoutExercise
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** What history works out from finished workouts: records as they were set, and the calendar. */
class HistoryTest {
    @Test
    fun recordsAreMeasuredAgainstEverythingBefore() {
        val workouts =
            listOf(
                // Newest first, as history lists them; records still go by date.
                workout("w3", day = 3, bench("c1", 85.0, 5, at = 5), bench("c2", 85.0, 5, at = 6)),
                workout("w1", day = 1, bench("a1", 80.0, 8, at = 1), bench("a2", 82.5, 8, at = 2)),
                workout("w2", day = 2, bench("b1", 60.0, 10, SetType.Warmup, at = 3), bench("b2", 82.5, 8, at = 4)),
            )
        val records = HistoryRecords.of(workouts) { TrackingType.WeightReps }

        // The first session is the baseline, even where a later set beat an earlier one.
        assertNull(records["a2"])
        // Equal to the best so far is not a record, and warm-ups never are.
        assertNull(records["b1"])
        assertNull(records["b2"])
        // Heavier than ever for fewer reps: heaviest weight and the 1- to 5-rep maxes, but not a better
        // estimated max or set volume than 82.5 kg for 8.
        assertEquals(
            setOf(RecordKey(RecordType.HeaviestWeight)) + (1..5).map { RecordKey(RecordType.RepMax, it) },
            records.getValue("c1"),
        )
        assertNull(records["c2"])
    }

    @Test
    fun recordsMatchWhatTheLoggerAwardedOnTheDay() {
        val history = listOf(bench("h1", 80.0, 8, at = 1), bench("h2", 80.0, 8, at = 2))
        val today =
            listOf(bench("t1", 82.5, 8, at = 11), bench("t2", 82.5, 9, at = 12), bench("t3", 90.0, 3, at = 13))
        val onTheDay = SessionRecords.of(TrackingType.WeightReps, history, today)
        val later =
            HistoryRecords.of(
                listOf(
                    workout("then", day = 1, *history.toTypedArray()),
                    workout("now", day = 2, *today.toTypedArray()),
                ),
            ) { TrackingType.WeightReps }
        assertEquals(onTheDay, later)
    }

    @Test
    fun aWeekStartsOnTheLocalesFirstDay() {
        // September 2026 starts on a Tuesday.
        val september = YearMonth(2026, Month.SEPTEMBER)
        val mondays = TrainingCalendar.weeks(september, DayOfWeek.MONDAY)
        assertEquals(listOf(null, LocalDate(2026, 9, 1)), mondays.first().take(2))
        // The last week: Monday the 28th to Wednesday the 30th, then nothing.
        assertEquals(
            listOf(LocalDate(2026, 9, 28), LocalDate(2026, 9, 30), null),
            mondays.last().let {
                listOf(it[0], it[2], it[3])
            },
        )
        assertEquals(5, mondays.size)

        val sundays = TrainingCalendar.weeks(september, DayOfWeek.SUNDAY)
        assertEquals(listOf(null, null, LocalDate(2026, 9, 1)), sundays.first().take(3))
        assertEquals(List(5) { 7 }, sundays.map { it.size })
        assertEquals(
            listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY),
            TrainingCalendar.weekdays(DayOfWeek.SUNDAY).let { listOf(it[0], it[1], it[6]) },
        )
    }

    @Test
    fun aLateWorkoutCountsForThatEvening() {
        // 22:30 UTC is still the 29th in New York but already the 30th in Vienna.
        val lateEvening = Instant.parse("2026-09-29T22:30:00Z")
        assertEquals(LocalDate(2026, 9, 29), TrainingCalendar.dayOf(lateEvening, TimeZone.of("America/New_York")))
        assertEquals(LocalDate(2026, 9, 30), TrainingCalendar.dayOf(lateEvening, TimeZone.of("Europe/Vienna")))
    }

    @Test
    fun aWorkoutBecomesARoutineWithWhatWasDone() {
        val done =
            listOf(
                WorkoutExercise(
                    id = "we1",
                    exerciseId = "bench",
                    restSeconds = 120,
                    sets =
                        listOf(
                            bench("s1", 60.0, 10, SetType.Warmup, at = 1),
                            bench("s2", 80.0, 8, at = 2).copy(targetReps = RepRange(8, 12)),
                        ),
                ),
                // Nothing logged: not part of the routine.
                WorkoutExercise(id = "we2", exerciseId = "dips", sets = listOf(LoggedSet("s3"))),
            )
        var next = 0
        val routine = RoutineUpdate.fromExercises(Routine(id = "r", name = "Push"), done) { "id-${next++}" }

        assertEquals(listOf("bench"), routine.exercises.map { it.exerciseId })
        assertEquals(120, routine.exercises.single().restSeconds)
        assertEquals(
            listOf(
                RoutineSet("id-1", SetType.Warmup, Mass(60.0), RepRange(10)),
                // A rep range stays the goal.
                RoutineSet("id-2", SetType.Normal, Mass(80.0), RepRange(8, 12)),
            ),
            routine.exercises.single().sets,
        )
    }

    private fun workout(
        id: String,
        day: Int,
        vararg sets: LoggedSet,
    ): FinishedWorkout {
        val start = Instant.parse("2026-09-0${day}T17:00:00Z")
        return FinishedWorkout(
            id = id,
            name = null,
            startedAt = start,
            endedAt = start + 1.hours,
            exercises = listOf(WorkoutExercise(id = "$id-bench", exerciseId = "bench", sets = sets.toList())),
        )
    }

    private fun bench(
        id: String,
        kg: Double,
        reps: Int,
        type: SetType = SetType.Normal,
        at: Long,
    ) = LoggedSet(id, type, Mass(kg), reps, completedAt = Instant.fromEpochSeconds(at))
}
