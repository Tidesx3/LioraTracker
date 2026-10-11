package app.liora.core.designsystem.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.theme.LioraTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.min

/** How a piece of glass looks: what it lays over the backdrop it bends, and how it bends it. */
@Immutable
data class GlassStyle(
    /** Over the bent backdrop, so what's on the glass reads. */
    val tint: Color,
    /** Instead, where there's nothing to bend. */
    val solidTint: Color,
    /** The light along the rim. */
    val rim: Color,
    /** The screen's background, which goes on past its edge where the glass gathers from beyond it. */
    val underlay: Color,
    val blur: Dp = 7.dp,
    val bend: Dp = 22.dp,
    val depth: Dp = 18.dp,
    val saturation: Float = 1.7f,
)

object GlassDefaults {
    /** Clear glass, for the navigation floating over content. */
    @Composable
    @ReadOnlyComposable
    fun regular(): GlassStyle =
        with(LioraTheme.glass) { GlassStyle(tint = tint, solidTint = solidTint, rim = rim, underlay = base) }

    /** The accent's glass, for what's live: the workout in progress, the rest timer. */
    @Composable
    @ReadOnlyComposable
    fun accent(): GlassStyle =
        with(LioraTheme.glass) {
            GlassStyle(
                tint = accentTint,
                solidTint = accentTint.copy(alpha = SOLID_ACCENT),
                rim = rim.copy(alpha = if (dark) rim.alpha else ACCENT_RIM),
                underlay = base,
                saturation = 1.4f,
            )
        }

    private const val SOLID_ACCENT = 0.96f
    private const val ACCENT_RIM = 0.7f
}

/**
 * Glass in [shape]: the [backdrop] beneath it blurred, bent along the rim and tinted, light catching
 * the rim, and the content on top. Without a backdrop, or where the platform can't blur, the glass is
 * nearly solid ([GlassStyle.solidTint]) and keeps its rim. Glass must not sit inside the source of its
 * own [backdrop].
 */
fun Modifier.glass(
    backdrop: GlassBackdrop?,
    shape: Shape,
    style: GlassStyle,
): Modifier = this then GlassElement(backdrop, shape, style)

/**
 * A see-through pane with nothing to bend, such as a card over the aurora: [fill], the light along its
 * rim, drawn over its content, and, if given, a soft [shadow] kept outside it, so it doesn't darken
 * what shows through.
 */
fun Modifier.glassPane(
    shape: Shape,
    fill: Color,
    rim: Color,
    shadow: Color? = null,
): Modifier = this then GlassPaneElement(shape, fill, rim, shadow)

private data class GlassElement(
    val backdrop: GlassBackdrop?,
    val shape: Shape,
    val style: GlassStyle,
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(backdrop, shape, style)

    override fun update(node: GlassNode) {
        node.update(backdrop, shape, style)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glass"
    }
}

private class GlassNode(
    private var backdrop: GlassBackdrop?,
    private var shape: Shape,
    private var style: GlassStyle,
) : Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode {
    private var layer: GraphicsLayer? = null
    private var glow: Painter? = null
    private var lens: GlassLensSpec? = null
    private var effect: RenderEffect? = null
    private val path = Path()

    override val shouldAutoInvalidate = false

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer()
    }

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
        glow = null
    }

    fun update(
        backdrop: GlassBackdrop?,
        shape: Shape,
        style: GlassStyle,
    ) {
        if (shape != this.shape || style.rim != this.style.rim) glow = null
        this.backdrop = backdrop
        this.shape = shape
        this.style = style
        invalidateDraw()
    }

    // The backdrop is drawn from where it sits relative to the glass, so moving either redraws it.
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) = invalidateDraw()

    override fun ContentDrawScope.draw() {
        val outline = shape.createOutline(size, layoutDirection, this)
        path.rewind()
        path.addOutline(outline)
        val bent = drawBackdrop(outline)
        drawPath(path, if (bent) style.tint else style.solidTint)
        drawGlow()
        drawRim(path, outline.cornerRadius(size), style.rim, RIM_WIDTH.dp.toPx())
        drawContent()
    }

    /** The backdrop through the glass, clipped to it; false if there's none to draw. */
    private fun ContentDrawScope.drawBackdrop(outline: Outline): Boolean {
        val backdrop = backdrop
        val source = backdrop?.coordinates
        val layer = layer
        if (backdrop == null || source == null || layer == null) return false
        val inset = ceil((style.depth + style.blur * 2).toPx())
        val spec =
            GlassLensSpec(
                width = size.width,
                height = size.height,
                inset = inset,
                cornerRadius = outline.cornerRadius(size),
                blur = style.blur.toPx(),
                bend = style.bend.toPx(),
                depth = style.depth.toPx(),
                saturation = style.saturation,
            )
        if (spec != lens) {
            lens = spec
            effect = glassLensEffect(spec)
        }
        val effect = effect ?: return false
        val offset = requireLayoutCoordinates().localPositionOf(source, Offset.Zero)
        layer.renderEffect = effect
        layer.record(IntSize((size.width + 2 * inset).toInt(), (size.height + 2 * inset).toInt())) {
            drawRect(style.underlay)
            translate(offset.x + inset, offset.y + inset) { drawLayer(backdrop.layer) }
        }
        clipPath(path) { translate(-inset, -inset) { drawLayer(layer) } }
        return true
    }

    /** Light gathering inside the rim, as in thick glass. */
    private fun DrawScope.drawGlow() {
        val painter =
            glow ?: requireGraphicsContext()
                .shadowContext
                .createInnerShadowPainter(shape, Shadow(radius = GLOW_RADIUS.dp, color = style.rim.copy(alpha = GLOW)))
                .also { glow = it }
        with(painter) { draw(size) }
    }
}

private data class GlassPaneElement(
    val shape: Shape,
    val fill: Color,
    val rim: Color,
    val shadow: Color?,
) : ModifierNodeElement<GlassPaneNode>() {
    override fun create() = GlassPaneNode(shape, fill, rim, shadow)

    override fun update(node: GlassPaneNode) {
        node.update(shape, fill, rim, shadow)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassPane"
    }
}

private class GlassPaneNode(
    private var shape: Shape,
    private var fill: Color,
    private var rim: Color,
    private var shadow: Color?,
) : Modifier.Node(),
    DrawModifierNode {
    private val path = Path()
    private var shadowPainter: Painter? = null

    override val shouldAutoInvalidate = false

    fun update(
        shape: Shape,
        fill: Color,
        rim: Color,
        shadow: Color?,
    ) {
        if (shape != this.shape || shadow != this.shadow) shadowPainter = null
        this.shape = shape
        this.fill = fill
        this.rim = rim
        this.shadow = shadow
        invalidateDraw()
    }

    override fun onDetach() {
        shadowPainter = null
    }

    override fun ContentDrawScope.draw() {
        val outline = shape.createOutline(size, layoutDirection, this)
        path.rewind()
        path.addOutline(outline)
        shadow?.let { color ->
            val painter =
                shadowPainter ?: requireGraphicsContext()
                    .shadowContext
                    .createDropShadowPainter(
                        shape,
                        Shadow(
                            radius = PANE_SHADOW_RADIUS.dp,
                            color = color,
                            offset = DpOffset(0.dp, PANE_SHADOW_DROP.dp),
                        ),
                    ).also { shadowPainter = it }
            clipPath(path, ClipOp.Difference) { with(painter) { draw(size) } }
        }
        drawPath(path, fill)
        drawContent()
        drawRim(path, outline.cornerRadius(size), rim, PANE_RIM_WIDTH.dp.toPx())
    }
}

/** The corner radius the lens curves around, from the outline (a rounded shape's top start corner). */
internal fun Outline.cornerRadius(size: Size): Float =
    when (this) {
        is Outline.Rounded -> min(roundRect.topLeftCornerRadius.x, min(size.width, size.height) / 2)
        is Outline.Rectangle -> 0f
        is Outline.Generic -> min(size.width, size.height) / 2
    }

/**
 * The rim of [path] (filling [box] from the scope's origin), inside the edge: light from the top left
 * catches the corner facing it and, more softly, the one opposite, and fades along the sides between.
 */
internal fun DrawScope.drawRim(
    path: Path,
    radius: Float,
    color: Color,
    width: Float,
    box: Size = size,
) {
    if (box.minDimension <= 0f) return
    val center = Offset(box.width / 2, box.height / 2)
    // Where the light lands: the start of the top edge, past the corner's curve, and the end of the bottom one.
    val lit = angleOf(Offset(radius, 0f) - center)
    val opposite = angleOf(Offset(box.width - radius, box.height) - center)
    // Evenly spaced around the sweep.
    val colors =
        List(RIM_STOPS + 1) { i ->
            val at = i.toFloat() / RIM_STOPS
            val light = RIM_BASE + RIM_MAIN * gleam(at, lit) + RIM_SECOND * gleam(at, opposite)
            color.copy(alpha = (color.alpha * light).coerceAtMost(1f))
        }
    clipPath(path) {
        drawPath(path, Brush.sweepGradient(colors, center = center), style = Stroke(width * 2))
    }
}

/** The sweep's position (0 to 1, clockwise from the end) of the direction [d]. */
private fun angleOf(d: Offset): Float {
    val turns = (atan2(d.y, d.x) / (2 * PI)).toFloat()
    return if (turns < 0f) turns + 1f else turns
}

/** How brightly the light at [lit] shows at [at], both in turns: a soft peak. */
private fun gleam(
    at: Float,
    lit: Float,
): Float {
    val apart = kotlin.math.abs(at - lit).let { min(it, 1f - it) } / RIM_SPREAD
    return exp(-apart * apart)
}

private const val RIM_WIDTH = 1.2f
private const val RIM_STOPS = 36
private const val RIM_BASE = 0.18f
private const val RIM_MAIN = 0.82f
private const val RIM_SECOND = 0.45f
private const val RIM_SPREAD = 0.09f
private const val GLOW_RADIUS = 10
private const val GLOW = 0.22f
private const val PANE_RIM_WIDTH = 1f
private const val PANE_SHADOW_RADIUS = 22
private const val PANE_SHADOW_DROP = 6
