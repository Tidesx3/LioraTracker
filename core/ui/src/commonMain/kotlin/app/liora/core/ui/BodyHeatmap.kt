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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
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

/** The color a [load] gets on the heatmap: the body's neutral tone, then ever stronger primary. */
@Composable
fun muscleLoadColor(load: MuscleLoad): Color =
    when (load) {
        MuscleLoad.None -> MaterialTheme.colorScheme.surfaceContainerHighest
        MuscleLoad.Light -> MaterialTheme.colorScheme.primary.copy(alpha = LIGHT_ALPHA)
        MuscleLoad.Moderate -> MaterialTheme.colorScheme.primary.copy(alpha = MODERATE_ALPHA)
        MuscleLoad.Full -> MaterialTheme.colorScheme.primary
    }

/**
 * A body seen from the front and the back, each muscle shaded by how many hard sets it got. A simple
 * figure of rounded shapes: clear at a glance, and easy to restyle later.
 */
@Composable
fun BodyHeatmap(
    setsPerMuscle: Map<Muscle, Double>,
    modifier: Modifier = Modifier,
) {
    val colors = MuscleLoad.entries.associateWith { muscleLoadColor(it) }
    val neutral = MaterialTheme.colorScheme.surfaceContainerHigh
    val colorOf = { muscle: Muscle? ->
        if (muscle == null) neutral else colors.getValue(MuscleLoad.of(setsPerMuscle[muscle] ?: 0.0))
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BodyView(stringResource(Res.string.body_front), FrontView, colorOf, Modifier.weight(1f))
        BodyView(stringResource(Res.string.body_back), BackView, colorOf, Modifier.weight(1f))
    }
}

@Composable
private fun BodyView(
    label: String,
    shapes: List<BodyShape>,
    colorOf: (Muscle?) -> Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(WIDTH / HEIGHT)) {
            val factor = size.width / WIDTH
            scale(factor, pivot = Offset.Zero) {
                shapes.forEach { it.draw(this, colorOf(it.muscle)) }
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One part of the figure, in a 100 × 200 box; null [muscle] is a part no muscle stat covers. */
private sealed interface BodyShape {
    val muscle: Muscle?

    fun draw(
        scope: DrawScope,
        color: Color,
    )

    class Oval(
        override val muscle: Muscle?,
        val rect: Rect,
    ) : BodyShape {
        override fun draw(
            scope: DrawScope,
            color: Color,
        ) = scope.drawOval(color, rect.topLeft, rect.size)
    }

    class Block(
        override val muscle: Muscle?,
        val rect: Rect,
        val radius: Float = 4f,
    ) : BodyShape {
        override fun draw(
            scope: DrawScope,
            color: Color,
        ) = scope.drawRoundRect(color, rect.topLeft, rect.size, CornerRadius(radius))
    }

    class Polygon(
        override val muscle: Muscle?,
        val points: List<Offset>,
    ) : BodyShape {
        override fun draw(
            scope: DrawScope,
            color: Color,
        ) {
            val path =
                Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
            scope.drawPath(path, color)
        }
    }
}

private const val WIDTH = 100f
private const val HEIGHT = 200f
private const val LIGHT_ALPHA = 0.35f
private const val MODERATE_ALPHA = 0.65f

private fun rect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
) = Rect(left, top, right, bottom)

/** [shape] and its mirror image across the body's middle. */
private fun mirrored(shape: BodyShape): List<BodyShape> =
    listOf(
        shape,
        when (shape) {
            is BodyShape.Oval -> BodyShape.Oval(shape.muscle, shape.rect.mirror())
            is BodyShape.Block -> BodyShape.Block(shape.muscle, shape.rect.mirror(), shape.radius)
            is BodyShape.Polygon -> BodyShape.Polygon(shape.muscle, shape.points.map { Offset(WIDTH - it.x, it.y) })
        },
    )

private fun Rect.mirror() = Rect(WIDTH - right, top, WIDTH - left, bottom)

/** What both views share: head, arms below the elbow, hands, knees, feet. */
private val Common: List<BodyShape> =
    listOf(BodyShape.Oval(null, rect(41f, 2f, 59f, 22f))) +
        mirrored(BodyShape.Block(Muscle.Forearms, rect(22f, 67f, 31f, 92f))) +
        mirrored(BodyShape.Oval(null, rect(21f, 92f, 30f, 102f))) +
        mirrored(BodyShape.Oval(Muscle.Shoulders, rect(26f, 30f, 40f, 46f))) +
        mirrored(BodyShape.Oval(null, rect(37f, 139f, 46f, 147f))) +
        mirrored(BodyShape.Block(null, rect(35f, 182f, 46f, 190f), radius = 3f))

private val FrontView: List<BodyShape> =
    Common +
        listOf(
            BodyShape.Block(Muscle.Neck, rect(45f, 20f, 55f, 28f), radius = 2f),
            BodyShape.Block(Muscle.Abdominals, rect(41f, 51f, 59f, 84f)),
            BodyShape.Block(null, rect(40f, 84f, 60f, 94f)),
            BodyShape.Block(Muscle.Adductors, rect(47.5f, 95f, 52.5f, 122f), radius = 2f),
        ) +
        mirrored(BodyShape.Polygon(Muscle.Traps, listOf(Offset(45f, 24f), Offset(38f, 32f), Offset(45f, 30f)))) +
        mirrored(BodyShape.Block(Muscle.Chest, rect(38.5f, 32f, 49.5f, 50f))) +
        mirrored(BodyShape.Block(Muscle.Biceps, rect(25f, 46f, 33f, 66f))) +
        // The obliques have no stat of their own.
        mirrored(BodyShape.Block(null, rect(37f, 52f, 40.5f, 82f), radius = 2f)) +
        mirrored(BodyShape.Block(Muscle.Abductors, rect(34f, 84f, 39.5f, 104f), radius = 3f)) +
        mirrored(BodyShape.Block(Muscle.Quadriceps, rect(35f, 94f, 47f, 140f), radius = 5f)) +
        mirrored(BodyShape.Block(null, rect(37f, 147f, 46f, 182f)))

private val BackView: List<BodyShape> =
    Common +
        listOf(
            BodyShape.Block(null, rect(45f, 20f, 55f, 28f), radius = 2f),
            BodyShape.Block(Muscle.MiddleBack, rect(43f, 40f, 57f, 64f), radius = 3f),
            BodyShape.Polygon(
                Muscle.Traps,
                listOf(
                    Offset(50f, 22f),
                    Offset(38f, 32f),
                    Offset(44f, 40f),
                    Offset(50f, 52f),
                    Offset(56f, 40f),
                    Offset(62f, 32f),
                ),
            ),
            BodyShape.Block(Muscle.LowerBack, rect(42f, 65f, 58f, 84f), radius = 3f),
        ) +
        mirrored(
            BodyShape.Polygon(
                Muscle.Lats,
                listOf(Offset(38f, 40f), Offset(42.5f, 42f), Offset(42.5f, 70f), Offset(40f, 76f), Offset(36f, 56f)),
            ),
        ) +
        mirrored(BodyShape.Block(Muscle.Triceps, rect(25f, 46f, 33f, 66f))) +
        mirrored(BodyShape.Block(Muscle.Glutes, rect(36f, 84f, 49.5f, 104f), radius = 7f)) +
        mirrored(BodyShape.Block(Muscle.Hamstrings, rect(36f, 105f, 48f, 140f), radius = 5f)) +
        mirrored(BodyShape.Oval(Muscle.Calves, rect(37f, 145f, 47f, 174f))) +
        mirrored(BodyShape.Block(null, rect(38.5f, 172f, 45.5f, 182f), radius = 2f))
