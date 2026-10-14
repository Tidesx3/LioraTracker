package app.liora.core.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.theme.LioraTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Confetti bursting from the middle of its bounds, once, as it first shows: for a personal record. The
 * pieces fly out mostly upwards, tumble and fall, and fade; they're drawn past its bounds, so give it a
 * small spot such as the trophy and keep it above its neighbours. It takes no input and holds nothing
 * up. [seed] picks the pieces, so the same burst looks the same every time (and in screenshots). [colors]
 * replaces a record's gold for other moments.
 */
@Composable
fun ConfettiBurst(
    modifier: Modifier = Modifier,
    seed: Int = 0,
    reach: Dp = DefaultReach,
    colors: List<Color>? = null,
) {
    val palette =
        colors ?: listOf(
            LioraTheme.colors.personalRecord,
            MaterialTheme.colorScheme.primary,
            LioraTheme.colors.dropSet,
            LioraTheme.colors.personalRecord.copy(alpha = LIGHTER),
        )
    val pieces = remember(seed) { confetti(Random(seed)) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(BURST_MILLIS, easing = LinearEasing)) }
    Spacer(
        modifier.drawBehind {
            val done = progress.value
            if (done > 0f && done < 1f) pieces.forEach { drawPiece(it, done, palette, reach.toPx()) }
        },
    )
}

/** One piece of confetti: where it flies, how far, how it turns, and what it looks like. */
private class Piece(
    val angle: Float,
    val distance: Float,
    val spin: Float,
    val width: Float,
    val height: Float,
    val color: Int,
)

private fun confetti(random: Random): List<Piece> =
    List(PIECES) {
        // Mostly upwards: an arc from the left round over the top to the right, with a few going lower.
        val angle = (PI + random.nextDouble() * PI * UP_SHARE + PI * (1 - UP_SHARE) / 2).toFloat()
        Piece(
            angle = if (random.nextFloat() < LOW_SHARE) angle + PI.toFloat() else angle,
            distance = MIN_DISTANCE + random.nextFloat() * (1 - MIN_DISTANCE),
            spin = (random.nextFloat() - HALF) * MAX_SPIN,
            width = MIN_SIZE + random.nextFloat() * SIZE_RANGE,
            height = (MIN_SIZE + random.nextFloat() * SIZE_RANGE) * FLAT,
            color = random.nextInt(COLORS),
        )
    }

private fun DrawScope.drawPiece(
    piece: Piece,
    done: Float,
    colors: List<Color>,
    reach: Float,
) {
    // Out fast and slowing, while gravity takes over.
    val left = 1 - done
    val out = 1 - left * left * left
    val x = center.x + cos(piece.angle) * piece.distance * reach * out
    val y = center.y + sin(piece.angle) * piece.distance * reach * out + reach * FALL * done * done
    val fade = if (done < FADE_FROM) 1f else (1 - done) / (1 - FADE_FROM)
    val size = Size(piece.width.dp.toPx(), piece.height.dp.toPx())
    rotate(piece.spin * done, Offset(x, y)) {
        drawRect(
            color = colors[piece.color % colors.size],
            topLeft = Offset(x - size.width / 2, y - size.height / 2),
            size = size,
            alpha = fade,
        )
    }
}

private val DefaultReach = 56.dp
private const val BURST_MILLIS = 900
private const val PIECES = 22
private const val COLORS = 4
private const val UP_SHARE = 1.2
private const val LOW_SHARE = 0.15f
private const val MIN_DISTANCE = 0.45f
private const val MAX_SPIN = 720f
private const val MIN_SIZE = 4f
private const val SIZE_RANGE = 3f
private const val FLAT = 0.6f
private const val FALL = 0.6f
private const val FADE_FROM = 0.6f
private const val HALF = 0.5f
private const val LIGHTER = 0.6f
