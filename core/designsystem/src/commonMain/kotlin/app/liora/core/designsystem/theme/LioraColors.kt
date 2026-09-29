package app.liora.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colors Material 3 has no role for: set types, records and completion states. */
@Immutable
data class LioraColors(
    val personalRecord: Color,
    val onPersonalRecord: Color,
    val warmupSet: Color,
    val dropSet: Color,
    val failureSet: Color,
    val completed: Color,
    val onCompleted: Color,
)

internal val DarkLioraColors =
    LioraColors(
        personalRecord = Color(0xFFFFC857),
        onPersonalRecord = Color(0xFF3D2A00),
        warmupSet = Color(0xFFF2B84B),
        dropSet = Color(0xFFB79CFF),
        failureSet = Color(0xFFFF7A7A),
        completed = Color(0xFF5BD68A),
        onCompleted = Color(0xFF003919),
    )

internal val LightLioraColors =
    LioraColors(
        personalRecord = Color(0xFFB07A00),
        onPersonalRecord = Color(0xFFFFFFFF),
        warmupSet = Color(0xFF976800),
        dropSet = Color(0xFF6B4FD8),
        failureSet = Color(0xFFC62828),
        completed = Color(0xFF1E8E4E),
        onCompleted = Color(0xFFFFFFFF),
    )

internal val LocalLioraColors = staticCompositionLocalOf { DarkLioraColors }
