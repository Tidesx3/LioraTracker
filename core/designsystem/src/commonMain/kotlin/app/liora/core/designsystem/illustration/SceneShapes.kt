package app.liora.core.designsystem.illustration

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser

internal fun svg(pathData: String): Path = PathParser().parsePathString(pathData).toPath()

internal fun rect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float = 0f,
): Path = Path().apply { addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(radius))) }

/** A rectangle rounded only at the top, like a calendar's header. */
internal fun tab(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
): Path =
    Path().apply {
        addRoundRect(
            RoundRect(
                left,
                top,
                right,
                bottom,
                topLeftCornerRadius = CornerRadius(radius),
                topRightCornerRadius = CornerRadius(radius),
                bottomRightCornerRadius = CornerRadius.Zero,
                bottomLeftCornerRadius = CornerRadius.Zero,
            ),
        )
    }

internal fun circle(
    x: Float,
    y: Float,
    radius: Float,
): Path = oval(x, y, radius, radius)

internal fun oval(
    x: Float,
    y: Float,
    radiusX: Float,
    radiusY: Float,
): Path =
    Path().apply {
        addOval(
            Rect(Offset(x - radiusX, y - radiusY), Size(radiusX * 2, radiusY * 2)),
        )
    }

/**
 * Part of a circle's outline, from [startDegrees] (0 points right, clockwise) on by [sweepDegrees]; with a
 * [radiusY] of its own, part of an oval's.
 */
internal fun arc(
    x: Float,
    y: Float,
    radius: Float,
    startDegrees: Float,
    sweepDegrees: Float,
    radiusY: Float = radius,
): Path =
    Path().apply {
        arcTo(
            Rect(Offset(x - radius, y - radiusY), Size(radius * 2, radiusY * 2)),
            startDegrees,
            sweepDegrees,
            forceMoveTo = true,
        )
    }

/** A four-pointed star with curved sides, [radius] from its middle to each point. */
internal fun sparkle(
    x: Float,
    y: Float,
    radius: Float,
): Path =
    Path().apply {
        moveTo(x, y - radius)
        quadraticTo(x, y, x + radius, y)
        quadraticTo(x, y, x, y + radius)
        quadraticTo(x, y, x - radius, y)
        quadraticTo(x, y, x, y - radius)
        close()
    }

/** A plus sign, [radius] from its middle to each end, its arms [thickness] wide. */
internal fun plus(
    x: Float,
    y: Float,
    radius: Float,
    thickness: Float,
): Path {
    val half = thickness / 2
    return Path().apply {
        addPath(rect(x - radius, y - half, x + radius, y + half, half))
        addPath(rect(x - half, y - radius, x + half, y + radius, half))
    }
}

/** This path turned clockwise by [degrees] around ([x], [y]). */
internal fun Path.rotated(
    degrees: Float,
    x: Float,
    y: Float,
): Path =
    Path().also {
        it.addPath(this)
        it.transform(
            Matrix().apply {
                translate(x, y)
                rotateZ(degrees)
                translate(-x, -y)
            },
        )
    }

/** This path flipped across the scene's middle, x = 120. */
internal fun Path.mirrored(): Path =
    Path().also {
        it.addPath(this)
        it.transform(Mirror)
    }

private val Mirror =
    Matrix().apply {
        values[Matrix.ScaleX] = -1f
        values[Matrix.TranslateX] = SCENE_WIDTH
    }
