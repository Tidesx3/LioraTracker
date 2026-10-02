package app.liora.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

internal val LioraShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

/**
 * App theme. Brand colors by default; [dynamicColor] opts into the wallpaper-based palette on
 * Android 12+ (falls back to brand colors elsewhere).
 */
@Composable
fun LioraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        (if (dynamicColor) platformDynamicColorScheme(darkTheme) else null)
            ?: if (darkTheme) LioraDarkColorScheme else LioraLightColorScheme
    val lioraColors = if (darkTheme) DarkLioraColors else LightLioraColors

    CompositionLocalProvider(LocalLioraColors provides lioraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LioraTypography,
            shapes = LioraShapes,
            content = content,
        )
    }
}

/** Access to Liora-specific tokens, alongside [MaterialTheme]. */
object LioraTheme {
    val colors: LioraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLioraColors.current
}

/** The platform's dynamic (wallpaper-derived) scheme, or null where unsupported. */
@Composable
internal expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?

/** Whether the phone offers colors from the wallpaper (Android 12 and up). */
expect val dynamicColorSupported: Boolean
