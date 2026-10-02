package app.liora.core.data.body

import app.liora.core.common.IdGenerator
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.dao.ProgressPhotoDao
import app.liora.core.database.model.ProgressPhotoEntity
import app.liora.core.model.PhotoPose
import app.liora.core.model.ProgressPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Where progress photos' image files live; the platform provides it (app-private files on Android). */
interface PhotoStorage {
    /**
     * Copies the image at [source], a platform location such as an Android content URI, into storage
     * under [id], sized for a phone's screen. Null when it can't be read as an image.
     */
    suspend fun import(
        source: String,
        id: String,
    ): ImportedPhoto?

    suspend fun delete(path: String)
}

/** An image in storage, and when the camera says it was taken, if it says. */
data class ImportedPhoto(
    val path: String,
    val takenAt: Instant?,
)

/** Progress photos: added from the camera or the gallery, tagged with a pose, and compared. */
interface ProgressPhotoRepository {
    /** Every photo on this device, oldest first. */
    val photos: Flow<List<ProgressPhoto>>

    /**
     * Adds the image at [source] (see [PhotoStorage.import]). It keeps the date the camera recorded, so
     * older photos land on the day they were taken, or else now. Null when the image can't be read.
     */
    suspend fun add(
        source: String,
        pose: PhotoPose?,
    ): ProgressPhoto?

    suspend fun setPose(
        id: String,
        pose: PhotoPose?,
    )

    /** Moves the photo to [day] in [zone]'s calendar, at the same time of day. */
    suspend fun moveTo(
        id: String,
        day: LocalDate,
        zone: TimeZone,
    )

    /** Removes the photo, image file included. */
    suspend fun delete(id: String)
}

internal class OfflineProgressPhotoRepository(
    private val dao: ProgressPhotoDao,
    private val storage: PhotoStorage,
    private val ids: IdGenerator,
    private val stamper: SyncStamper,
    private val clock: Clock,
) : ProgressPhotoRepository {
    // Photos synced from another device have no file here until Phase 2 downloads them.
    override val photos: Flow<List<ProgressPhoto>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toModel() } }

    override suspend fun add(
        source: String,
        pose: PhotoPose?,
    ): ProgressPhoto? {
        val id = ids.newId()
        val imported = storage.import(source, id) ?: return null
        val now = clock.now()
        // A camera with its clock set wrong can't date a photo into the future.
        val takenAt = imported.takenAt?.coerceAtMost(now) ?: now
        val row =
            ProgressPhotoEntity(
                id = id,
                takenAt = takenAt.toEpochMilliseconds(),
                pose = pose?.key,
                localPath = imported.path,
                blobId = null,
                notes = null,
                sync = stamper.newRow(),
            )
        dao.upsert(row)
        return row.toModel()
    }

    override suspend fun setPose(
        id: String,
        pose: PhotoPose?,
    ) = change(id) { copy(pose = pose?.key) }

    override suspend fun moveTo(
        id: String,
        day: LocalDate,
        zone: TimeZone,
    ) = change(id) {
        val time = Instant.fromEpochMilliseconds(takenAt).toLocalDateTime(zone).time
        copy(takenAt = LocalDateTime(day, time).toInstant(zone).toEpochMilliseconds())
    }

    override suspend fun delete(id: String) {
        val row = dao.get(id)?.takeIf { it.sync.deletedAt == null } ?: return
        dao.upsert(row.copy(sync = stamper.tombstone(row.sync)))
        row.localPath?.let { storage.delete(it) }
    }

    private suspend fun change(
        id: String,
        edit: ProgressPhotoEntity.() -> ProgressPhotoEntity,
    ) {
        val row = dao.get(id)?.takeIf { it.sync.deletedAt == null } ?: return
        dao.upsert(row.edit().copy(sync = stamper.touch(row.sync)))
    }
}

private fun ProgressPhotoEntity.toModel(): ProgressPhoto? =
    localPath?.let { path ->
        ProgressPhoto(id, Instant.fromEpochMilliseconds(takenAt), pose?.let(PhotoPose::fromKey), path)
    }
