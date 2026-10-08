package app.liora.sync.gdrive

import app.liora.core.data.sync.PulledBatches
import app.liora.core.data.sync.SyncBackend
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The sync store in the user's Drive app data folder. Each device only adds and deletes its own files,
 * so devices never write over each other:
 * - `changes-<device>-<seq>.json.gz`: its batches, numbered from 1.
 * - `snapshot-<device>-<seq>.json.gz`: everything it had once its batches piled up, in place of its files
 *   up to `seq`, which it then deletes.
 * - `blob-<name>`: photos' images.
 *
 * The cursor is how far this device has read each other device's files.
 */
internal class DriveSyncBackend(
    private val folder: AppDataFolder,
    private val snapshotAfter: Int = SNAPSHOT_AFTER,
) : SyncBackend {
    override suspend fun push(
        deviceId: String,
        batch: ByteArray,
    ) {
        val seq = (ownFiles(deviceId).maxOfOrNull { it.seq } ?: 0) + 1
        folder.create(fileName(CHANGES, deviceId, seq), properties(CHANGES, deviceId, seq), gzip(batch))
    }

    override suspend fun pull(
        deviceId: String,
        cursor: String?,
    ): PulledBatches {
        val read = decodeCursor(cursor)
        val next = read.toMutableMap()
        val batches = mutableListOf<ByteArray>()
        val theirs = folder.list(SYNC_FILES).mapNotNull { it.syncFile() }.filter { it.device != deviceId }
        for ((device, files) in theirs.groupBy { it.device }) {
            var at = read[device] ?: 0
            val changes = files.filter { it.kind == CHANGES && it.seq > at }.sortedBy { it.seq }
            val snapshot = files.filter { it.kind == SNAPSHOT }.maxByOrNull { it.seq }
            // Batches folded into a snapshot are gone, and the snapshot stands in for them.
            if (snapshot != null && snapshot.seq > changes.unbrokenFrom(at)) {
                batches += gunzip(folder.download(snapshot.file.id))
                at = snapshot.seq
            }
            for (change in changes.filter { it.seq > at }) {
                // A gap would skip changes: wait for them.
                if (change.seq != at + 1) break
                batches += gunzip(folder.download(change.file.id))
                at = change.seq
            }
            next[device] = at
        }
        return PulledBatches(batches, encodeCursor(next))
    }

    override suspend fun putBlob(
        name: String,
        bytes: ByteArray,
    ) {
        val existing = blob(name)
        if (existing == null) {
            folder.create("$BLOB-$name", mapOf(KIND to BLOB, BLOB to name), bytes)
        } else {
            folder.update(existing.id, bytes)
        }
    }

    override suspend fun getBlob(name: String): ByteArray? = blob(name)?.let { folder.download(it.id) }

    override suspend fun usedBytes(): Long = folder.list("trashed = false").sumOf { it.size?.toLongOrNull() ?: 0L }

    override suspend fun wantsSnapshot(deviceId: String): Boolean =
        ownFiles(deviceId).count { it.kind == CHANGES } > snapshotAfter

    override suspend fun putSnapshot(
        deviceId: String,
        snapshot: ByteArray,
    ) {
        val own = ownFiles(deviceId)
        val seq = own.maxOfOrNull { it.seq } ?: return
        folder.create(fileName(SNAPSHOT, deviceId, seq), properties(SNAPSHOT, deviceId, seq), gzip(snapshot))
        // Only once the snapshot is there, so a device reading meanwhile finds one or the other.
        own.forEach { folder.delete(it.file.id) }
    }

    private suspend fun ownFiles(deviceId: String): List<SyncFile> =
        folder
            .list("$SYNC_FILES and ${has(DEVICE, deviceId)}")
            .mapNotNull { it.syncFile() }
            .filter { it.device == deviceId }

    private suspend fun blob(name: String): DriveFile? =
        folder.list("${has(BLOB, name)} and trashed = false").firstOrNull { it.properties[BLOB] == name }
}

/** A batch or snapshot, read from its file's properties. */
private class SyncFile(
    val file: DriveFile,
    val kind: String,
    val device: String,
    val seq: Int,
)

private fun DriveFile.syncFile(): SyncFile? {
    val kind = properties[KIND]?.takeIf { it == CHANGES || it == SNAPSHOT }
    val device = properties[DEVICE]
    val seq = properties[SEQ]?.toIntOrNull()
    return if (kind != null && device != null && seq != null) SyncFile(this, kind, device, seq) else null
}

/** The last of the batches numbered on without a gap from [after]; [after] itself when there are none. */
private fun List<SyncFile>.unbrokenFrom(after: Int): Int {
    var at = after
    for (file in this) {
        if (file.seq == at + 1) {
            at = file.seq
        } else if (file.seq > at + 1) {
            break
        }
    }
    return at
}

private fun decodeCursor(cursor: String?): Map<String, Int> =
    cursor?.let { runCatching { Json.decodeFromString(cursorSerializer, it) }.getOrNull() }.orEmpty()

private fun encodeCursor(cursor: Map<String, Int>): String = Json.encodeToString(cursorSerializer, cursor)

private val cursorSerializer = MapSerializer(String.serializer(), Int.serializer())

/** Some weeks of training; a device new to the store then reads one file instead of a hundred. */
private const val SNAPSHOT_AFTER = 100

private const val KIND = "kind"
private const val DEVICE = "device"
private const val SEQ = "seq"
private const val CHANGES = "changes"
private const val SNAPSHOT = "snapshot"
private const val BLOB = "blob"
private const val SEQ_DIGITS = 8

private val SYNC_FILES = "(${has(KIND, CHANGES)} or ${has(KIND, SNAPSHOT)}) and trashed = false"

private fun fileName(
    kind: String,
    deviceId: String,
    seq: Int,
) = "$kind-$deviceId-${seq.toString().padStart(SEQ_DIGITS, '0')}.json.gz"

private fun properties(
    kind: String,
    deviceId: String,
    seq: Int,
) = mapOf(KIND to kind, DEVICE to deviceId, SEQ to seq.toString())

/** A Drive search for files whose property [key] is [value]. */
private fun has(
    key: String,
    value: String,
) = "appProperties has { key='$key' and value='${value.replace("\\", "\\\\").replace("'", "\\'")}' }"
