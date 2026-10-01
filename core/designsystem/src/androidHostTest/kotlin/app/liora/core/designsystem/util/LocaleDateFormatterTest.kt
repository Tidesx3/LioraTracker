package app.liora.core.designsystem.util

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** Dates in each language's own order, words and punctuation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocaleDateFormatterTest {
    private val german = LocaleDateFormatter(Locale.GERMANY, use24Hours = true)
    private val english = LocaleDateFormatter(Locale.US, use24Hours = false)
    private val day = LocalDate(2026, 9, 29)

    @Test
    fun datesFollowTheLocale() {
        assertEquals("Di., 29. Sept.", german.shortDate(day))
        assertEquals("Tue, Sep 29", english.shortDate(day))
        assertEquals("29. Sept.", german.dayAndMonth(day))
        assertEquals("Sep 29", english.dayAndMonth(day))
        assertEquals("Dienstag, 29. September 2026", german.longDate(day))
        assertEquals("Tuesday, September 29, 2026", english.longDate(day))
        assertEquals("September 2026", german.month(YearMonth(2026, Month.SEPTEMBER)))
        assertEquals("März 2026", german.month(YearMonth(2026, Month.MARCH)))
    }

    @Test
    fun timesFollowTheClockSetting() {
        assertEquals("17:47", german.time(LocalTime(17, 47)))
        // ICU versions differ in the space before "PM" (plain or narrow no-break).
        val afternoon = english.time(LocalTime(17, 47)).map { if (it.isWhitespace()) ' ' else it }.joinToString("")
        assertEquals("5:47 PM", afternoon)
    }

    @Test
    fun weeksStartWhereTheLocaleStartsThem() {
        assertEquals(DayOfWeek.MONDAY, german.firstDayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, english.firstDayOfWeek)
        assertEquals("D", german.weekdayInitial(DayOfWeek.TUESDAY))
        assertEquals("T", english.weekdayInitial(DayOfWeek.TUESDAY))
    }
}
