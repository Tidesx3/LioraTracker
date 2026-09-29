package app.liora.core.designsystem.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import java.util.Locale

@Composable
actual fun rememberNumberFormatter(): NumberFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { LocaleNumberFormatter(locale) }
}

internal class LocaleNumberFormatter(
    private val locale: Locale,
) : NumberFormatter {
    override fun format(
        value: Double,
        maxFractionDigits: Int,
    ): String =
        NumberFormat
            .getNumberInstance(locale)
            .apply {
                minimumFractionDigits = 0
                maximumFractionDigits = maxFractionDigits
            }.format(value)
}
