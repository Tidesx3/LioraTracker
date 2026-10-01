package app.liora.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/** Moving a finished workout's date and times, in the calendar the user sees. */
class WorkoutTimesTest {
    private val berlin = TimeZone.of("Europe/Berlin")

    /** Tuesday evening, 18:05 to 19:20. */
    private val tuesday = WorkoutTimes(at(2026, 9, 29, 18, 5), at(2026, 9, 29, 19, 20))

    @Test
    fun anotherDateKeepsTheTimes() {
        val moved = tuesday.onDate(LocalDate(2026, 9, 27), berlin)
        assertEquals(WorkoutTimes(at(2026, 9, 27, 18, 5), at(2026, 9, 27, 19, 20)), moved)
    }

    @Test
    fun anotherStartKeepsTheDuration() {
        val moved = tuesday.startingAt(LocalTime(17, 30), berlin)
        assertEquals(WorkoutTimes(at(2026, 9, 29, 17, 30), at(2026, 9, 29, 18, 45)), moved)
    }

    @Test
    fun anotherEndChangesTheDuration() {
        // Forgot to finish: it really ended at 19:00.
        assertEquals(55.minutes, tuesday.endingAt(LocalTime(19, 0), berlin).duration)
        // Ending before it started means it ran past midnight.
        val late = tuesday.endingAt(LocalTime(0, 15), berlin)
        assertEquals(at(2026, 9, 30, 0, 15), late.endedAt)
        assertEquals(tuesday.startedAt, late.startedAt)
    }

    @Test
    fun movingOntoTheClockChangeKeepsTheRealDuration() {
        // Three hours after a night shift; clocks in Germany go back an hour at 3:00 on 25 October 2026.
        val night = WorkoutTimes(at(2026, 10, 24, 1, 0), at(2026, 10, 24, 4, 0))
        val moved = night.onDate(LocalDate(2026, 10, 25), berlin)
        assertEquals(at(2026, 10, 25, 1, 0), moved.startedAt)
        assertEquals(night.duration, moved.duration)
        assertEquals(at(2026, 10, 25, 3, 0), moved.endedAt)
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ) = LocalDateTime(year, month, day, hour, minute).toInstant(berlin)
}
