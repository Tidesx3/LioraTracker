package app.liora.core.domain

/**
 * Parses a number typed by the user. Accepts both decimal separators ("82,5" and "82.5"), since
 * people switch keyboards and locales; rejects anything ambiguous like "1.234,5".
 */
fun parseDecimalInput(text: String): Double? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty() || normalized.count { it == '.' } > 1) return null
    if (!normalized.all { it.isDigit() || it == '.' }) return null
    return normalized.toDoubleOrNull()
}

/**
 * Time typed like on a kitchen timer: digits fill from the right, the last two are seconds. "130" is
 * 1:30, "45" is 0:45, "300" is 3:00. Needs only a number keyboard and is never ambiguous. Seconds
 * above 59 roll over ("90" is 1:30).
 */
fun parseClockDigits(text: String): Int? {
    val digits = text.filter { it.isDigit() }.trimStart('0')
    if (digits.isEmpty()) return if (text.any { it.isDigit() }) 0 else null
    if (digits.length > MAX_CLOCK_DIGITS) return null
    val seconds = digits.takeLast(2).toInt()
    val minutes = digits.dropLast(2).ifEmpty { "0" }.toInt()
    return minutes * SECONDS_PER_MINUTE + seconds
}

/** The digits [parseClockDigits] reads back as [seconds], e.g. 90 → "130". */
fun clockDigits(seconds: Int): String {
    val minutes = seconds / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return if (minutes == 0) rest.toString() else "$minutes${rest.toString().padStart(2, '0')}"
}

/** Clock digits as they're being typed, shown as m:ss before they roll over ("90" shows 0:90 until the next digit). */
fun clockDigitsText(digits: String): String {
    if (digits.isEmpty()) return ""
    val padded = digits.padStart(MIN_CLOCK_TEXT_DIGITS, '0')
    return padded.dropLast(2).trimStart('0').ifEmpty { "0" } + ":" + padded.takeLast(2)
}

private const val MAX_CLOCK_DIGITS = 5
private const val SECONDS_PER_MINUTE = 60

/** "5" shows as 0:05: at least one minute digit and two second digits. */
private const val MIN_CLOCK_TEXT_DIGITS = 3
