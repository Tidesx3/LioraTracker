package app.liora.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Liquid Glass: the navigation floats on glass that blurs, bends and brightens what's beneath it, over
 * soft glows of the accent (the aurora), and cards are see-through panes catching the light at their
 * rim. Settings › Appearance turns it off for the solid look; then [enabled] is false and every part
 * draws as before.
 */
@Immutable
data class LioraGlass(
    val enabled: Boolean,
    val dark: Boolean,
    /** The screen beneath the glows. */
    val base: Color,
    /** The glows behind every screen: from the top start, the end and the bottom start. */
    val aurora: Triple<Color, Color, Color>,
    /** Laid over the content glass bends, so what's on it reads. */
    val tint: Color,
    /** Glass with nothing to bend (Android before 12, or nothing beneath): nearly solid. */
    val solidTint: Color,
    /** The accent's glass, for what's live: the workout in progress, the rest timer. */
    val accentTint: Color,
    val onAccent: Color,
    /** The light along a rim, brightest where it faces the top start. */
    val rim: Color,
    /** Cards: see-through panes over the glows. */
    val card: Color,
    val cardRim: Color,
    /** The soft shadow glass floats on. */
    val shadow: Color,
)

/** The glass for [colors]: Liora's own glows, or the wallpaper's colors when [dynamic]. */
internal fun lioraGlass(
    enabled: Boolean,
    dark: Boolean,
    dynamic: Boolean,
    colors: ColorScheme,
): LioraGlass {
    val glows =
        if (dynamic) {
            Triple(colors.primary, colors.tertiary, colors.secondary)
        } else if (dark) {
            Triple(Color(0xFF5504CF), Color(0xFFC0268F), Color(0xFF2D4BFF))
        } else {
            Triple(Color(0xFF7C4DFF), Color(0xFFFF5FB8), Color(0xFF4F8BFF))
        }
    val strength = if (dark) DarkGlowAlphas else LightGlowAlphas
    return LioraGlass(
        enabled = enabled,
        dark = dark,
        base = colors.background,
        aurora =
            Triple(
                glows.first.copy(alpha = strength.first),
                glows.second.copy(alpha = strength.second),
                glows.third.copy(alpha = strength.third),
            ),
        tint = if (dark) Color(0x6614141C) else Color(0x8CFFFFFF),
        solidTint = if (dark) colors.surfaceContainerHigh.copy(alpha = 0.96f) else Color(0xF5FFFFFF),
        accentTint = (if (dynamic) colors.primary else Color(0xFF5504CF)).copy(alpha = if (dark) 0.74f else 0.84f),
        onAccent = if (dynamic) colors.onPrimary else Color.White,
        rim = if (dark) Color(0x52FFFFFF) else Color(0xF2FFFFFF),
        card = if (dark) Color(0x12FFFFFF) else Color(0xB8FFFFFF),
        cardRim = if (dark) Color(0x24FFFFFF) else Color(0xE6FFFFFF),
        shadow = if (dark) Color(0x80000000) else Color(0x262A1A5E),
    )
}

private val DarkGlowAlphas = Triple(0.55f, 0.24f, 0.2f)
private val LightGlowAlphas = Triple(0.24f, 0.16f, 0.15f)

internal val LocalLioraGlass =
    staticCompositionLocalOf {
        lioraGlass(
            enabled = false,
            dark = true,
            dynamic = false,
            colors = LioraDarkColorScheme,
        )
    }
