package app.liora.core.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.onDay
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Calendar days and months for history: which days were trained, and a month laid out in weeks. */
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

    private const val DAYS_PER_WEEK = 7
}
