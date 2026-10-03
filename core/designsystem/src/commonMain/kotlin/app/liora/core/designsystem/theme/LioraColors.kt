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

// A done set turns emerald, so done reads at a glance against the violet that marks what's next; drop
// sets are pink, so they don't read as the accent either. In dark the green is brighter and carries a
// dark tick, which keeps the contrast the white tick has on the deeper green in light.

internal val DarkLioraColors =
    LioraColors(
        personalRecord = Color(0xFFFFC857),
        onPersonalRecord = Color(0xFF3D2A00),
        warmupSet = Color(0xFFF2B84B),
        dropSet = Color(0xFFFF8AD0),
        failureSet = Color(0xFFFF7A7A),
        completed = Color(0xFF34D399),
        onCompleted = Color(0xFF002A1B),
    )

internal val LightLioraColors =
    LioraColors(
        personalRecord = Color(0xFFB07A00),
        onPersonalRecord = Color(0xFFFFFFFF),
        warmupSet = Color(0xFF976800),
        dropSet = Color(0xFFC2185B),
        failureSet = Color(0xFFC62828),
        completed = Color(0xFF0E9F6E),
        onCompleted = Color(0xFFFFFFFF),
    )

internal val LocalLioraColors = staticCompositionLocalOf { DarkLioraColors }
