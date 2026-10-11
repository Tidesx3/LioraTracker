package app.liora.core.designsystem.glass

import androidx.compose.ui.graphics.RenderEffect

/**
 * How a piece of glass treats what's beneath it, in pixels. The backdrop is recorded reaching [inset]
 * past the glass on every side, because the blur and the rim's bend reach that far for their colors.
 */
internal data class GlassLensSpec(
    val width: Float,
    val height: Float,
    val inset: Float,
    val cornerRadius: Float,
    /** How far the blur spreads. */
    val blur: Float,
    /** How wide the curved band along the rim is; flat glass (0) only blurs. */
    val bend: Float,
    /** How far beyond the rim its curve gathers the backdrop from. */
    val depth: Float,
    /** Above 1, colors beneath come through livelier, as through real glass. */
    val saturation: Float,
)

/**
 * The effect that turns the recorded backdrop into the view through the glass: blurred, its colors
 * lifted, and bent along the rim as by a lens. Null where the platform can't blur at all (Android
 * before 12); there glass is drawn solid instead.
 */
internal expect fun glassLensEffect(spec: GlassLensSpec): RenderEffect?
