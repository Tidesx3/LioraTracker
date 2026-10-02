package app.liora.core.domain.importing

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Dates and times as other apps write them into their exports, in local time:
 *
 * - ISO, as Strong writes them: `2026-09-30 17:39:18`, `2026-09-30T17:39`.
 * - With the month's name, as Hevy writes them in the phone's language: `30 Sept. 2026, 17:39`,
 *   `14 Mai 2026, 00:00`, `30 Sep 2026, 5:39 PM`, `Sep 30, 2026, 17:39`. English and German names,
 *   full or abbreviated, with or without a dot.
 * - German numbers: `30.09.2026, 17:39`.
 *
 * A date without a time is midnight.
 */
internal object ExportDates {
    fun parse(text: String): LocalDateTime? {
        val trimmed = text.trim()
        val (match, date) =
            DATE_FORMATS.firstNotNullOfOrNull { (pattern, read) ->
                pattern.matchAt(trimmed, 0)?.let { match -> read(match.groupValues)?.let { match to it } }
            } ?: return null
        val clock =
            trimmed
                .substring(match.range.last + 1)
                .trimStart(',', 'T', ' ')
                .trim()
        return if (clock.isEmpty()) date.atTime(0, 0) else time(clock)?.let { date.atTime(it) }
    }

    /** A time of day: `17:39`, `17:39:18`, `5:39 PM`. */
    private fun time(text: String): LocalTime? {
        val match = TIME.matchEntire(text) ?: return null
        val (hours, minutes, seconds) = match.destructured
        val half = match.groupValues.last()
        val hour =
            when (half.lowercase()) {
                "am" -> hours.toInt() % HALF_DAY
                "pm" -> hours.toInt() % HALF_DAY + HALF_DAY
                else -> hours.toInt()
            }
        return runCatching { LocalTime(hour, minutes.toInt(), seconds.ifEmpty { "0" }.toInt()) }.getOrNull()
    }

    /** A month by its English or German name, full or abbreviated: `Sept.`, `Mär`, `März`, `Oct`. */
    fun month(name: String): Int? {
        val folded =
            name
                .lowercase()
                .trimEnd('.')
                .replace('ä', 'a')
        if (folded.length < MONTH_PREFIX || !folded.all { it.isLetter() }) return null
        return if (folded == "mrz") MARCH else MONTHS[folded.take(MONTH_PREFIX)]
    }

    /** A length as Strong writes a workout's: `1h 9m`, `45m`, `1h`, `30s`. A bare number is seconds. */
    fun duration(text: String): Duration? {
        val trimmed = text.trim()
        trimmed.toLongOrNull()?.let { return it.seconds }
        val parts = DURATION_PART.findAll(trimmed).toList()
        if (parts.isEmpty()) return null
        return parts.fold(Duration.ZERO) { total, part ->
            val amount = part.groupValues[1].toLong()
            total +
                when (part.groupValues[2].lowercase()) {
                    "h" -> amount.hours
                    "m", "min" -> amount.minutes
                    else -> amount.seconds
                }
        }
    }

    private fun date(
        year: String,
        month: Int?,
        day: String,
    ): LocalDate? {
        val y = year.toIntOrNull()
        val d = day.toIntOrNull()
        if (y == null || month == null || d == null) return null
        // An impossible date, such as 30 February, is none.
        return runCatching { LocalDate(y, month, d) }.getOrNull()
    }

    /** How each way of writing a date starts, and how to read its parts: year, month and day. */
    private val DATE_FORMATS: List<Pair<Regex, (List<String>) -> LocalDate?>> =
        listOf(
            Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""") to { parts -> date(parts[1], parts[2].toIntOrNull(), parts[3]) },
            Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""") to { parts -> date(parts[3], parts[2].toIntOrNull(), parts[1]) },
            Regex("""(\d{1,2})\.?\s+(\p{L}+\.?)\s+(\d{4})""") to { parts -> date(parts[3], month(parts[2]), parts[1]) },
            Regex("""(\p{L}+\.?)\s+(\d{1,2}),?\s+(\d{4})""") to { parts -> date(parts[3], month(parts[1]), parts[2]) },
        )
    private val TIME = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?\s*([AaPp][Mm])?(?:Z|[+-]\d{2}:?\d{2})?""")
    private val DURATION_PART = Regex("""(\d+)\s*(h|min|m|s)\b""", RegexOption.IGNORE_CASE)

    private val MONTHS =
        mapOf(
            "jan" to 1,
            "feb" to 2,
            "mar" to 3,
            "apr" to 4,
            "may" to 5,
            "mai" to 5,
            "jun" to 6,
            "jul" to 7,
            "aug" to 8,
            "sep" to 9,
            "oct" to 10,
            "okt" to 10,
            "nov" to 11,
            "dec" to 12,
            "dez" to 12,
        )

    private const val MONTH_PREFIX = 3
    private const val MARCH = 3
    private const val HALF_DAY = 12
}
