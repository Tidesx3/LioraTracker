package app.liora.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** Material 3 type scale with firmer weights for headings; body text stays at the defaults. */
internal val LioraTypography =
    Typography(
        displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.SemiBold),
        displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.SemiBold),
        displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = Base.bodyLarge,
        bodyMedium = Base.bodyMedium,
        bodySmall = Base.bodySmall,
        labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Medium),
    )

/** Fixed-width digits so weights, reps and timers don't jitter while they change. */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")
