package app.liora.core.designsystem.util

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toJavaYearMonth
import kotlinx.datetime.toKotlinDayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
actual fun rememberDateFormatter(): DateFormatter {
    val locale = LocalConfiguration.current.locales[0]
    val use24Hours = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, use24Hours) { LocaleDateFormatter(locale, use24Hours) }
}

/** Patterns come from the locale's own skeletons, so each language keeps its order and punctuation. */
internal class LocaleDateFormatter(
    private val locale: Locale,
    use24Hours: Boolean,
) : DateFormatter {
    private val short = pattern("EEEMMMd")
    private val dayMonth = pattern("MMMd")
    private val long = pattern("EEEEyMMMMd")
    private val monthYear = pattern("yMMMM")
    private val clock = pattern(if (use24Hours) "Hm" else "hma")

    override val firstDayOfWeek: DayOfWeek = WeekFields.of(locale).firstDayOfWeek.toKotlinDayOfWeek()

    override fun shortDate(date: LocalDate): String = short.format(date.toJavaLocalDate())

    override fun dayAndMonth(date: LocalDate): String = dayMonth.format(date.toJavaLocalDate())

    override fun longDate(date: LocalDate): String = long.format(date.toJavaLocalDate())

    override fun month(month: YearMonth): String = monthYear.format(month.toJavaYearMonth())

    override fun time(time: LocalTime): String = clock.format(time.toJavaLocalTime())

    override fun weekdayInitial(day: DayOfWeek): String =
        day.toJavaDayOfWeek().getDisplayName(TextStyle.NARROW_STANDALONE, locale)

    private fun pattern(skeleton: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}
