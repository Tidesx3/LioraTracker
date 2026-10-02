package app.liora.android.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.liora.android.TestLioraApplication
import app.liora.android.photos.AndroidPhotoStorage
import app.liora.core.data.backup.ExportFileException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Backup archives and exports written to and read from the files the user picks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestLioraApplication::class)
class AndroidExportFilesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val files = AndroidExportFiles(context)
    private val photos = AndroidPhotoStorage(context)

    @After
    fun tearDown() = stopKoin()

    @Test
    fun anArchiveReadsBackWhatWasWritten() =
        runBlocking {
            val archive = file("backup.zip")
            val image = ByteArray(5_000) { (it % 251).toByte() }
            files.writeArchive(archive.uri()) {
                add("liora.json", """{"format": "liora-backup"}""".encodeToByteArray())
                add("photos/a.jpg", image)
                add("photos/b.jpg", byteArrayOf(1, 2, 3))
            }

            // Images go in as they are; there's nothing left to compress in a JPEG.
            ZipFile(archive).use { zip ->
                assertEquals(ZipEntry.DEFLATED, zip.getEntry("liora.json").method)
                assertEquals(ZipEntry.STORED, zip.getEntry("photos/a.jpg").method)
            }
            val read = mutableMapOf<String, ByteArray>()
            val wanted = setOf("photos/a.jpg", "photos/missing.jpg")
            files.readArchive(archive.uri(), wanted) { name, bytes -> read[name] = bytes }
            assertEquals(setOf("photos/a.jpg"), read.keys)
            assertArrayEquals(image, read.getValue("photos/a.jpg"))
        }

    @Test
    fun aFileThatIsntAnArchiveHoldsNothing() =
        runBlocking {
            val text = file("workouts.csv")
            files.writeText(text.uri(), "start,end\r\n")
            assertEquals("start,end\r\n", text.readText())

            var entries = 0
            files.readArchive(text.uri(), setOf("liora.json")) { _, _ -> entries++ }
            assertEquals(0, entries)
        }

    @Test(expected = ExportFileException::class)
    fun aFileThatsGoneFailsAsAFileProblem() =
        runBlocking {
            files.readArchive(file("gone.zip").uri(), setOf("liora.json")) { _, _ -> }
        }

    @Test
    fun photosGoBackIntoStorageAsTheyWere() =
        runBlocking {
            val bytes = byteArrayOf(9, 8, 7)
            val path = checkNotNull(photos.restore("0192-photo", bytes))

            assertTrue(path.startsWith(File(context.filesDir, "photos").path))
            assertArrayEquals(bytes, photos.read(path))
            // Restoring again replaces it.
            assertEquals(path, photos.restore("0192-photo", byteArrayOf(1)))
            assertArrayEquals(byteArrayOf(1), photos.read(path))
            // An id from a tampered backup can't reach outside the photos.
            assertNull(photos.restore("../databases/liora", bytes))
            assertNull(photos.read(File(context.filesDir, "photos/nothing.jpg").path))
        }

    private fun file(name: String) = File(context.cacheDir, name).also { it.delete() }

    private fun File.uri() = Uri.fromFile(this).toString()
}
