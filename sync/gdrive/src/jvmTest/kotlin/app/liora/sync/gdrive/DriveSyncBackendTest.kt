package app.liora.sync.gdrive

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The app data folder's layout: each device's batches in order, snapshots in their place, and blobs. */
class DriveSyncBackendTest {
    private val folder = InMemoryFolder()
    private val drive = DriveSyncBackend(folder, snapshotAfter = 3)

    @Test
    fun aDeviceReadsTheOthersBatchesInOrderAndOnlyOnce() =
        runTest {
            drive.push(PHONE, "1".encodeToByteArray())
            drive.push(PHONE, "2".encodeToByteArray())
            drive.push(TABLET, "mine".encodeToByteArray())

            val first = drive.pull(TABLET, cursor = null)
            assertEquals(listOf("1", "2"), first.texts())

            drive.push(PHONE, "3".encodeToByteArray())
            val second = drive.pull(TABLET, first.cursor)
            assertEquals(listOf("3"), second.texts())
            assertEquals(emptyList(), drive.pull(TABLET, second.cursor).texts())
        }

    @Test
    fun batchesAreGzippedAndNamedByDeviceAndNumber() =
        runTest {
            drive.push(PHONE, "{}".encodeToByteArray())
            val file = folder.files.values.single()
            assertEquals("changes-$PHONE-00000001.json.gz", file.name)
            assertEquals(mapOf("kind" to "changes", "device" to PHONE, "seq" to "1"), file.properties)
            assertContentEquals("{}".encodeToByteArray(), gunzip(folder.contents.getValue(file.id)))
        }

    @Test
    fun aSnapshotStandsInForTheBatchesItReplaced() =
        runTest {
            repeat(3) { drive.push(PHONE, "batch ${it + 1}".encodeToByteArray()) }
            val readOne = drive.pull(TABLET, null).cursor.let { cursor -> cursor }
            assertFalse(drive.wantsSnapshot(PHONE))
            drive.push(PHONE, "batch 4".encodeToByteArray())
            assertTrue(drive.wantsSnapshot(PHONE))

            drive.putSnapshot(PHONE, "everything".encodeToByteArray())
            assertEquals(listOf("snapshot-$PHONE-00000004.json.gz"), folder.files.values.map { it.name })
            assertFalse(drive.wantsSnapshot(PHONE))

            // A new device reads the snapshot; one that read up to batch 3 reads it too, for batch 4.
            assertEquals(listOf("everything"), drive.pull(LAPTOP, null).texts())
            assertEquals(listOf("everything"), drive.pull(TABLET, readOne).texts())

            // Batches after the snapshot follow it, numbered on.
            drive.push(PHONE, "batch 5".encodeToByteArray())
            val fresh = drive.pull(LAPTOP, null)
            assertEquals(listOf("everything", "batch 5"), fresh.texts())
            assertEquals(emptyList(), drive.pull(LAPTOP, fresh.cursor).texts())
        }

    @Test
    fun aMissingBatchHoldsBackTheOnesAfterIt() =
        runTest {
            repeat(3) { drive.push(PHONE, "batch ${it + 1}".encodeToByteArray()) }
            folder.files.values
                .single { it.properties["seq"] == "2" }
                .let { folder.delete(it.id) }

            val pulled = drive.pull(TABLET, null)
            assertEquals(listOf("batch 1"), pulled.texts())
            assertEquals("""{"$PHONE":1}""", pulled.cursor)
        }

    @Test
    fun blobsAreStoredByNameAndReplaced() =
        runTest {
            assertNull(drive.getBlob("photo-1"))
            drive.putBlob("photo-1", byteArrayOf(1, 2))
            drive.putBlob("photo-1", byteArrayOf(3))
            drive.putBlob("photo-2", byteArrayOf(4))

            assertContentEquals(byteArrayOf(3), drive.getBlob("photo-1"))
            assertContentEquals(byteArrayOf(4), drive.getBlob("photo-2"))
            assertEquals(2, folder.files.size)
            // Blobs aren't batches.
            assertEquals(emptyList(), drive.pull(TABLET, null).texts())
        }

    private fun app.liora.core.data.sync.PulledBatches.texts() = batches.map { gunzipped(it) }

    /** Pulled batches are unzipped already. */
    private fun gunzipped(bytes: ByteArray) = bytes.decodeToString()

    /** Files in memory, in the order they were made; it doesn't search, so the backend's own checks are tested. */
    private class InMemoryFolder : AppDataFolder {
        val files = linkedMapOf<String, DriveFile>()
        val contents = mutableMapOf<String, ByteArray>()
        private var next = 0

        override suspend fun list(query: String): List<DriveFile> = files.values.toList()

        override suspend fun download(id: String): ByteArray = contents.getValue(id)

        override suspend fun create(
            name: String,
            properties: Map<String, String>,
            content: ByteArray,
        ): DriveFile {
            val file = DriveFile("file-${next++}", name, properties)
            files[file.id] = file
            contents[file.id] = content
            return file
        }

        override suspend fun update(
            id: String,
            content: ByteArray,
        ) {
            contents[id] = content
        }

        override suspend fun delete(id: String) {
            files -= id
            contents -= id
        }
    }

    private companion object {
        const val PHONE = "phone"
        const val TABLET = "tablet"
        const val LAPTOP = "laptop"
    }
}
