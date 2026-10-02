package app.liora.core.designsystem.illustration

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

// Every scene is drawn in a 240 × 160 box, on a blob that fills most of it.
internal const val SCENE_WIDTH = 240f
internal const val SCENE_HEIGHT = 160f

/** One shape of a scene, filled with its [tone], or outlined when it has a [stroke]. */
internal class Layer(
    val tone: Tone,
    val path: Path,
    val stroke: Stroke? = null,
)

internal class SceneBuilder {
    private val layers = mutableListOf<Layer>()

    fun fill(
        tone: Tone,
        path: Path,
    ) {
        layers += Layer(tone, path)
    }

    /** Outlines [path] with round ends and corners, as a pen would. */
    fun stroke(
        tone: Tone,
        width: Float,
        path: Path,
    ) {
        layers += Layer(tone, path, Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun build(): List<Layer> = layers.toList()
}

/** A scene: the blob, then [content], each shape over the ones before it. */
internal fun scene(content: SceneBuilder.() -> Unit): List<Layer> =
    SceneBuilder()
        .apply {
            fill(Tone.Wash, Blob)
            content()
        }.build()

// A soft, lopsided round, from about (42, 21) to (198, 143).
private val Blob =
    svg(
        "M 120 22 C 158 20 192 40 196 76 C 200 110 172 140 132 142 " +
            "C 92 145 50 132 44 98 C 38 62 78 24 120 22 Z",
    )
