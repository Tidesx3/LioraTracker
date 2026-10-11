package app.liora.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.glass.GlassDefaults
import app.liora.core.designsystem.glass.LocalGlassBackdrop
import app.liora.core.designsystem.glass.drawRim
import app.liora.core.designsystem.glass.glass
import app.liora.core.designsystem.motion.rememberLioraHaptics
import app.liora.core.designsystem.theme.LioraGlass
import app.liora.core.designsystem.theme.LioraTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A tab of [LioraTabBar]. */
@Immutable
data class LioraTab(
    val label: String,
    val icon: DrawableResource,
)

/**
 * The tabs as a pill of Liquid Glass floating over the screen. The selected tab sits in a drop of
 * glass that flows to the next one when another is picked: its front leads and its back follows, so it
 * stretches on the way and settles round, like liquid. A tab squeezes a little under the finger.
 *
 * The drop can also be dragged: slide along the bar and it lifts and follows the finger, the tab under
 * it magnified, with a tick as it comes over each one; letting go there picks that tab.
 */
@Composable
fun LioraTabBar(
    tabs: List<LioraTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LioraTheme.glass
    val haptics = rememberLioraHaptics()
    val currentOnSelect by rememberUpdatedState(onSelect)
    // Where the finger holds the drop, in tabs from the start; and the tab it was let go on, until the
    // selection follows, so the drop doesn't swing back to the old tab for a frame.
    var held by remember { mutableStateOf<Float?>(null) }
    var released by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selectedIndex) { released = null }
    val hovered = held?.roundToInt()
    LaunchedEffect(hovered) { if (hovered != null) haptics.tabCrossed() }

    val target = held ?: released?.toFloat() ?: selectedIndex.coerceAtLeast(0).toFloat()
    val following = held != null
    val front by animateFloatAsState(
        target,
        if (following) spring(stiffness = HELD_STIFFNESS) else spring(FRONT_DAMPING, FRONT_STIFFNESS),
    )
    val back by animateFloatAsState(target, spring(Spring.DampingRatioNoBouncy, BACK_STIFFNESS))
    val lift by animateFloatAsState(if (following) 1f else 0f, spring(LIFT_DAMPING, Spring.StiffnessMedium))
    val drop = dropColors(glass)
    val dropPath = remember { Path() }
    val shown = hovered ?: released ?: selectedIndex
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT.dp)
                .dropShadow(CircleShape, Shadow(radius = 24.dp, color = glass.shadow, offset = DpOffset(0.dp, 8.dp)))
                .glass(LocalGlassBackdrop.current, CircleShape, GlassDefaults.regular())
                .padding(DROP_INSET.dp)
                .drawBehind {
                    if (shown >= 0 && tabs.isNotEmpty()) drawDrop(tabs.size, front, back, lift, drop, dropPath)
                }.pointerInput(tabs.size) {
                    val last = (tabs.size - 1).toFloat()

                    fun under(x: Float) = (x / (size.width.toFloat() / tabs.size) - HALF).coerceIn(0f, last)
                    detectHorizontalDragGestures(
                        onDragStart = { held = under(it.x) },
                        onDragEnd = {
                            held?.roundToInt()?.let { tab ->
                                released = tab
                                currentOnSelect(tab)
                            }
                            held = null
                        },
                        onDragCancel = { held = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            held = under(change.position.x)
                        },
                    )
                }.selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                tab = tab,
                selected = index == selectedIndex,
                highlighted = index == shown,
                magnified = index == hovered,
                onClick = { currentOnSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Tab(
    tab: LioraTab,
    selected: Boolean,
    highlighted: Boolean,
    magnified: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            magnified -> MAGNIFIED_SCALE
            pressed -> PRESSED_SCALE
            else -> 1f
        },
        spring(stiffness = Spring.StiffnessMedium),
    )
    val color by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    interactionSource = interaction,
                    indication = null,
                ).scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(tab.icon), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(
            text = tab.label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
        )
    }
}

private data class DropColors(
    val fill: Color,
    val tint: Color,
    val rim: Color,
)

@Composable
private fun dropColors(glass: LioraGlass): DropColors {
    val accent = MaterialTheme.colorScheme.primary
    return if (glass.dark) {
        DropColors(fill = Color.White.copy(alpha = 0.1f), tint = accent.copy(alpha = 0.16f), rim = glass.rim)
    } else {
        DropColors(fill = Color.White.copy(alpha = 0.62f), tint = accent.copy(alpha = 0.1f), rim = glass.rim)
    }
}

/**
 * The drop under the selected tab, from the tab its back is at to the one its front is at: as wide as a
 * tab at rest, longer and a little flatter while it flows. Held by the finger ([lift] toward 1) it rises
 * out of the bar, larger and clearer, the light brighter on its rim.
 */
private fun DrawScope.drawDrop(
    count: Int,
    front: Float,
    back: Float,
    lift: Float,
    colors: DropColors,
    path: Path,
) {
    val tab = size.width / count
    val stretch = abs(front - back).coerceAtMost(1f)
    val grow = LIFT_GROWTH * lift
    val height = size.height * (1f - FLATTEN * stretch + grow)
    val top = (size.height - height) / 2
    // The front springs a little past its tab before settling; at rest the drop stays inside the bar.
    val reach = tab * grow / 2
    val left = (min(front, back) * tab).coerceAtLeast(0f) - reach
    val right = ((max(front, back) + 1f) * tab).coerceAtMost(size.width) + reach
    translate(left, top) {
        val box = Size(right - left, height)
        path.rewind()
        path.addRoundRect(RoundRect(0f, 0f, box.width, box.height, CornerRadius(height / 2)))
        drawPath(path, colors.fill.copy(alpha = colors.fill.alpha * (1f - LIFT_CLEARING * lift)))
        drawPath(path, colors.tint)
        drawRim(path, height / 2, colors.rim, (1f + lift).dp.toPx(), box)
    }
}

private const val BAR_HEIGHT = 64
private const val DROP_INSET = 4
private const val FRONT_DAMPING = 0.72f
private const val FRONT_STIFFNESS = 520f
private const val BACK_STIFFNESS = 190f
private const val FLATTEN = 0.14f
private const val PRESSED_SCALE = 0.9f
private const val MAGNIFIED_SCALE = 1.12f
private const val HELD_STIFFNESS = 1200f
private const val LIFT_DAMPING = 0.6f
private const val LIFT_GROWTH = 0.16f
private const val LIFT_CLEARING = 0.45f
private const val HALF = 0.5f
