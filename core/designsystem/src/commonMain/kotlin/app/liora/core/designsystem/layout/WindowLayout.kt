package app.liora.core.designsystem.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * Layout decisions derived from the window size class and the device posture, never from device checks.
 * The same code serves phones, the Galaxy Z Fold 7's cover screen (~411 dp, compact) and inner screen
 * (~984 dp, expanded), split-screen and tablets, and it re-evaluates live when the window is folded,
 * unfolded or resized.
 */
@Immutable
data class WindowLayout(
    /** Medium width and up: a navigation rail replaces the bottom bar. */
    val navigationRail: Boolean,
    /** Expanded width and up: list-detail destinations show both panes side by side. */
    val twoPane: Boolean,
    /** Half folded with a horizontal hinge, standing on a table like a little laptop. */
    val tabletop: Boolean = false,
)

@Composable
fun currentWindowLayout(): WindowLayout {
    val info = currentWindowAdaptiveInfo()
    val sizeClass = info.windowSizeClass
    return WindowLayout(
        navigationRail = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND),
        twoPane = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND),
        tabletop = info.windowPosture.isTabletop,
    )
}

/** Where the fold runs, in window pixels, when the device reports one; null on flat screens. */
@Composable
fun currentHingeBounds(): Rect? =
    currentWindowAdaptiveInfo()
        .windowPosture.hingeList
        .firstOrNull()
        ?.bounds

/** Widest a column of text or form fields gets, so lines don't stretch across a wide screen. */
val ReadableContentWidth = 600.dp

/** Fills the width up to [ReadableContentWidth] and centers itself in anything wider. */
fun Modifier.readableWidth(): Modifier =
    fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = ReadableContentWidth)
        .fillMaxWidth()
