package app.liora.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * When a finished workout started and ended. Changes work in [zone]'s calendar, where its date and
 * times are shown: a new date or start moves the whole workout, a new end changes how long it lasted.
 */
data class WorkoutTimes(
    val startedAt: Instant,
    val endedAt: Instant,
) {
    val duration: Duration get() = endedAt - startedAt

    /** The same times on [date]. */
    fun onDate(
        date: LocalDate,
        zone: TimeZone,
    ): WorkoutTimes = startingAt(LocalDateTime(date, startedAt.toLocalDateTime(zone).time), zone)

    /** Starts at [time] on the same day, and lasts as long as before. */
    fun startingAt(
        time: LocalTime,
        zone: TimeZone,
    ): WorkoutTimes = startingAt(LocalDateTime(startedAt.toLocalDateTime(zone).date, time), zone)

    /** Ends at [time] on the day it started, or on the next when that time comes before the start. */
    fun endingAt(
        time: LocalTime,
        zone: TimeZone,
    ): WorkoutTimes {
        val day = startedAt.toLocalDateTime(zone).date
        val sameDay = LocalDateTime(day, time).toInstant(zone)
        // A late session that ran past midnight.
        val nextDay = LocalDateTime(day.plus(1, DateTimeUnit.DAY), time)
        return copy(endedAt = if (sameDay >= startedAt) sameDay else nextDay.toInstant(zone))
    }

    private fun startingAt(
        start: LocalDateTime,
        zone: TimeZone,
    ): WorkoutTimes {
        val instant = start.toInstant(zone)
        return WorkoutTimes(instant, instant + duration)
    }
}
