package app.liora.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.theme.LioraGlass
import app.liora.core.designsystem.theme.LocalLioraGlass
import app.liora.core.designsystem.theme.lioraGlass

/** Soft, generous corners: cards float as rounded tiles, bars and buttons as pills. */
internal val LioraShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(22.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )

/**
 * App theme. Brand colors by default; [dynamicColor] opts into the wallpaper-based palette on
 * Android 12+ (falls back to brand colors elsewhere). [glass] turns on Liquid Glass ([LioraGlass]).
 */
@Composable
fun LioraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    glass: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dynamicScheme = if (dynamicColor) platformDynamicColorScheme(darkTheme) else null
    val colorScheme = dynamicScheme ?: if (darkTheme) LioraDarkColorScheme else LioraLightColorScheme
    val lioraGlass =
        remember(glass, darkTheme, colorScheme) {
            lioraGlass(enabled = glass, dark = darkTheme, dynamic = dynamicScheme != null, colors = colorScheme)
        }

    CompositionLocalProvider(
        LocalLioraColors provides if (darkTheme) DarkLioraColors else LightLioraColors,
        LocalLioraGlass provides lioraGlass,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = lioraTypography(),
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

    /** Liquid Glass, or the solid look when it's off ([LioraGlass.enabled]). */
    val glass: LioraGlass
        @Composable
        @ReadOnlyComposable
        get() = LocalLioraGlass.current
}

/** The platform's dynamic (wallpaper-derived) scheme, or null where unsupported. */
@Composable
internal expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?

/** Whether the phone offers colors from the wallpaper (Android 12 and up). */
expect val dynamicColorSupported: Boolean
