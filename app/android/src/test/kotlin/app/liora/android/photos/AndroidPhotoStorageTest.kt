package app.liora.android.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import app.liora.android.TestLioraApplication
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Importing progress photos: shrunk, turned upright, dated by the camera, and kept in app storage. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class AndroidPhotoStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = AndroidPhotoStorage(context)
    private val vienna = TimeZone.of("Europe/Vienna")

    @After
    fun tearDown() = stopKoin()

    @Test
    fun aBigSidewaysPhotoComesInUprightAndSmaller() =
        runBlocking {
            // As a phone held upright saves it: landscape pixels, and an EXIF note to turn them.
            val source = jpeg(width = 3000, height = 1000)
            ExifInterface(source.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:08:01 07:45:00")
                saveAttributes()
            }

            val imported = checkNotNull(storage.import(Uri.fromFile(source).toString(), "photo-1"))

            val stored = File(imported.path)
            assertTrue(stored.path.startsWith(File(context.filesDir, "photos").path))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(stored.path, bounds)
            assertEquals(683 to 2048, bounds.outWidth to bounds.outHeight)
            assertEquals(LocalDateTime(2026, 8, 1, 7, 45).toInstant(TimeZone.currentSystemDefault()), imported.takenAt)
        }

    @Test
    fun aSmallPhotoKeepsItsSizeAndHasNoDate() =
        runBlocking {
            val imported = checkNotNull(storage.import(Uri.fromFile(jpeg(600, 800)).toString(), "photo-2"))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imported.path, bounds)
            assertEquals(600 to 800, bounds.outWidth to bounds.outHeight)
            assertNull(imported.takenAt)
        }

    @Test
    fun somethingThatIsNotAnImageIsTurnedAway() =
        runBlocking {
            val text = File(context.cacheDir, "notes.txt").apply { writeText("not a photo") }
            assertNull(storage.import(Uri.fromFile(text).toString(), "photo-3"))
            assertNull(storage.import(Uri.fromFile(File(context.cacheDir, "missing.jpg")).toString(), "photo-4"))
            assertFalse(File(context.filesDir, "photos/photo-3.jpg").exists())
        }

    @Test
    fun deletingRemovesTheFile() =
        runBlocking {
            val imported = checkNotNull(storage.import(Uri.fromFile(jpeg(300, 400)).toString(), "photo-5"))
            storage.delete(imported.path)
            assertFalse(File(imported.path).exists())
        }

    @Test
    fun exifDatesUseTheirOffsetOrElseThePhonesZone() {
        assertEquals(
            LocalDateTime(2026, 8, 1, 7, 45).toInstant(UtcOffset(hours = 2)),
            exifDateTaken("2026:08:01 07:45:00", "+02:00", vienna),
        )
        assertEquals(
            LocalDateTime(2026, 8, 1, 7, 45).toInstant(vienna),
            exifDateTaken("2026:08:01 07:45:00", null, vienna),
        )
        // Cameras without a set clock, and junk.
        assertNull(exifDateTaken("0000:00:00 00:00:00", null, vienna))
        assertNull(exifDateTaken("yesterday", null, vienna))
        assertNull(exifDateTaken(null, null, vienna))
    }

    private fun jpeg(
        width: Int,
        height: Int,
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        return File.createTempFile("source", ".jpg", context.cacheDir).apply {
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
    }
}
