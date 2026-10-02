package app.liora.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Liora's palette: one vivid violet (#5504CF) on near-neutral surfaces, chosen in Phase 2. In light it's
// the accent itself, on white cards over a pale lavender grey; in dark it fills bars and ticks on near
// black, and a lighter violet of the same hue carries text and buttons, where #5504CF would be too
// dark to read. A warm amber stays reserved for achievements (records, streaks).

internal val LioraDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFA57BFF),
        onPrimary = Color(0xFF1A0047),
        primaryContainer = Color(0xFF5504CF),
        onPrimaryContainer = Color(0xFFEBDDFF),
        inversePrimary = Color(0xFF5504CF),
        secondary = Color(0xFFA9A7B8),
        onSecondary = Color(0xFF0B0B0F),
        secondaryContainer = Color(0xFF2B1660),
        onSecondaryContainer = Color(0xFFE6DAFF),
        tertiary = Color(0xFFFFB870),
        onTertiary = Color(0xFF4A2800),
        tertiaryContainer = Color(0xFF6A3C00),
        onTertiaryContainer = Color(0xFFFFDCBE),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0B0B0F),
        onBackground = Color(0xFFEEEDF4),
        surface = Color(0xFF0B0B0F),
        onSurface = Color(0xFFEEEDF4),
        surfaceVariant = Color(0xFF262632),
        onSurfaceVariant = Color(0xFFA9A7B8),
        surfaceTint = Color(0xFFA57BFF),
        inverseSurface = Color(0xFFE6E4EE),
        inverseOnSurface = Color(0xFF2A2933),
        outline = Color(0xFF7A778C),
        outlineVariant = Color(0xFF2C2B38),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF262632),
        surfaceDim = Color(0xFF0B0B0F),
        surfaceContainerLowest = Color(0xFF060609),
        surfaceContainerLow = Color(0xFF111117),
        surfaceContainer = Color(0xFF17171F),
        surfaceContainerHigh = Color(0xFF1E1E28),
        surfaceContainerHighest = Color(0xFF262632),
    )

internal val LioraLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF5504CF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF23005C),
        inversePrimary = Color(0xFFCFBCFF),
        secondary = Color(0xFF5D5A6E),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEFE7FF),
        onSecondaryContainer = Color(0xFF26085E),
        tertiary = Color(0xFF8A5100),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDCBE),
        onTertiaryContainer = Color(0xFF2C1600),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF6F5FA),
        onBackground = Color(0xFF141220),
        surface = Color(0xFFF6F5FA),
        onSurface = Color(0xFF141220),
        surfaceVariant = Color(0xFFE1E0EB),
        onSurfaceVariant = Color(0xFF5D5A6E),
        surfaceTint = Color(0xFF5504CF),
        inverseSurface = Color(0xFF2D2B38),
        inverseOnSurface = Color(0xFFF3F1F8),
        outline = Color(0xFF7B7790),
        outlineVariant = Color(0xFFD7D4E3),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFDCDAE6),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFAFAFD),
        surfaceContainer = Color(0xFFEFEEF5),
        surfaceContainerHigh = Color(0xFFE8E7F0),
        surfaceContainerHighest = Color(0xFFE1E0EB),
    )
