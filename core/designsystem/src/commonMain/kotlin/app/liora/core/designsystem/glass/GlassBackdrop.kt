package app.liora.core.designsystem.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo

/**
 * What glass floating over some content bends: that content, recorded by [glassSource] each time it
 * draws, so the glass follows it as it scrolls and changes. Glass is never inside its own backdrop's
 * source, or it would have to draw itself; screens get one from [LocalGlassBackdrop] where that holds.
 */
@Stable
class GlassBackdrop internal constructor(
    internal val layer: GraphicsLayer,
) {
    internal var source: Modifier.Node? = null

    /** Where the recorded content sits, while it's on screen to be bent. */
    internal val coordinates: LayoutCoordinates?
        get() = source?.takeIf { it.isAttached && !layer.isReleased }?.requireLayoutCoordinates()
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/**
 * The backdrop for glass in the bars around a screen's content ([app.liora.core.designsystem.component.LioraScaffold]
 * provides it to its bars, and the app shell to its tabs), or null inside the content itself.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Records this content for the glass floating over it, and draws it as usual. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this then GlassSourceElement(backdrop)

private data class GlassSourceElement(
    val backdrop: GlassBackdrop,
) : ModifierNodeElement<GlassSourceNode>() {
    override fun create() = GlassSourceNode(backdrop)

    override fun update(node: GlassSourceNode) {
        node.update(backdrop)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassSource"
    }
}

private class GlassSourceNode(
    private var backdrop: GlassBackdrop,
) : Modifier.Node(),
    DrawModifierNode {
    override fun onAttach() {
        backdrop.source = this
    }

    override fun onDetach() {
        if (backdrop.source === this) backdrop.source = null
    }

    fun update(backdrop: GlassBackdrop) {
        if (backdrop === this.backdrop) return
        onDetach()
        this.backdrop = backdrop
        onAttach()
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val layer = backdrop.layer
        if (layer.isReleased) {
            drawContent()
            return
        }
        layer.record { this@draw.drawContent() }
        drawLayer(layer)
    }
}
