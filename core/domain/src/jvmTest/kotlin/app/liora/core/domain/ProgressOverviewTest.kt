package app.liora.core.domain

import app.liora.core.model.FinishedWorkout
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.Muscle
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.WorkoutExercise
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** What the Progress tab adds up: weeks and streaks, sets per muscle, and the records board. */
class ProgressOverviewTest {
    private val now = Instant.parse("2026-10-01T18:00:00Z")

    // Thursday, 1 October 2026.
    private val today = LocalDate(2026, 10, 1)

    @Test
    fun weeksStartOnTheLocalesFirstDay() {
        assertEquals(LocalDate(2026, 9, 28), TrainingCalendar.weekStart(today, DayOfWeek.MONDAY))
        assertEquals(LocalDate(2026, 9, 27), TrainingCalendar.weekStart(today, DayOfWeek.SUNDAY))
        // A Monday is its own week's start.
        assertEquals(LocalDate(2026, 9, 28), TrainingCalendar.weekStart(LocalDate(2026, 9, 28), DayOfWeek.MONDAY))

        val weeks = TrainingCalendar.recentWeeks(today, DayOfWeek.MONDAY, count = 3)
        assertEquals(LocalDate(2026, 9, 14), weeks.first().first())
        // This week is whole, the days still to come included.
        assertEquals(LocalDate(2026, 10, 4), weeks.last().last())
        assertEquals(List(3) { 7 }, weeks.map { it.size })
    }

    @Test
    fun aStreakCountsWeeksInARow() {
        val threeWeeks =
            setOf(LocalDate(2026, 9, 8), LocalDate(2026, 9, 17), LocalDate(2026, 9, 21), LocalDate(2026, 9, 24))
        // Not trained yet this week: the streak still stands.
        assertEquals(3, TrainingCalendar.weekStreak(threeWeeks, today, DayOfWeek.MONDAY))
        assertEquals(4, TrainingCalendar.weekStreak(threeWeeks + today, today, DayOfWeek.MONDAY))
        // A week off ends it.
        assertEquals(0, TrainingCalendar.weekStreak(setOf(LocalDate(2026, 9, 17)), today, DayOfWeek.MONDAY))
        assertEquals(0, TrainingCalendar.weekStreak(emptySet(), today, DayOfWeek.MONDAY))
    }

    @Test
    fun setsPerMuscleCountsWorkingSetsOfKnownExercises() {
        val workout =
            workout(
                "w",
                daysAgo = 1,
                "bench" to listOf(set(40.0, 10, SetType.Warmup), set(80.0, 8), set(80.0, 8), set(80.0, 8)),
                "mystery" to listOf(set(10.0, 10)),
            )
        val targets = mapOf("bench" to MuscleTargets(setOf(Muscle.Chest), setOf(Muscle.Triceps, Muscle.Shoulders)))
        val sets = setsPerMuscle(listOf(workout)) { targets[it] }
        assertEquals(mapOf(Muscle.Chest to 3.0, Muscle.Triceps to 1.5, Muscle.Shoulders to 1.5), sets)
    }

    @Test
    fun theBoardShowsEachExercisesBestNewestFirst() {
        val workouts =
            listOf(
                workout("w1", daysAgo = 40, "bench" to listOf(set(100.0, 5)), "squat" to listOf(set(120.0, 5))),
                workout("w2", daysAgo = 10, "bench" to listOf(set(95.0, 5)), "squat" to listOf(set(125.0, 5))),
                workout("w3", daysAgo = 2, "bench" to listOf(set(97.5, 5)), "unknown" to listOf(set(10.0, 5))),
            )
        val board =
            RecordsBoard.of(workouts, { if (it == "unknown") null else TrackingType.WeightReps }, now)

        assertEquals(listOf("squat", "bench"), board.map { it.exerciseId })
        assertEquals("w2", board.first().best.workoutId)
        assertEquals(null, board.first().stall)
        // Bench's best is from 40 days ago and two sessions since haven't beaten it.
        assertEquals(
            "w1",
            board
                .last()
                .stall
                ?.best
                ?.workoutId,
        )
    }

    private var nextId = 0

    private fun set(
        kg: Double,
        reps: Int,
        type: SetType = SetType.Normal,
    ) = LoggedSet("s${nextId++}", type, Mass(kg), reps, completedAt = now)

    private fun workout(
        id: String,
        daysAgo: Int,
        vararg exercises: Pair<String, List<LoggedSet>>,
    ): FinishedWorkout {
        val start = now - daysAgo.days
        return FinishedWorkout(
            id = id,
            name = null,
            startedAt = start,
            endedAt = start + 1.hours,
            exercises =
                exercises.map { (exerciseId, sets) ->
                    WorkoutExercise("$id-$exerciseId", exerciseId, sets = sets.map { it.copy(completedAt = start) })
                },
        )
    }
}
