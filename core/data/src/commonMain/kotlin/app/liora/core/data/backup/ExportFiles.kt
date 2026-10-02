package app.liora.core.data.backup

/**
 * Files the user picked to export to or import from: platform locations such as Android content URIs.
 * The platform does the reading and writing. Backups are ZIP archives, so they can hold the photos'
 * images next to the JSON. Throws [ExportFileException] when a file can't be read or written.
 */
interface ExportFiles {
    /** Writes [text] as UTF-8 to [destination], replacing what was there. */
    suspend fun writeText(
        destination: String,
        text: String,
    )

    /** Writes a ZIP archive to [destination], replacing what was there; [entries] adds its files in order. */
    suspend fun writeArchive(
        destination: String,
        entries: suspend ArchiveWriter.() -> Unit,
    )

    /**
     * Reads the files named [names] from the ZIP archive at [source], in the archive's order, and stops
     * once it has them all. Names it doesn't hold are skipped; a file that isn't an archive holds none.
     */
    suspend fun readArchive(
        source: String,
        names: Set<String>,
        onEntry: suspend (name: String, bytes: ByteArray) -> Unit,
    )
}

/** Adds files to an archive being written. */
fun interface ArchiveWriter {
    suspend fun add(
        name: String,
        bytes: ByteArray,
    )
}

/** A file the user picked couldn't be read or written: it's gone, access was withdrawn, or the disk is full. */
class ExportFileException(
    cause: Throwable,
) : Exception(cause.message, cause)
