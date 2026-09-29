package app.liora.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A body measurement in canonical units (kg, cm, %), keyed by a stable type key like `waist`. */
@Entity(tableName = "measurement", indices = [Index("type", "taken_at")])
data class MeasurementEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    val type: String,
    val value: Double,
    @Embedded val sync: SyncMetadata,
)

@Entity(tableName = "progress_photo", indices = [Index("taken_at")])
data class ProgressPhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    val pose: String?,
    /** App-private file; photos sync as blobs, not as table data. */
    @ColumnInfo(name = "local_path") val localPath: String?,
    @ColumnInfo(name = "blob_id") val blobId: String?,
    val notes: String?,
    @Embedded val sync: SyncMetadata,
)

/** User preferences that follow the user across devices (units, rest defaults, plates, ...). */
@Entity(tableName = "preference")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
    @Embedded val sync: SyncMetadata,
)

/** Device-local bookkeeping that must never sync (device id, seed version, sync cursors). */
@Entity(tableName = "local_meta")
data class LocalMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
