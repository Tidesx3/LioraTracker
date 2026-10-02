package app.liora.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import app.liora.core.model.PhotoPose
import java.io.File

/**
 * A stand-in progress photo, since tests can't use real ones: a figure against a wall, its waist
 * [waist] pixels wide, saved as a JPEG with the camera date [takenAt] (EXIF's `2026:08:01 08:00:00`).
 * Returns its location, as the photo picker would hand it over.
 */
internal fun syntheticPhoto(
    context: Context,
    pose: PhotoPose,
    waist: Float,
    takenAt: String,
): String {
    val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawFigure(pose, waist)
    val file = File.createTempFile("progress", ".jpg", context.cacheDir)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
    ExifInterface(file.path).apply {
        setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, takenAt)
        saveAttributes()
    }
    return Uri.fromFile(file).toString()
}

private fun Canvas.drawFigure(
    pose: PhotoPose,
    waist: Float,
) {
    drawColor(Color.rgb(54, 62, 70))
    drawRect(0f, 690f, WIDTH.toFloat(), HEIGHT.toFloat(), paint(Color.rgb(38, 42, 48)))
    val skin = paint(if (pose == PhotoPose.Back) Color.rgb(196, 152, 124) else Color.rgb(214, 170, 140))
    val center = WIDTH / 2f
    val side = pose == PhotoPose.Side
    val shoulders = if (side) 62f else 118f
    val halfWaist = (if (side) waist * 0.7f else waist) / 2
    drawCircle(center, 150f, 52f, skin)
    drawRect(center - 20, 190f, center + 20, 240f, skin)
    val torso =
        Path().apply {
            moveTo(center - shoulders, 232f)
            lineTo(center + shoulders, 232f)
            lineTo(center + halfWaist, 430f)
            lineTo(center - halfWaist, 430f)
            close()
        }
    drawPath(torso, skin)
    if (side) {
        drawRect(center - 18, 244f, center + 18, 460f, paint(Color.rgb(190, 148, 120)))
    } else {
        drawRect(center - shoulders - 34, 238f, center - shoulders - 4, 460f, skin)
        drawRect(center + shoulders + 4, 238f, center + shoulders + 34, 460f, skin)
    }
    drawRect(center - halfWaist - 6, 426f, center + halfWaist + 6, 520f, paint(Color.rgb(32, 120, 110)))
    if (side) {
        drawRect(center - 30, 520f, center + 30, 700f, skin)
    } else {
        drawRect(center - halfWaist, 520f, center - 8, 700f, skin)
        drawRect(center + 8, 520f, center + halfWaist, 700f, skin)
    }
}

private fun paint(color: Int) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
    }

private const val WIDTH = 600
private const val HEIGHT = 800
private const val QUALITY = 90
