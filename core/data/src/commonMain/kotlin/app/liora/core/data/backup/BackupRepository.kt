package app.liora.core.data.backup

import app.liora.core.data.body.PhotoStorage
import app.liora.core.database.TransactionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Backups of everything the user made, in one file they keep where they like (see [BackupJson] for
 * what's in it). Restoring merges a backup into this device rather than replacing what's here, so it
 * never loses work: each row keeps whichever version of it is newer.
 */
interface BackupRepository {
    /**
     * Writes a backup to [destination] (see [ExportFiles]). It finishes even when the screen that
     * asked for it closes. Throws [ExportFileException] when the file can't be written.
     */
    suspend fun backUp(destination: String)

    /**
     * The backup at [source], to look at before restoring it. Throws [InvalidBackupException] when the
     * file isn't a backup, [NewerBackupException] when it's from a newer app, and [ExportFileException]
     * when it can't be read.
     */
    suspend fun read(source: String): BackupContents

    /**
     * Adds what [contents] holds to this device, images included: rows that aren't here yet, and newer
     * versions of rows that are. Rows changed here since, and deletions made since, stay. Returns how
     * many rows changed; none when this device had it all already.
     */
    suspend fun restore(contents: BackupContents): Int
}

/** A backup read from a file, with what it holds. */
class BackupContents internal constructor(
    internal val source: String,
    internal val backup: Backup,
) {
    val exportedAt: Instant get() = Instant.fromEpochMilliseconds(backup.exportedAt)
    val workouts: Int get() = backup.workouts.size
    val routines: Int get() = backup.routines.size
    val customExercises: Int get() = backup.exercises.size
    val measurements: Int get() = backup.measurements.size
    val photos: Int get() = backup.photos.size
}

internal class OfflineBackupRepository(
    private val tables: SyncedTables,
    private val files: ExportFiles,
    private val photoStorage: PhotoStorage,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) : BackupRepository {
    // A backup half-written, or images restored without their rows, would be worse than finishing
    // after the screen that started it has gone.
    override suspend fun backUp(destination: String) =
        withContext(NonCancellable) {
            val snapshot =
                transactions.inTransaction {
                    tables.snapshot(RowSelection.Live, clock.now().toEpochMilliseconds(), ::photoFile)
                }
            val json = withContext(Dispatchers.Default) { BackupJson.encode(snapshot.backup) }
            files.writeArchive(destination) {
                add(BACKUP_JSON, json.encodeToByteArray())
                for ((file, path) in snapshot.images) photoStorage.read(path)?.let { add(file, it) }
            }
        }

    override suspend fun read(source: String): BackupContents {
        var json: ByteArray? = null
        files.readArchive(source, setOf(BACKUP_JSON)) { _, bytes -> json = bytes }
        val text = json?.decodeToString() ?: throw InvalidBackupException("No $BACKUP_JSON in the file")
        return BackupContents(source, withContext(Dispatchers.Default) { BackupJson.decode(text) })
    }

    override suspend fun restore(contents: BackupContents): Int =
        withContext(NonCancellable) {
            val backup = contents.backup
            // Images first, so a restored photo never points at a file that isn't there yet.
            val wanted = tables.imagesWanted(backup)
            val images = mutableMapOf<String, String>()
            if (wanted.isNotEmpty()) {
                files.readArchive(contents.source, wanted.keys) { file, bytes ->
                    val id = wanted.getValue(file)
                    photoStorage.restore(id, bytes)?.let { images[id] = it }
                }
            }
            // Restored rows may be news to the sync store too, so they're pushed with the next sync.
            val merged = transactions.inTransaction { tables.merge(backup, images, dirty = true) }
            merged.deletedImages.forEach { photoStorage.delete(it) }
            merged.written
        }
}
