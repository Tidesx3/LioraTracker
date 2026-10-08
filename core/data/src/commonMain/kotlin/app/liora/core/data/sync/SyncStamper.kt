package app.liora.core.data.sync

import app.liora.core.common.Hlc
import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.database.dao.LocalMetaDao
import app.liora.core.database.model.LocalMetaEntity
import app.liora.core.database.model.SyncMetadata
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** Stamps every local write so it can later sync: HLC for ordering, dirty flag for "needs push". */
class SyncStamper(
    private val hlc: HybridLogicalClock,
    private val clock: Clock,
) {
    fun nowMillis(): Long = clock.now().toEpochMilliseconds()

    suspend fun newRow(): SyncMetadata = SyncMetadata(createdAt = nowMillis(), hlc = hlc.now().encoded, dirty = true)

    suspend fun touch(existing: SyncMetadata): SyncMetadata = existing.copy(hlc = hlc.now().encoded, dirty = true)

    suspend fun tombstone(existing: SyncMetadata): SyncMetadata =
        existing.copy(deletedAt = nowMillis(), hlc = hlc.now().encoded, dirty = true)

    /**
     * A row from a backup or another device. It keeps the [hlc] it was written with, so it merges like any
     * other version of the row, and the clock moves past it, so edits made here afterwards still win. It's
     * [dirty] when the sync store may not have it (a restored backup), not when it came from there.
     */
    suspend fun restored(
        createdAt: Long,
        hlc: String,
        deletedAt: Long?,
        dirty: Boolean,
    ): SyncMetadata {
        this.hlc.receive(Hlc.parse(hlc))
        return SyncMetadata(createdAt = createdAt, hlc = hlc, deletedAt = deletedAt, dirty = dirty)
    }
}

/**
 * The `local_meta` key of the newest HLC merged from a backup or another device. The clock starts past
 * it after a restart (see [HybridLogicalClock]).
 */
internal const val NEWEST_MERGED_HLC = "newest_merged_hlc"

/** This installation's stable id, used as the HLC node so concurrent edits from two devices never tie. */
class DeviceIdentity(
    private val meta: LocalMetaDao,
    private val ids: IdGenerator,
) {
    private val mutex = Mutex()
    private var cached: String? = null

    suspend fun deviceId(): String =
        cached ?: mutex.withLock {
            cached ?: (meta.get(KEY) ?: ids.newId().also { meta.put(LocalMetaEntity(KEY, it)) })
                .also { cached = it }
        }

    private companion object {
        const val KEY = "device_id"
    }
}
