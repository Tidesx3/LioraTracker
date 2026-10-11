package app.liora.android.importing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.Reader
import java.util.UUID

/**
 * Routine files handed to Liora (docs/routine-format.md): an AI chat's answer shared as text, or a file shared
 * or opened with Liora. What came is copied into the app's cache at once, while Liora may still read it, so the
 * import screen reads it at leisure (`RoutineImportRoute`), even after Android ends the process meanwhile.
 */
internal class SharedRoutines(
    private val context: Context,
) {
    /** Whether [intent] hands Liora something to import as routines. */
    fun isFor(intent: Intent?): Boolean = intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_VIEW

    /**
     * Copies what [intent] hands over into the cache and returns where it is now, for the import screen; null
     * when it holds nothing Liora can read.
     */
    suspend fun receive(intent: Intent): String? =
        withContext(Dispatchers.IO) {
            val stream =
                if (intent.action == Intent.ACTION_SEND) {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.data
                }
            val text =
                stream?.let(::read)
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(MAX_CHARS)
                    ?: return@withContext null
            val folder = File(context.cacheDir, FOLDER).apply { mkdirs() }
            // Each import reads its own; older ones are left to the system, which clears the cache as it needs.
            val file = File(folder, "${UUID.randomUUID()}.json").apply { writeText(text) }
            Uri.fromFile(file).toString()
        }

    /** The text of the file at [uri], at most [MAX_CHARS] of it; null when it can't be read. */
    private fun read(uri: Uri): String? =
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.reader().buffered().use { it.readAtMost(MAX_CHARS) }
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }

    /** Up to [limit] characters, however the reader hands them over. */
    private fun Reader.readAtMost(limit: Int): String {
        val buffer = CharArray(limit)
        var length = 0
        while (length < limit) {
            val read = read(buffer, length, limit - length)
            if (read < 0) break
            length += read
        }
        return String(buffer, 0, length)
    }

    private companion object {
        const val FOLDER = "routines"

        /** A program of routines is a few dozen kilobytes; anything far bigger isn't one. */
        const val MAX_CHARS = 1_000_000
    }
}
