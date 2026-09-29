package app.liora.core.designsystem.util

import androidx.compose.runtime.Composable

/**
 * Locale-aware number display: "82,5" in German, "82.5" in English, trailing zeros dropped. Use it
 * for every number shown to the user instead of string templates.
 */
interface NumberFormatter {
    fun format(
        value: Double,
        maxFractionDigits: Int = 2,
    ): String

    fun format(value: Int): String = format(value.toDouble(), maxFractionDigits = 0)
}

/** A formatter for the app's current locale; recreated when the language changes. */
@Composable
expect fun rememberNumberFormatter(): NumberFormatter
