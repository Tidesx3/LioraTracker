package app.liora.core.database.model

import androidx.room.ColumnInfo

/**
 * Columns every synced row carries. Rows are never hard-deleted: [deletedAt] marks a tombstone so the
 * deletion itself can sync. [hlc] orders concurrent edits (last writer wins); [dirty] marks rows
 * changed locally since the last successful push.
 */
data class SyncMetadata(
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "hlc") val hlc: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "dirty") val dirty: Boolean = true,
)

/** A row that syncs, whatever its key. */
interface Syncable {
    val sync: SyncMetadata
}

/** A synced row with its own id, so repositories can diff and stamp rows generically. */
interface SyncedRow : Syncable {
    val id: String
}
