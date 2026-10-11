package app.liora.core.designsystem.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect as PlatformRenderEffect

/**
 * Android 13 and up bend the rim with [LENS] (AGSL) over the blur; Android 12 blurs and lifts the
 * colors with a color matrix, the glass flat; before that there's no blur, so no glass.
 */
internal actual fun glassLensEffect(spec: GlassLensSpec): RenderEffect? =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> lensEffect(spec)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> frostEffect(spec)
        else -> null
    }

@RequiresApi(Build.VERSION_CODES.S)
private fun blur(spec: GlassLensSpec): PlatformRenderEffect? =
    spec.blur.takeIf { it > 0f }?.let { PlatformRenderEffect.createBlurEffect(it, it, Shader.TileMode.CLAMP) }

@RequiresApi(Build.VERSION_CODES.S)
private fun frostEffect(spec: GlassLensSpec): RenderEffect? {
    val lift = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(spec.saturation) })
    val blur = blur(spec)
    val effect =
        if (blur != null) {
            PlatformRenderEffect.createColorFilterEffect(lift, blur)
        } else {
            PlatformRenderEffect.createColorFilterEffect(lift)
        }
    return effect.asComposeRenderEffect()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun lensEffect(spec: GlassLensSpec): RenderEffect {
    // One shader for all glass: an effect keeps the uniforms it was made with, so each piece of glass
    // sets its own and makes its effect right away, on the main thread where Compose draws.
    val shader = Lens.shader
    shader.setFloatUniform("size", spec.width, spec.height)
    shader.setFloatUniform("origin", spec.inset, spec.inset)
    shader.setFloatUniform("radius", spec.cornerRadius)
    shader.setFloatUniform("bend", spec.bend)
    shader.setFloatUniform("depth", spec.depth)
    shader.setFloatUniform("saturation", spec.saturation)
    val lens = PlatformRenderEffect.createRuntimeShaderEffect(shader, "content")
    val blur = blur(spec)
    return (if (blur != null) PlatformRenderEffect.createChainEffect(lens, blur) else lens).asComposeRenderEffect()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object Lens {
    val shader by lazy { RuntimeShader(LENS) }
}

/**
 * A rounded rectangle of glass, thick at its rim. Inside the band of width `bend` along the rim the
 * surface curves down like a lens's edge (a quarter circle), so the content there is gathered from
 * up to `depth` beyond the rim, squeezed in, the way glass bends what's around it, and the colors come
 * through livelier (`saturation`).
 */
private const val LENS = """
uniform shader content;
uniform float2 size;
uniform float2 origin;
uniform float radius;
uniform float bend;
uniform float depth;
uniform float saturation;

float roundedBox(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - origin - halfSize;
    float d = roundedBox(p, halfSize, radius);
    float2 shift = float2(0.0);
    if (d < 0.0 && bend > 0.0 && -d < bend) {
        float e = 0.5;
        float2 normal = normalize(float2(
            roundedBox(p + float2(e, 0.0), halfSize, radius) - roundedBox(p - float2(e, 0.0), halfSize, radius),
            roundedBox(p + float2(0.0, e), halfSize, radius) - roundedBox(p - float2(0.0, e), halfSize, radius)
        ) + 0.0001);
        float x = 1.0 + d / bend;
        float curve = 1.0 - sqrt(max(1.0 - x * x, 0.0));
        shift = normal * curve * depth;
    }
    half4 color = content.eval(coord + shift);
    half luma = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
    color.rgb = clamp(mix(half3(luma), color.rgb, half(saturation)), half3(0.0), half3(color.a));
    return color;
}
"""
