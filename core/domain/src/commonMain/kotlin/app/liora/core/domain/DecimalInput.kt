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
