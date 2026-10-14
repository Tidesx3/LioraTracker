package app.liora.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Weekly goals: weeks in a row that reach them, rest weeks earned and used, and goals that change. */
class WeekStreaksTest {
    /** A Friday; weeks start on Mondays. */
    private val today = LocalDate(2026, 10, 16)
    private val thisWeek = LocalDate(2026, 10, 12)
    private val fourDays = WeeklyGoals().withGoal(4, LocalDate(2026, 1, 5))

    @Test
    fun weeksThatReachTheGoalMakeTheStreak() {
        val week = progress(fourDays, 4, 4, 5)
        assertEquals(3, week.streak)
        // This week isn't over: short of the goal so far, it doesn't end the streak.
        assertEquals(0, week.done)
        assertFalse(week.met)
        assertEquals(WeekResult.Met, week.lastWeek)

        // Once reached, it counts.
        val reached = progress(fourDays, 4, 4, 5, thisWeek = 4)
        assertEquals(4, reached.streak)
        assertTrue(reached.met)
    }

    @Test
    fun aWeekShortOfTheGoalEndsAStreakWithoutARestWeek() {
        val week = progress(fourDays, 4, 4, 3, 4)
        assertEquals(1, week.streak)
        assertEquals(0, week.restWeeks)
        assertEquals(WeekResult.Missed, week.weeks[thisWeek.minus(14, DateTimeUnit.DAY)])
    }

    @Test
    fun everySixWeeksReachedEarnARestWeekThatKeepsTheStreak() {
        // Six weeks reached, one short: the rest week keeps the streak, which doesn't count it.
        val rested = progress(fourDays, 4, 4, 4, 4, 4, 4, 2)
        assertEquals(6, rested.streak)
        assertEquals(0, rested.restWeeks)
        assertEquals(WeekResult.Rested, rested.lastWeek)

        // Twelve reached hold two; a third waits for one to be used.
        val held = progress(fourDays, *IntArray(18) { 4 })
        assertEquals(18, held.streak)
        assertEquals(WeekStreaks.MAX_REST_WEEKS, held.restWeeks)
    }

    @Test
    fun theWeekThatEarnsARestWeekSaysSo() {
        val sixth = progress(fourDays, 4, 4, 4, 4, 4, thisWeek = 4)
        assertTrue(sixth.earnedRestWeek)
        assertEquals(1, sixth.restWeeks)
        assertFalse(progress(fourDays, 4, 4, 4, 4, thisWeek = 4).earnedRestWeek)
    }

    @Test
    fun aGoalChangedLaterLeavesTheWeeksBeforeAlone() {
        // Two days a week were enough until this week; four from now on.
        val goals = WeeklyGoals().withGoal(2, LocalDate(2026, 1, 5)).withGoal(4, thisWeek)
        val week = progress(goals, 2, 2, 2, thisWeek = 2)
        assertEquals(3, week.streak)
        assertEquals(4, week.goal)
        assertFalse(week.met)
    }

    @Test
    fun beforeAnyGoalAnyWeekWithTrainingCounts() {
        val week = progress(WeeklyGoals(), 1, 2, 1, thisWeek = 1)
        assertFalse(week.goalSet)
        assertEquals(WeeklyGoals.ANY, week.goal)
        assertEquals(4, week.streak)
    }

    @Test
    fun twoWorkoutsOnADayAreOneDay() {
        val day = thisWeek.plus(1, DateTimeUnit.DAY)
        val week = WeekStreaks.of(setOf(day, day), fourDays, today, DayOfWeek.MONDAY)
        assertEquals(1, week.done)
    }

    @Test
    fun withoutTrainingThereIsNoStreak() {
        val week = WeekStreaks.of(emptySet(), fourDays, today, DayOfWeek.MONDAY)
        assertEquals(0, week.streak)
        assertNull(week.lastWeek)
    }

    @Test
    fun choosingAGoalTwiceInAWeekKeepsTheLast() {
        val goals = WeeklyGoals().withGoal(3, thisWeek).withGoal(5, thisWeek)
        assertEquals(listOf(GoalChange(thisWeek, 5)), goals.changes)
        assertEquals(7, WeeklyGoals().withGoal(9, thisWeek).current)
    }

    /**
     * The weeks before this one, oldest first, with [daysPerWeek] training days each, and [thisWeek] days so
     * far this week (Monday on).
     */
    private fun progress(
        goals: WeeklyGoals,
        vararg daysPerWeek: Int,
        thisWeek: Int = 0,
    ): WeekProgress {
        val days =
            daysPerWeek.withIndex().flatMap { (index, count) ->
                val start = this.thisWeek.minus((daysPerWeek.size - index) * 7, DateTimeUnit.DAY)
                List(count) { start.plus(it, DateTimeUnit.DAY) }
            } + List(thisWeek) { this.thisWeek.plus(it, DateTimeUnit.DAY) }
        return WeekStreaks.of(days.toSet(), goals, today, DayOfWeek.MONDAY)
    }
}
