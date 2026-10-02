package app.liora.android.backup

import android.content.Context
import androidx.core.net.toUri
import app.liora.core.data.backup.ArchiveWriter
import app.liora.core.data.backup.ExportFileException
import app.liora.core.data.backup.ExportFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Files the user picked through the system's file picker (the Storage Access Framework), by content
 * URI: on the phone, in Drive, on a USB stick. Backups are ZIP archives.
 */
internal class AndroidExportFiles(
    private val context: Context,
) : ExportFiles {
    override suspend fun writeText(
        destination: String,
        text: String,
    ) = io {
        output(destination).use { it.write(text.encodeToByteArray()) }
    }

    override suspend fun writeArchive(
        destination: String,
        entries: suspend ArchiveWriter.() -> Unit,
    ) = io {
        ZipOutputStream(output(destination).buffered()).use { zip ->
            ArchiveWriter { name, bytes -> zip.putEntry(name, bytes) }.entries()
        }
    }

    override suspend fun readArchive(
        source: String,
        names: Set<String>,
        onEntry: suspend (name: String, bytes: ByteArray) -> Unit,
    ) = io {
        val remaining = names.toMutableSet()
        input(source).use { stream ->
            ZipInputStream(stream.buffered()).use { zip ->
                while (remaining.isNotEmpty()) {
                    val entry = zip.nextEntryOrNull() ?: break
                    if (remaining.remove(entry.name)) onEntry(entry.name, zip.readBytes())
                }
            }
        }
    }

    private fun output(destination: String): OutputStream {
        val uri = destination.toUri()
        val resolver = context.contentResolver
        // "wt" truncates a file that already has content; a few providers only know "w", which is
        // enough for the new, empty file the picker made.
        val stream =
            try {
                resolver.openOutputStream(uri, "wt")
            } catch (_: IllegalArgumentException) {
                resolver.openOutputStream(uri, "w")
            } catch (_: FileNotFoundException) {
                resolver.openOutputStream(uri, "w")
            }
        return stream ?: throw IOException("Can't write to $destination")
    }

    private fun input(source: String): InputStream =
        context.contentResolver.openInputStream(source.toUri()) ?: throw IOException("Can't read $source")

    /** On the IO threads, with the platform's ways of failing as [ExportFileException]. */
    private suspend fun <T> io(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: IOException) {
                throw ExportFileException(e)
            } catch (e: SecurityException) {
                // The permission the picker gave ran out, or the file moved.
                throw ExportFileException(e)
            }
        }
}

private fun ZipOutputStream.putEntry(
    name: String,
    bytes: ByteArray,
) {
    val entry = ZipEntry(name)
    if (name.endsWith(".jpg")) {
        // JPEGs are compressed already; deflating them again only costs time.
        entry.method = ZipEntry.STORED
        entry.size = bytes.size.toLong()
        entry.compressedSize = bytes.size.toLong()
        entry.crc = CRC32().apply { update(bytes) }.value
    }
    putNextEntry(entry)
    write(bytes)
    closeEntry()
}

/** The next entry; null at the end, and for a file that isn't a ZIP archive at all. */
private fun ZipInputStream.nextEntryOrNull(): ZipEntry? =
    try {
        nextEntry
    } catch (_: ZipException) {
        null
    }
