package app.liora.android.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import app.liora.core.data.body.ImportedPhoto
import app.liora.core.data.body.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * Progress photos as JPEGs in app-private storage (`files/photos`), off the phone's gallery and out of
 * other apps' reach. Each import is turned upright and scaled to [MAX_EDGE] pixels on its long side:
 * sharp on any phone, and a year of weekly photos stays around a hundred megabytes.
 */
internal class AndroidPhotoStorage(
    private val context: Context,
) : PhotoStorage {
    private val directory: File get() = File(context.filesDir, DIRECTORY)

    override suspend fun import(
        source: String,
        id: String,
    ): ImportedPhoto? =
        withContext(Dispatchers.IO) {
            val uri = source.toUri()
            try {
                decode(uri)?.let { decoded ->
                    val exif = readExif(uri)
                    val upright = decoded.oriented(exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0) ?: 0)
                    ImportedPhoto(
                        path = store(upright, id),
                        takenAt =
                            exifDateTaken(
                                exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                                exif?.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
                                TimeZone.currentSystemDefault(),
                            ),
                    )
                }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                // The picker's permission to read it ran out.
                null
            }
        }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) { File(path).delete() }
    }

    /** The image at most [MAX_EDGE] pixels on its long side; null when it isn't an image. */
    private fun decode(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        // Measuring only fills in the options; the decode itself returns nothing.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        // Halve while decoding as far as possible without going below the target, then scale the rest.
        var sample = 1
        while (longEdge / (sample * 2) >= MAX_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.scaledDown()
    }

    /** The camera's notes; null when there are none to read, which only costs the date and orientation. */
    private fun readExif(uri: Uri): ExifInterface? =
        try {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        } catch (_: IOException) {
            null
        }

    /** Saves [bitmap] as [id]'s JPEG and returns its path. */
    private fun store(
        bitmap: Bitmap,
        id: String,
    ): String {
        directory.mkdirs()
        val file = File(directory, "$id.jpg")
        // Written aside and renamed, so a crash midway never leaves half an image.
        val partial = File(directory, "$id.part")
        partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        if (!partial.renameTo(file)) throw IOException("Couldn't store $id")
        return file.absolutePath
    }

    private fun Bitmap.scaledDown(): Bitmap {
        val longEdge = max(width, height)
        if (longEdge <= MAX_EDGE) return this
        val factor = MAX_EDGE.toFloat() / longEdge
        val scaled = scale((width * factor).roundToInt(), (height * factor).roundToInt())
        if (scaled !== this) recycle()
        return scaled
    }

    private companion object {
        const val DIRECTORY = "photos"
        const val MAX_EDGE = 2048
        const val JPEG_QUALITY = 88
    }
}

/** The image as it should be seen, given its EXIF orientation (cameras store it as the sensor saw it). */
private fun Bitmap.oriented(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
            matrix.setScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_ROTATE_180 -> {
            matrix.setRotate(HALF_TURN)
        }

        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setScale(1f, -1f)
        }

        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(QUARTER_TURN)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_ROTATE_90 -> {
            matrix.setRotate(QUARTER_TURN)
        }

        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-QUARTER_TURN)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_ROTATE_270 -> {
            matrix.setRotate(-QUARTER_TURN)
        }

        else -> {
            return this
        }
    }
    val turned = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (turned !== this) recycle()
    return turned
}

/**
 * When the camera says it took the photo: EXIF's `2026:08:01 08:00:00`, in the offset it recorded or,
 * as most cameras leave that out, in [zone]. Null when there's no readable date.
 */
internal fun exifDateTaken(
    dateTime: String?,
    offset: String?,
    zone: TimeZone,
): Instant? {
    val local = dateTime?.trim()?.let(::parseExifDateTime) ?: return null
    val recorded = offset?.trim()?.let { runCatching { UtcOffset.parse(it) }.getOrNull() }
    return if (recorded != null) local.toInstant(recorded) else local.toInstant(zone)
}

/** `2026:08:01 08:00:00`; null for anything else, such as the zeros a camera without a set clock writes. */
private fun parseExifDateTime(text: String): LocalDateTime? {
    if (!EXIF_DATE_TIME.matches(text)) return null
    // The date's colons become dashes, and the space a T: ISO 8601.
    val iso = text.replaceFirst(':', '-').replaceFirst(':', '-').replace(' ', 'T')
    return runCatching { LocalDateTime.parse(iso) }.getOrNull()
}

private val EXIF_DATE_TIME = Regex("""\d{4}:\d{2}:\d{2} \d{2}:\d{2}:\d{2}""")
private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f
