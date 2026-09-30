package app.liora.core.designsystem.util

import androidx.compose.runtime.Composable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth

/**
 * Locale-aware dates and times, in each language's own order and words: "Tue, Sep 29" in English,
 * "Di., 29. Sept." in German. Use it for every date shown instead of string templates.
 */
interface DateFormatter {
    /** Where the locale's week starts: Monday in Germany, Sunday in the US. */
    val firstDayOfWeek: DayOfWeek

    /** "Tue, Sep 29" / "Di., 29. Sept.", for lists grouped under a month. */
    fun shortDate(date: LocalDate): String

    /** "Tuesday, September 29, 2026" / "Dienstag, 29. September 2026". */
    fun longDate(date: LocalDate): String

    /** "September 2026". */
    fun month(month: YearMonth): String

    /** "5:47 PM" or "17:47", following the phone's 12/24-hour setting. */
    fun time(time: LocalTime): String

    /** "M" / "D": one letter for a calendar's weekday header. */
    fun weekdayInitial(day: DayOfWeek): String
}

/** A formatter for the app's current locale; recreated when the language changes. */
@Composable
expect fun rememberDateFormatter(): DateFormatter
