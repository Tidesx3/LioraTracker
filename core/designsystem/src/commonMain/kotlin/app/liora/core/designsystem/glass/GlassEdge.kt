package app.liora.core.designsystem.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.theme.LioraGlass
import kotlin.math.ceil

/**
 * Behind a top bar: what scrolls beneath it, frosted and fading out toward the bar's bottom edge, so
 * content slips under softly instead of being cut off, and the title reads over it. Without a backdrop
 * or blur, the background itself fades out there instead.
 */
fun Modifier.glassEdge(
    backdrop: GlassBackdrop?,
    glass: LioraGlass,
): Modifier = this then GlassEdgeElement(backdrop, glass)

private data class GlassEdgeElement(
    val backdrop: GlassBackdrop?,
    val glass: LioraGlass,
) : ModifierNodeElement<GlassEdgeNode>() {
    override fun create() = GlassEdgeNode(backdrop, glass)

    override fun update(node: GlassEdgeNode) {
        node.backdrop = backdrop
        node.glass = glass
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassEdge"
    }
}

private class GlassEdgeNode(
    var backdrop: GlassBackdrop?,
    var glass: LioraGlass,
) : Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode {
    private var layers: EdgeLayers? = null
    private var lens: GlassLensSpec? = null
    private var effect: RenderEffect? = null

    override val shouldAutoInvalidate = false

    override fun onAttach() {
        val graphics = requireGraphicsContext()
        layers =
            EdgeLayers(
                frost = graphics.createGraphicsLayer(),
                faded = graphics.createGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen },
            )
    }

    override fun onDetach() {
        val graphics = requireGraphicsContext()
        layers?.let {
            graphics.releaseGraphicsLayer(it.frost)
            graphics.releaseGraphicsLayer(it.faded)
        }
        layers = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) = invalidateDraw()

    override fun ContentDrawScope.draw() {
        if (!drawFrost()) {
            drawRect(Brush.verticalGradient(0f to glass.base, SOLID_UNTIL to glass.base, 1f to Color.Transparent))
        }
        drawContent()
    }

    /** The frosted backdrop, faded toward the bottom edge; false if there's none to draw. */
    private fun ContentDrawScope.drawFrost(): Boolean {
        val backdrop = backdrop
        val source = backdrop?.coordinates
        val layers = layers
        if (backdrop == null || source == null || layers == null) return false
        val (frost, faded) = layers
        val blur = BLUR.dp.toPx()
        val inset = ceil(blur * 2)
        val spec =
            GlassLensSpec(
                width = size.width,
                height = size.height,
                inset = inset,
                cornerRadius = 0f,
                blur = blur,
                bend = 0f,
                depth = 0f,
                saturation = SATURATION,
            )
        if (spec != lens) {
            lens = spec
            effect = glassLensEffect(spec)
        }
        val effect = effect ?: return false
        val offset = requireLayoutCoordinates().localPositionOf(source, Offset.Zero)
        frost.renderEffect = effect
        frost.record(IntSize((size.width + 2 * inset).toInt(), (size.height + 2 * inset).toInt())) {
            // Past the screen's edge the backdrop is empty: the background goes on there, so the blur
            // doesn't fade into nothing at the top of the window.
            drawRect(glass.base)
            translate(offset.x + inset, offset.y + inset) { drawLayer(backdrop.layer) }
        }
        faded.record {
            translate(-inset, -inset) { drawLayer(frost) }
            drawRect(
                Brush.verticalGradient(0f to glass.base.copy(alpha = TINT), 1f to glass.base.copy(alpha = TINT / 2)),
            )
            drawRect(
                Brush.verticalGradient(0f to Color.Black, SOLID_UNTIL to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }
        drawLayer(faded)
        return true
    }
}

/** The frosted backdrop, and it faded toward the bar's bottom edge (offscreen, to fade it as one). */
private data class EdgeLayers(
    val frost: GraphicsLayer,
    val faded: GraphicsLayer,
)

private const val BLUR = 18
private const val SATURATION = 1.5f
private const val TINT = 0.5f

/** How far down the bar stays fully frosted before it fades into the content. */
private const val SOLID_UNTIL = 0.62f
