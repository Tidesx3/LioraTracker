package app.liora.core.data

import app.liora.core.data.workout.OfflineWeekProgressRepository
import app.liora.core.domain.GoalChange
import app.liora.core.domain.WeekResult
import app.liora.core.model.Mass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/** The weekly goal from this week on, and the streak of weeks that reach it, from the workouts trained. */
class WeekProgressRepositoryTest {
    private val originalZone = java.util.TimeZone.getDefault()
    private val phone = TestPhone("phone")
    private val weeks = OfflineWeekProgressRepository(phone.history, phone.settings, phone.clock)

    @BeforeTest
    fun setUp() {
        // The test clock starts on a Monday evening in Berlin.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"))
    }

    @AfterTest
    fun tearDown() {
        phone.database.close()
        java.util.TimeZone.setDefault(originalZone)
    }

    @Test
    fun aGoalHoldsFromThisWeekOnAndTheStreakCountsTheWeeksThatReachIt() =
        runTest {
            // Last week, Monday and Wednesday, before any goal.
            train()
            phone.clock.advance(2.days)
            train()
            phone.clock.advance(5.days)

            weeks.setGoal(2, DayOfWeek.MONDAY)
            val monday = weeks.progress(DayOfWeek.MONDAY).first()
            assertEquals(LocalDate(2026, 10, 5), monday.weekStart)
            assertEquals(2, monday.goal)
            assertEquals(1, monday.streak)
            assertEquals(WeekResult.Met, monday.lastWeek)
            assertEquals(
                listOf(GoalChange(LocalDate(2026, 10, 5), 2)),
                phone.settings
                    .current()
                    .weeklyGoals.changes,
            )

            train()
            assertFalse(weeks.progress(DayOfWeek.MONDAY).first().met)
            phone.clock.advance(1.days)
            train()
            val tuesday = weeks.progress(DayOfWeek.MONDAY).first()
            assertTrue(tuesday.met)
            assertEquals(2, tuesday.streak)
        }

    /** A workout now: one squat set, done. */
    private suspend fun train() {
        val workouts = phone.workouts
        workouts.startEmptyWorkout()
        workouts.editor.addExercises(listOf(TestPhone.SQUAT))
        val set =
            workouts.activeWorkout
                .first()!!
                .exercises
                .single()
                .sets
                .first()
        workouts.sets.updateSet(set.id) { copy(weight = Mass(100.0), reps = 5) }
        workouts.sets.completeSet(set.id)
        workouts.finish()
    }
}
