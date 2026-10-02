package app.liora.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import app.liora.core.model.Muscle
import app.liora.core.ui.resources.Res
import app.liora.core.ui.resources.body_back
import app.liora.core.ui.resources.body_front
import org.jetbrains.compose.resources.stringResource

/** How hard a muscle was worked over a week, by hard sets: none, a little, solid, a full dose. */
enum class MuscleLoad {
    None,
    Light,
    Moderate,
    Full,
    ;

    companion object {
        /** 10 or more hard sets a week is the usual dose for growth; 5 starts to count. */
        fun of(sets: Double): MuscleLoad =
            when {
                sets <= 0.0 -> None
                sets < MODERATE_SETS -> Light
                sets < FULL_SETS -> Moderate
                else -> Full
            }

        private const val MODERATE_SETS = 5.0
        private const val FULL_SETS = 10.0
    }
}

/**
 * The color a [load] gets on the heatmap: the body's own tone, then ever more of the primary color. The
 * colors are opaque, so the legend's swatches match the figure.
 */
@Composable
fun muscleLoadColor(load: MuscleLoad): Color {
    // outlineVariant stands out from the background in both themes, as the body should.
    val body = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    return when (load) {
        MuscleLoad.None -> body
        MuscleLoad.Light -> lerp(body, primary, LIGHT_SHARE)
        MuscleLoad.Moderate -> lerp(body, primary, MODERATE_SHARE)
        MuscleLoad.Full -> primary
    }
}

/**
 * A body seen from the front and the back, each muscle shaded by how many hard sets it got. The muscles
 * are rounded shapes, cut apart by thin lines in the background color.
 */
@Composable
fun BodyHeatmap(
    setsPerMuscle: Map<Muscle, Double>,
    modifier: Modifier = Modifier,
) {
    val colors = MuscleLoad.entries.associateWith { muscleLoadColor(it) }
    val colorOf = { muscle: Muscle? ->
        colors.getValue(if (muscle == null) MuscleLoad.None else MuscleLoad.of(setsPerMuscle[muscle] ?: 0.0))
    }
    val seams = MaterialTheme.colorScheme.surface
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BodyView(stringResource(Res.string.body_front), FrontView, colorOf, seams, Modifier.weight(1f))
        BodyView(stringResource(Res.string.body_back), BackView, colorOf, seams, Modifier.weight(1f))
    }
}

@Composable
private fun BodyView(
    label: String,
    parts: List<BodyPart>,
    colorOf: (Muscle?) -> Color,
    seams: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(WIDTH / HEIGHT)) {
            scale(size.width / WIDTH, pivot = Offset.Zero) {
                translate(top = -TOP) {
                    // Parts may run past the body's edge: the outline trims them, so they meet it exactly.
                    clipPath(Silhouette) {
                        drawPath(Silhouette, colorOf(null))
                        parts.forEach { part ->
                            drawPath(part.path, colorOf(part.muscle))
                            drawPath(part.path, seams, style = Seam)
                        }
                    }
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One shape of the figure, in a 200 × 400 box, and the muscle it shows; null is a part no stat covers.
 * Parts are drawn in order, each over the ones before it, so its outline is the seam between them.
 */
private class BodyPart(
    val muscle: Muscle?,
    val path: Path,
)

private const val WIDTH = 200f

// The figure stands between 10 and 390 in its 400 high box.
private const val TOP = 10f
private const val HEIGHT = 380f
private const val SEAM_WIDTH = 1.6f
private const val LIGHT_SHARE = 0.38f
private const val MODERATE_SHARE = 0.68f

// A cubic curve bulges out 3/4 as far as its control points.
private const val CONTROL_REACH = 4f / 3f
private const val POINTED_START = 0.2f
private const val POINTED_END = 0.8f

private val Seam = Stroke(width = SEAM_WIDTH, join = StrokeJoin.Round)

// Mirrors across the middle line, x = 100.
private val Mirror =
    Matrix().apply {
        values[Matrix.ScaleX] = -1f
        values[Matrix.TranslateX] = WIDTH
    }

private fun Path.mirrored(): Path =
    Path().also {
        it.addPath(this)
        it.transform(Mirror)
    }

private fun svg(pathData: String): Path = PathParser().parsePathString(pathData).toPath()

private fun roundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
): Path = Path().apply { addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(radius))) }

/**
 * An almond from ([ax], [ay]) down to ([bx], [by]). Its [left] side bulges out by the first width near
 * the start and the second near the end, and so does its [right] side. [along] places the bulges on the
 * way: 0 and 1 round the ends off, 0.2 and 0.8 bring them to a point.
 */
private fun leaf(
    ax: Int,
    ay: Int,
    bx: Int,
    by: Int,
    left: Pair<Int, Int>,
    right: Pair<Int, Int>,
    along: Pair<Float, Float> = POINTED_START to POINTED_END,
): Path {
    val start = Offset(ax.toFloat(), ay.toFloat())
    val end = Offset(bx.toFloat(), by.toFloat())
    val axis = end - start
    // Points left of a downward axis.
    val out = Offset(-axis.y, axis.x) / axis.getDistance() * CONTROL_REACH

    fun point(
        at: Float,
        width: Int,
    ) = start + axis * at + out * width.toFloat()

    val left1 = point(along.first, left.first)
    val left2 = point(along.second, left.second)
    val right1 = point(along.first, -right.first)
    val right2 = point(along.second, -right.second)
    return Path().apply {
        moveTo(start.x, start.y)
        cubicTo(left1.x, left1.y, left2.x, left2.y, end.x, end.y)
        cubicTo(right2.x, right2.y, right1.x, right1.y, start.x, start.y)
        close()
    }
}

/** [path] on the viewer's left, and its mirror image on the right. */
private fun MutableList<BodyPart>.both(
    muscle: Muscle?,
    path: Path,
) {
    add(BodyPart(muscle, path))
    add(BodyPart(muscle, path.mirrored()))
}

/** [path] as it is, across the middle of the body. */
private fun MutableList<BodyPart>.middle(
    muscle: Muscle?,
    path: Path,
) {
    add(BodyPart(muscle, path))
}

// Top-level values initialize in order: everything the views use comes before them.

private val Head =
    svg(
        "M 100 14 C 110 14 116 21 116 31 C 118 32 118 40 116 43 C 115 50 108 58 100 58 " +
            "C 92 58 85 50 84 43 C 82 40 82 32 84 31 C 84 21 90 14 100 14 Z",
    )

/** The body's half on the viewer's left, head to toe. It ends on the middle line, where its mirror image starts. */
private val HalfOutline =
    svg(
        "M 100 14 C 90 14 84 21 84 31 C 82 32 82 40 84 43 C 85 47 88 51 91 53 C 91 58 90 61 88 63 " +
            "C 82 68 72 72 62 76 C 50 79 43 86 42 96 C 41 104 40 108 38 114 C 36 122 34 130 33 140 " +
            "C 27 148 21 160 19 172 C 18 178 18 183 18 188 " +
            // The hand: the thumb, then four fingers.
            "C 14 192 10 197 8 202 C 7 205 9 207 11 205 C 13 202 15 200 16 201 " +
            "C 15 207 14 213 14 218 C 14 220 16 221 17 219 C 17.5 214 18 211 18.5 209 " +
            "C 18.5 214 18.5 219 19 222 C 19.5 224 21.5 224 22 222 C 22 217 22 212 22 209 " +
            "C 22.5 213 23 217 23.5 220 C 24 222 26 222 26 220 C 26 216 26 211 25.5 208 " +
            "C 26.5 211 27.5 214 28 216 C 28.5 218 30.5 217 30 215 C 29.5 210 29.5 204 29 198 " +
            "L 28 191 C 34 182 44 168 48 156 C 50 150 51 144 52 139 C 56 130 59 120 61 110 " +
            "C 63 124 65 142 68 158 C 69 168 67 178 63 186 C 59 198 56 212 56 226 C 56 242 59 258 61 270 " +
            "C 57 284 54 298 55 314 C 56 330 61 342 64 352 " +
            // The foot.
            "C 63 360 60 366 57 371 C 54 376 53 382 56 385 C 59 388 66 388 72 387 C 77 386 80 384 80 380 " +
            "C 80 372 78 362 77 354 C 79 340 83 322 84 306 " +
            "C 85 294 86 284 87 274 C 89 256 92 236 94 222 C 96 214 98 208 100 206 Z",
    )

private val Silhouette =
    Path().apply {
        addPath(HalfOutline)
        addPath(HalfOutline.mirrored())
    }

private val Deltoid =
    svg("M 70 80 C 60 74 46 78 42 90 C 39 100 40 108 42 113 C 50 107 58 97 64 90 C 68 86 71 83 70 80 Z")

// Below the wrist and the ankle, so the hand and the foot get a line of their own.
private val Hand = svg("M 8 187 L 34 191 L 34 232 L 0 232 L 0 194 Z")
private val Foot = svg("M 60 353 C 66 349 74 350 79 355 L 84 392 L 50 392 Z")

private val FrontView: List<BodyPart> =
    buildList {
        both(Muscle.Traps, svg("M 100 58 L 88 58 C 82 66 70 72 56 74 L 56 84 L 100 84 Z"))
        middle(Muscle.Neck, svg("M 86 48 C 88 60 93 72 100 82 C 107 72 112 60 114 48 Z"))
        middle(null, Head)
        both(Muscle.Shoulders, Deltoid)
        both(
            Muscle.Chest,
            svg("M 100 85 C 94 82 84 81 74 82 C 65 83 60 89 59 97 C 58 106 63 114 72 117 C 82 120 94 119 100 117 Z"),
        )
        both(Muscle.Lats, svg("M 56 104 C 62 110 67 120 70 134 L 56 134 Z"))
        // Serratus.
        both(null, leaf(63, 116, 80, 127, left = 1 to 1, right = 6 to 4, along = 0.15f to 0.8f))
        // The obliques, which the abdominals' stat covers.
        both(Muscle.Abdominals, leaf(70, 129, 81, 184, left = 12 to 12, right = 3 to 4, along = 0.1f to 0.9f))
        both(Muscle.Abdominals, roundRect(84f, 120f, 100f, 132f, 4f))
        both(Muscle.Abdominals, roundRect(83.5f, 134f, 100f, 146.5f, 4f))
        both(Muscle.Abdominals, roundRect(83f, 148.5f, 100f, 161f, 4f))
        both(
            Muscle.Abdominals,
            svg("M 100 163 L 100 202 C 95 198 90 190 87 182 C 84 175 83 169 83 166 C 83 164 84 163 86 163 Z"),
        )
        both(Muscle.Triceps, leaf(44, 108, 36, 142, left = 6 to 6, right = 3 to 3, along = 0.1f to 0.9f))
        both(Muscle.Biceps, leaf(56, 106, 46, 144, left = 12 to 10, right = 12 to 9, along = 0.08f to 0.92f))
        both(Muscle.Forearms, leaf(36, 140, 19, 186, left = 10 to 4, right = 6 to 2, along = 0.15f to 0.75f))
        both(Muscle.Forearms, leaf(50, 144, 25, 190, left = 7 to 3, right = 10 to 4, along = 0.15f to 0.8f))
        both(null, Hand)
        both(Muscle.Adductors, svg("M 100 207 C 92 202 82 196 74 192 L 80 230 C 84 244 86 256 88 272 L 100 272 Z"))
        both(Muscle.Abductors, leaf(64, 186, 58, 244, left = 10 to 6, right = 4 to 3, along = 0.15f to 0.8f))
        both(Muscle.Quadriceps, leaf(66, 210, 69, 264, left = 14 to 10, right = 7 to 6, along = 0.2f to 0.85f))
        both(Muscle.Quadriceps, leaf(72, 185, 74, 252, left = 11 to 6, right = 13 to 6, along = 0.25f to 0.8f))
        both(Muscle.Quadriceps, leaf(85, 220, 80, 270, left = 9 to 9, right = 9 to 10, along = 0.2f to 0.92f))
        // The knee.
        both(null, leaf(77, 262, 77, 290, left = 8 to 7, right = 8 to 7, along = 0.1f to 0.9f))
        both(Muscle.Calves, leaf(63, 276, 64, 344, left = 10 to 8, right = 5 to 4))
        // The shin.
        both(null, leaf(68, 284, 72, 352, left = 5 to 4, right = 6 to 3))
        both(Muscle.Calves, leaf(80, 282, 77, 342, left = 5 to 3, right = 12 to 8, along = 0.25f to 0.75f))
        both(null, Foot)
    }

private val BackView: List<BodyPart> =
    buildList {
        middle(Muscle.Neck, svg("M 84 46 L 116 46 L 116 70 L 84 70 Z"))
        middle(null, Head)
        both(
            Muscle.LowerBack,
            svg("M 100 120 L 100 182 C 96 184 90 182 87 176 C 85 166 87 154 91 146 C 95 138 98 130 100 120 Z"),
        )
        both(
            Muscle.Traps,
            svg(
                "M 100 46 C 97 58 90 64 84 67 C 76 71 66 74 56 76 L 58 80 C 70 82 82 84 88 86 " +
                    "C 92 104 96 128 100 150 Z",
            ),
        )
        both(Muscle.Traps, svg("M 100 94 C 95 91 91 89 87 87 C 91 104 96 128 100 150 Z"))
        both(Muscle.Shoulders, Deltoid)
        both(Muscle.Lats, svg("M 56 108 C 68 114 82 114 92 110 C 95 124 92 142 83 158 C 74 150 64 140 56 132 Z"))
        // The shoulder blade: infraspinatus and teres, which the middle back's stat covers.
        both(
            Muscle.MiddleBack,
            svg("M 86 88 C 90 96 86 106 80 110 C 72 114 63 110 61 102 C 60 94 66 87 74 86 C 79 85 84 86 86 88 Z"),
        )
        both(Muscle.Triceps, leaf(44, 110, 38, 148, left = 8 to 7, right = 5 to 4, along = 0.1f to 0.9f))
        both(Muscle.Triceps, leaf(53, 103, 50, 148, left = 10 to 8, right = 11 to 8, along = 0.1f to 0.9f))
        // The elbow.
        both(null, leaf(44, 143, 44, 153, left = 5 to 5, right = 5 to 5, along = 0f to 1f))
        both(Muscle.Forearms, leaf(37, 148, 19, 186, left = 10 to 4, right = 6 to 2, along = 0.15f to 0.75f))
        both(Muscle.Forearms, leaf(49, 150, 25, 190, left = 7 to 3, right = 10 to 4, along = 0.15f to 0.8f))
        both(null, Hand)
        both(Muscle.Adductors, svg("M 100 214 C 96 216 92 222 90 230 C 89 244 90 256 92 270 L 100 270 Z"))
        // The iliotibial band.
        both(null, leaf(59, 212, 62, 268, left = 8 to 6, right = 2 to 2))
        both(Muscle.Hamstrings, leaf(68, 214, 71, 274, left = 10 to 8, right = 8 to 6, along = 0.1f to 0.85f))
        both(Muscle.Hamstrings, leaf(82, 216, 79, 274, left = 8 to 6, right = 9 to 6, along = 0.1f to 0.85f))
        // The gluteus medius, above the gluteus maximus.
        both(Muscle.Abductors, svg("M 54 168 C 62 166 70 168 75 172 C 68 177 63 185 61 196 L 54 196 Z"))
        both(
            Muscle.Glutes,
            svg(
                "M 100 178 C 96 170 86 166 78 167 C 68 168 62 178 62 192 C 62 206 68 216 80 219 " +
                    "C 88 221 95 219 100 216 Z",
            ),
        )
        // The back of the knee, then the soleus and the Achilles tendon.
        both(null, leaf(76, 266, 76, 292, left = 7 to 6, right = 7 to 6, along = 0.1f to 0.9f))
        both(null, leaf(62, 318, 68, 354, left = 5 to 2, right = 2 to 1))
        both(null, leaf(80, 320, 74, 354, left = 2 to 1, right = 5 to 2))
        both(Muscle.Calves, leaf(70, 276, 63, 326, left = 13 to 12, right = 6 to 6, along = 0.15f to 0.85f))
        both(Muscle.Calves, leaf(80, 284, 80, 330, left = 6 to 5, right = 10 to 8, along = 0.15f to 0.85f))
        both(null, Foot)
    }
