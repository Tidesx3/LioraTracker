package app.liora.core.designsystem.illustration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import app.liora.core.designsystem.component.isDark

/** The scenes drawn for empty screens, each a small still life on a soft blob of the accent. */
enum class Illustration {
    /** Two routine cards and a play button. */
    Routines,

    /** A barbell waiting for its first exercise. */
    Workout,

    /** A calendar page with one day ticked off. */
    Calendar,

    /** A chart climbing to a flag. */
    Chart,

    /** A magnifying glass that found nothing. */
    Search,

    /** A kettlebell and a dumbbell. */
    Exercises,

    /** A bathroom scale with footprints on it. */
    Body,

    /** Two instant photos of a pose. */
    Photos,

    /** Weight plates. */
    Gym,

    /** A page nobody can read. */
    UnknownFile,

    /** A tick in confetti. */
    Done,
}

/**
 * Draws [illustration] in the theme's colors: the accent and tints of it on the screen's own tones, so
 * it sits as calmly in dark themes as in light ones, and follows wallpaper colors. Decorative: the
 * text beside it says what it means.
 */
@Composable
fun LioraIllustration(
    illustration: Illustration,
    modifier: Modifier = Modifier,
) {
    val colors = illustrationColors()
    val layers = illustration.layers
    Canvas(modifier.aspectRatio(SCENE_WIDTH / SCENE_HEIGHT)) {
        scale(size.width / SCENE_WIDTH, pivot = Offset.Zero) {
            layers.forEach { layer ->
                val color = colors.of(layer.tone)
                val stroke = layer.stroke
                if (stroke == null) drawPath(layer.path, color) else drawPath(layer.path, color, style = stroke)
            }
        }
    }
}

/** The roles a scene paints with; [illustrationColors] gives each one its color. */
internal enum class Tone {
    /** The blob behind every scene. */
    Wash,

    /** The ground under standing objects. */
    Shadow,

    /** Cards, pages and photo frames. */
    Paper,

    /** Lines and dots printed on paper. */
    Line,

    /** A lighter accent for second objects and sparkles. */
    Soft,
    Accent,

    /** Marks on the accent, like a tick. */
    OnAccent,
}

internal class IllustrationColors(
    private val wash: Color,
    private val shadow: Color,
    private val paper: Color,
    private val line: Color,
    private val soft: Color,
    private val accent: Color,
    private val onAccent: Color,
) {
    fun of(tone: Tone): Color =
        when (tone) {
            Tone.Wash -> wash
            Tone.Shadow -> shadow
            Tone.Paper -> paper
            Tone.Line -> line
            Tone.Soft -> soft
            Tone.Accent -> accent
            Tone.OnAccent -> onAccent
        }
}

// Tints of the accent over the screen: a pale lavender in light themes, a deep violet in dark ones,
// where paper is a step up from the cards and the softer accent sits between the wash and the accent.
@Composable
private fun illustrationColors(): IllustrationColors =
    with(MaterialTheme.colorScheme) {
        if (isDark()) {
            val wash = lerp(surface, primaryContainer, 0.24f)
            val paper = lerp(surfaceContainerHighest, primary, 0.1f)
            IllustrationColors(
                wash = wash,
                shadow = lerp(surface, primaryContainer, 0.1f),
                paper = paper,
                line = lerp(paper, primary, 0.28f),
                soft = lerp(wash, primary, 0.48f),
                accent = primary,
                onAccent = onPrimary,
            )
        } else {
            IllustrationColors(
                wash = lerp(surface, primaryContainer, 0.8f),
                shadow = lerp(surface, primary, 0.16f),
                paper = surfaceContainerLowest,
                line = lerp(surfaceContainerLowest, primary, 0.16f),
                soft = lerp(surfaceContainerLowest, primary, 0.42f),
                accent = primary,
                onAccent = onPrimary,
            )
        }
    }
