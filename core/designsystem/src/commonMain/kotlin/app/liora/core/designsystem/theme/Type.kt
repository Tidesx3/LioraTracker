package app.liora.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.liora.core.designsystem.resources.Res
import app.liora.core.designsystem.resources.sora
import org.jetbrains.compose.resources.Font

private val Base = Typography()

/**
 * Liora's type scale in Sora (SIL Open Font License, see `files/licenses`): Material 3's sizes with
 * firmer weights for headings. Sora's digits are proportional by default, so numbers that change
 * live use [tabularNumbers].
 */
@Composable
internal fun lioraTypography(): Typography {
    // The font is variable: one file, each weight picked on its axis.
    val sora =
        FontFamily(
            listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
                Font(
                    Res.font.sora,
                    weight,
                    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
                )
            },
        )

    fun TextStyle.text(weight: FontWeight? = fontWeight) = copy(fontFamily = sora, fontWeight = weight)
    return Typography(
        displayLarge = Base.displayLarge.text(FontWeight.SemiBold),
        displayMedium = Base.displayMedium.text(FontWeight.SemiBold),
        displaySmall = Base.displaySmall.text(FontWeight.SemiBold),
        headlineLarge = Base.headlineLarge.text(FontWeight.SemiBold),
        headlineMedium = Base.headlineMedium.text(FontWeight.SemiBold),
        headlineSmall = Base.headlineSmall.text(FontWeight.SemiBold),
        titleLarge = Base.titleLarge.copy(letterSpacing = (-0.2).sp).text(FontWeight.SemiBold),
        titleMedium = Base.titleMedium.text(FontWeight.SemiBold),
        titleSmall = Base.titleSmall.text(FontWeight.SemiBold),
        bodyLarge = Base.bodyLarge.text(),
        bodyMedium = Base.bodyMedium.text(),
        bodySmall = Base.bodySmall.text(),
        labelLarge = Base.labelLarge.text(FontWeight.SemiBold),
        labelMedium = Base.labelMedium.text(FontWeight.Medium),
        labelSmall = Base.labelSmall.text(FontWeight.Medium),
    )
}

/** Fixed-width digits so weights, reps and timers don't jitter while they change. */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")
