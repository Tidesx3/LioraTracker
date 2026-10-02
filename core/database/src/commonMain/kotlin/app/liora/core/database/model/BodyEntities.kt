package app.liora.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A body measurement in canonical units (kg, %, m), keyed by a stable type key like `waist`. Keys this
 * app version doesn't know (synced from a newer one) stay in the table and are skipped when read.
 */
@Entity(tableName = "measurement", indices = [Index("type", "taken_at")])
data class MeasurementEntity(
    @PrimaryKey override val id: String,
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    val type: String,
    val value: Double,
    @Embedded override val sync: SyncMetadata,
) : SyncedRow

/** A progress photo; [pose] is a stable key like `front`. */
@Entity(tableName = "progress_photo", indices = [Index("taken_at")])
data class ProgressPhotoEntity(
    @PrimaryKey override val id: String,
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    val pose: String?,
    /** App-private file; photos sync as blobs, not as table data. */
    @ColumnInfo(name = "local_path") val localPath: String?,
    @ColumnInfo(name = "blob_id") val blobId: String?,
    val notes: String?,
    @Embedded override val sync: SyncMetadata,
) : SyncedRow

/** User preferences that follow the user across devices (units, rest defaults, plates, ...). */
@Entity(tableName = "preference")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
    @Embedded override val sync: SyncMetadata,
) : Syncable

/** Device-local bookkeeping that must never sync (device id, seed version, sync cursors). */
@Entity(tableName = "local_meta")
data class LocalMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
