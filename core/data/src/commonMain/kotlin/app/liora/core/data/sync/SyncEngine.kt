package app.liora.core.data.sync

import app.liora.core.data.backup.Backup
import app.liora.core.data.backup.BackupJson
import app.liora.core.data.backup.NewerBackupException
import app.liora.core.data.backup.RowSelection
import app.liora.core.data.backup.SyncedTables
import app.liora.core.data.backup.hlcs
import app.liora.core.data.body.PhotoStorage
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.LocalMetaDao
import app.liora.core.database.dao.SyncDao
import app.liora.core.database.model.LocalMetaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/** What a sync did: rows merged from other devices, and rows pushed from this one. */
data class SyncResult(
    val pulled: Int,
    val pushed: Int,
)

/**
 * Keeps this device and a [SyncBackend] in step. A sync pulls what other devices changed and merges it
 * row by row, the newer version of each row winning by HLC, then pushes what changed here as one batch.
 * Batches are in the backup format ([BackupJson]): the rows changed since the last push, each nested under
 * its parents, deletions included. The workout in progress stays here until it's finished.
 */
class SyncEngine internal constructor(
    private val tables: SyncedTables,
    private val syncDao: SyncDao,
    private val meta: LocalMetaDao,
    private val device: DeviceIdentity,
    private val photoStorage: PhotoStorage,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** When the last sync finished, if one has. */
    val lastSynced: Flow<Instant?> =
        meta.observe(LAST_SYNC).map { millis -> millis?.toLongOrNull()?.let(Instant::fromEpochMilliseconds) }

    /**
     * Pulls, then pushes, one sync at a time. Throws [NewerBackupException] when another device runs a newer
     * Liora whose batches this one can't read, and whatever [backend] throws when the store can't be reached.
     * Nothing is lost either way: the next sync picks up where this one stopped.
     */
    suspend fun sync(backend: SyncBackend): SyncResult =
        mutex.withLock {
            val deviceId = device.deviceId()
            val pulled = pull(backend, deviceId)
            val pushed = push(backend, deviceId)
            meta.put(LocalMetaEntity(LAST_SYNC, clock.now().toEpochMilliseconds().toString()))
            SyncResult(pulled, pushed)
        }

    /**
     * Makes the next sync start over, for a store that has nothing from this device yet: it pulls all
     * there is and pushes everything here, deletions too.
     */
    suspend fun startOver() =
        mutex.withLock {
            meta.delete(CURSOR)
            meta.put(LocalMetaEntity(PUSH_EVERYTHING, true.toString()))
        }

    private suspend fun pull(
        backend: SyncBackend,
        deviceId: String,
    ): Int {
        val pulled = backend.pull(deviceId, meta.get(CURSOR))
        val batches =
            withContext(Dispatchers.Default) { pulled.batches.map { BackupJson.decode(it.decodeToString()) } }
        // Images first, so a photo never points at a file that isn't there yet.
        val wanted = buildMap { batches.forEach { putAll(tables.imagesWanted(it)) } }
        val images = mutableMapOf<String, String>()
        for ((name, id) in wanted) {
            backend.getBlob(name)?.let { bytes -> photoStorage.restore(id, bytes)?.let { images[id] = it } }
        }
        val merged =
            transactions.inTransaction {
                // Rows from the store aren't dirty: the store has them already.
                batches.map { tables.merge(it, images, dirty = false) }.also {
                    pulled.cursor?.let { cursor -> meta.put(LocalMetaEntity(CURSOR, cursor)) }
                }
            }
        merged.flatMap { it.deletedImages }.forEach { photoStorage.delete(it) }
        return merged.sumOf { it.written }
    }

    private suspend fun push(
        backend: SyncBackend,
        deviceId: String,
    ): Int {
        val selection = if (meta.get(PUSH_EVERYTHING) != null) RowSelection.All else RowSelection.Changed
        val snapshot =
            transactions.inTransaction {
                tables.snapshot(selection, clock.now().toEpochMilliseconds(), imageName = { photoId -> photoId })
            }
        val batch = snapshot.backup
        val rows = batch.hlcs().count()
        if (rows > 0) {
            // Images first, so whoever pulls a photo finds its image.
            for ((name, path) in snapshot.images) photoStorage.read(path)?.let { backend.putBlob(name, it) }
            backend.push(deviceId, encode(batch))
        }
        transactions.inTransaction {
            markPushed(batch)
            meta.delete(PUSH_EVERYTHING)
        }
        if (backend.wantsSnapshot(deviceId)) {
            // Every image is in the store already: each went with the batch that first held its photo.
            val everything =
                transactions.inTransaction {
                    tables.snapshot(RowSelection.All, clock.now().toEpochMilliseconds(), imageName = { it })
                }
            backend.putSnapshot(deviceId, encode(everything.backup))
        }
        return rows
    }

    private suspend fun encode(batch: Backup): ByteArray =
        withContext(Dispatchers.Default) { BackupJson.encodeCompact(batch).encodeToByteArray() }

    /** Clears `dirty` on the rows in [batch], unless they changed while it was pushed. */
    private suspend fun markPushed(batch: Backup) {
        batch.exercises.forEach { syncDao.pushedExercise(it.id, it.hlc) }
        batch.exerciseSettings.forEach { syncDao.pushedExerciseSettings(it.exerciseId, it.hlc) }
        batch.routineFolders.forEach { syncDao.pushedRoutineFolder(it.id, it.hlc) }
        batch.routines.forEach { routine ->
            syncDao.pushedRoutine(routine.id, routine.hlc)
            routine.exercises.forEach { exercise ->
                syncDao.pushedRoutineExercise(exercise.id, exercise.hlc)
                exercise.sets.forEach { syncDao.pushedRoutineSet(it.id, it.hlc) }
            }
        }
        batch.workouts.forEach { workout ->
            syncDao.pushedWorkout(workout.id, workout.hlc)
            workout.exercises.forEach { exercise ->
                syncDao.pushedWorkoutExercise(exercise.id, exercise.hlc)
                exercise.sets.forEach { syncDao.pushedWorkoutSet(it.id, it.hlc) }
            }
        }
        batch.measurements.forEach { syncDao.pushedMeasurement(it.id, it.hlc) }
        batch.photos.forEach { syncDao.pushedProgressPhoto(it.id, it.hlc) }
        batch.gyms.forEach { syncDao.pushedGymProfile(it.id, it.hlc) }
        batch.preferences.forEach { syncDao.pushedPreference(it.key, it.hlc) }
    }

    private companion object {
        // Kept in local_meta: where to pull from next, a full push asked for, and when the last sync ended.
        const val CURSOR = "sync_cursor"
        const val PUSH_EVERYTHING = "sync_push_everything"
        const val LAST_SYNC = "sync_last"
    }
}
