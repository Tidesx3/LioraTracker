package app.liora.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Instant

/**
 * A CSV file as Liora's exports write it: RFC 4180 (commas, CRLF, quotes only where needed) with dot
 * decimals, so any tool reads it the same whatever its language, and a byte order mark first, so Excel
 * reads umlauts right.
 */
internal class CsvWriter {
    private val text = StringBuilder().append(BYTE_ORDER_MARK)

    fun row(fields: List<String?>) {
        fields.forEachIndexed { index, field ->
            if (index > 0) text.append(',')
            text.append(quoted(field.orEmpty()))
        }
        text.append("\r\n")
    }

    override fun toString(): String = text.toString()

    /** A field as RFC 4180 has it: in quotes, with quotes doubled, when it holds a comma, quote or line break. */
    private fun quoted(field: String): String =
        if (field.any { it in NEEDS_QUOTES }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    companion object {
        /** Local time, as `2026-10-02 18:05`. */
        fun time(
            instant: Instant,
            zone: TimeZone,
        ): String {
            val local = instant.toLocalDateTime(zone)
            val hour = local.hour.toString().padStart(2, '0')
            val minute = local.minute.toString().padStart(2, '0')
            return "${date(local.date)} $hour:$minute"
        }

        /** A day, as `2026-10-02`. */
        fun date(day: LocalDate): String = day.toString()

        /** [value] with up to [decimals] places and a dot, without trailing zeros: 82.5, 100, 0.04. */
        fun decimal(
            value: Double,
            decimals: Int,
        ): String {
            val factor = 10.0.pow(decimals).roundToLong()
            val scaled = (abs(value) * factor).roundToLong()
            val fraction = (scaled % factor).toString().padStart(decimals, '0').trimEnd('0')
            val sign = if (value < 0 && scaled != 0L) "-" else ""
            return sign + (scaled / factor) + if (fraction.isEmpty()) "" else ".$fraction"
        }

        private val BYTE_ORDER_MARK = Char(0xFEFF)
        private val NEEDS_QUOTES = setOf(',', '"', '\n', '\r')
    }
}
