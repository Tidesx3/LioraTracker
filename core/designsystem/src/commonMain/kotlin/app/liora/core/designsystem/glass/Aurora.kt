package app.liora.core.designsystem.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.liora.core.designsystem.theme.LioraGlass
import kotlin.math.min

/**
 * The background glass floats over: the theme's background lit by three soft glows ([LioraGlass.aurora]),
 * violet from the top start, pink from the end and blue from the bottom start. They're fixed to the
 * window, so panes side by side share one sky, and a screen sliding in moves over it rather than with it.
 */
fun Modifier.aurora(glass: LioraGlass): Modifier = this then AuroraElement(glass)

private data class AuroraElement(
    val glass: LioraGlass,
) : ModifierNodeElement<AuroraNode>() {
    override fun create() = AuroraNode(glass)

    override fun update(node: AuroraNode) {
        node.glass = glass
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "aurora"
    }
}

private class AuroraNode(
    var glass: LioraGlass,
) : Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    override val shouldAutoInvalidate = false

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) = invalidateDraw()

    override fun ContentDrawScope.draw() {
        drawRect(glass.base)
        val window = currentValueOf(LocalWindowInfo).containerSize.toSize().takeIf { it.minDimension > 0f } ?: size
        val origin = requireLayoutCoordinates().positionInWindow()
        translate(-origin.x, -origin.y) { drawGlows(window) }
        drawContent()
    }

    private fun DrawScope.drawGlows(window: Size) {
        val reach = min(window.minDimension, MAX_REACH.dp.toPx())
        val (violet, pink, blue) = glass.aurora
        drawGlow(violet, VioletGlow, window, reach)
        drawGlow(pink, PinkGlow, window, reach)
        drawGlow(blue, BlueGlow, window, reach)
    }
}

/** Where a glow sits, as fractions of the window, and how far it reaches, of the window's short side. */
private class Glow(
    val x: Float,
    val y: Float,
    val reach: Float,
)

private val VioletGlow = Glow(x = 0.12f, y = -0.02f, reach = 0.9f)
private val PinkGlow = Glow(x = 1.04f, y = 0.42f, reach = 0.72f)
private val BlueGlow = Glow(x = -0.04f, y = 0.96f, reach = 0.82f)

/** How strong a glow is from its middle out, evenly spaced: it fades softly, rather than evenly, toward its edge. */
private val Falloff = listOf(1f, 0.62f, 0.2f, 0f)

private fun DrawScope.drawGlow(
    color: Color,
    glow: Glow,
    window: Size,
    reach: Float,
) {
    val radius = reach * glow.reach
    if (radius <= 0f || color.alpha == 0f) return
    val center = Offset(window.width * glow.x, window.height * glow.y)
    val colors = Falloff.map { strength -> color.copy(alpha = color.alpha * strength) }
    drawCircle(Brush.radialGradient(colors, center = center, radius = radius), radius, center)
}

/** The glows keep a phone's size on big screens, so they stay glows rather than a wash. */
private const val MAX_REACH = 560
