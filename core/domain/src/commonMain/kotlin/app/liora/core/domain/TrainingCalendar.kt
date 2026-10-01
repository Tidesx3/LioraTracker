package app.liora.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.onDay
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Calendar days, weeks and months for history and progress: which days were trained, a month laid out
 * in weeks, the recent weeks and how many of them in a row had training.
 */
object TrainingCalendar {
    /** The day [instant] falls on where the user is: a workout at 23:30 counts for that evening. */
    fun dayOf(
        instant: Instant,
        zone: TimeZone,
    ): LocalDate = instant.toLocalDateTime(zone).date

    /**
     * [month] as rows of seven days, the first row starting on [firstDayOfWeek]. Days of the months
     * around it are null, so a grid shows only this month's dates.
     */
    fun weeks(
        month: YearMonth,
        firstDayOfWeek: DayOfWeek,
    ): List<List<LocalDate?>> {
        val lead = (month.firstDay.dayOfWeek.ordinal - firstDayOfWeek.ordinal + DAYS_PER_WEEK) % DAYS_PER_WEEK
        val cells = List(lead) { null } + (1..month.numberOfDays).map { month.onDay(it) }
        return cells.chunked(DAYS_PER_WEEK) { week -> week + List(DAYS_PER_WEEK - week.size) { null } }
    }

    /** The seven weekdays in the order a week starting on [firstDayOfWeek] shows them. */
    fun weekdays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> =
        List(DAYS_PER_WEEK) { DayOfWeek.entries[(firstDayOfWeek.ordinal + it) % DAYS_PER_WEEK] }

    /** The first day of [date]'s week. */
    fun weekStart(
        date: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): LocalDate = date.minus((date.dayOfWeek.ordinal - firstDayOfWeek.ordinal + DAYS_PER_WEEK) % DAYS_PER_WEEK, DAY)

    /**
     * The last [count] weeks up to [today]'s, oldest first, seven days each: the consistency grid.
     * Days after today are included, so every week is whole.
     */
    fun recentWeeks(
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
        count: Int,
    ): List<List<LocalDate>> {
        val first = weekStart(today, firstDayOfWeek).minus((count - 1) * DAYS_PER_WEEK, DAY)
        return List(count) { week -> List(DAYS_PER_WEEK) { day -> first.plus(week * DAYS_PER_WEEK + day, DAY) } }
    }

    /**
     * Weeks in a row with a workout, counting back from [today]'s. Lifting runs in weeks, not days: rest
     * days don't break a streak, a week without training does. The current week adds to the streak once
     * trained, but doesn't end it before it's over.
     */
    fun weekStreak(
        trainingDays: Set<LocalDate>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Int {
        val trained = trainingDays.map { weekStart(it, firstDayOfWeek) }.toSet()
        var week = weekStart(today, firstDayOfWeek)
        if (week !in trained) week = week.minus(DAYS_PER_WEEK, DAY)
        var streak = 0
        while (week in trained) {
            streak++
            week = week.minus(DAYS_PER_WEEK, DAY)
        }
        return streak
    }

    private const val DAYS_PER_WEEK = 7
    private val DAY = DateTimeUnit.DAY
}
