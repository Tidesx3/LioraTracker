package app.liora.core.data.importing

import app.liora.core.model.LengthUnit
import app.liora.core.model.RepRange
import app.liora.core.model.WeightUnit
import kotlin.math.roundToInt

// The values of a routine file in the forms people and AIs write them. Each reader takes the text as the
// file has it (a JSON number's too) and returns null for anything it can't read.

/** A number and the unit written after it, if any: "80 kg", "1.5 min", "5km", "82,5". */
internal data class Quantity(
    val value: Double,
    val unit: String?,
)

internal fun quantity(text: String): Quantity? {
    val match = QUANTITY.matchEntire(text.trim()) ?: return null
    val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    return Quantity(value, match.groupValues[2].lowercase().ifEmpty { null })
}

/** The unit a weight is written in, by its abbreviation or name. */
internal fun weightUnit(name: String?): WeightUnit? =
    when (name?.trim()?.lowercase()) {
        "kg", "kgs", "kilo", "kilos", "kilogram", "kilograms" -> WeightUnit.Kilogram
        "lb", "lbs", "pound", "pounds" -> WeightUnit.Pound
        else -> null
    }

/** Reps: a number such as "8", or a range such as "8-12", "8–12" or "8 to 12". */
internal fun repRange(text: String): RepRange? {
    val trimmed = text.trim()
    trimmed.toIntOrNull()?.let { reps -> return reps.takeIf { it in REPS }?.let(::RepRange) }
    val match = REP_RANGE.matchEntire(trimmed) ?: return null
    val (min, max) = match.destructured
    return RepRange.of(min.toIntOrNull(), max.toIntOrNull())?.takeIf { it.min in REPS && it.max in REPS }
}

/**
 * A time in whole seconds: seconds as a number, a clock time ("1:30", "1:00:00"), or with its unit ("90s",
 * "2 min").
 */
internal fun seconds(text: String): Int? {
    val trimmed = text.trim()
    val clock = CLOCK.matchEntire(trimmed)
    return if (clock != null) clockSeconds(clock) else quantitySeconds(trimmed)
}

private fun clockSeconds(clock: MatchResult): Int? {
    val parts =
        clock.groupValues
            .drop(1)
            .filter { it.isNotEmpty() }
            .map { it.toInt() }
    // The first part is free; the ones after it are minutes and seconds of 0–59.
    return if (parts.drop(1).any { it >= SIXTY }) null else parts.fold(0) { total, part -> total * SIXTY + part }
}

private fun quantitySeconds(text: String): Int? {
    val (value, unit) = quantity(text) ?: return null
    val factor = SECONDS_PER[unit ?: "s"] ?: return null
    return (value * factor).roundToInt()
}

/** A distance in metres: metres as a number, or with its unit ("400 m", "5 km", "1 mi", "50 yd"). */
internal fun meters(text: String): Double? {
    val (value, unit) = quantity(text) ?: return null
    val length = LENGTHS[unit ?: "m"] ?: return null
    return length.toMeters(value)
}

/** An RPE from 1 to 10 in half points. */
internal fun rpe(text: String): Double? {
    val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    val halves = value * 2
    return value.takeIf { it in RPE && halves == halves.roundToInt().toDouble() }
}

private val QUANTITY = Regex("""(\d+(?:[.,]\d+)?)\s*([a-zA-Z]*)\.?""")
private val REP_RANGE = Regex("""(\d+)\s*(?:-|–|—|to)\s*(\d+)""", RegexOption.IGNORE_CASE)
private val CLOCK = Regex("""(\d+):(\d{2})(?::(\d{2}))?""")
private const val SIXTY = 60
private val REPS = 1..999
private val RPE = 1.0..10.0

private val SECONDS_PER =
    mapOf(
        "s" to 1,
        "sec" to 1,
        "secs" to 1,
        "second" to 1,
        "seconds" to 1,
        "m" to 60,
        "min" to 60,
        "mins" to 60,
        "minute" to 60,
        "minutes" to 60,
        "h" to 3_600,
        "hr" to 3_600,
        "hrs" to 3_600,
        "hour" to 3_600,
        "hours" to 3_600,
    )

private val LENGTHS =
    mapOf(
        "m" to LengthUnit.Meter,
        "meter" to LengthUnit.Meter,
        "meters" to LengthUnit.Meter,
        "metre" to LengthUnit.Meter,
        "metres" to LengthUnit.Meter,
        "km" to LengthUnit.Kilometer,
        "kilometer" to LengthUnit.Kilometer,
        "kilometers" to LengthUnit.Kilometer,
        "kilometre" to LengthUnit.Kilometer,
        "kilometres" to LengthUnit.Kilometer,
        "mi" to LengthUnit.Mile,
        "mile" to LengthUnit.Mile,
        "miles" to LengthUnit.Mile,
        "yd" to LengthUnit.Yard,
        "yard" to LengthUnit.Yard,
        "yards" to LengthUnit.Yard,
    )
