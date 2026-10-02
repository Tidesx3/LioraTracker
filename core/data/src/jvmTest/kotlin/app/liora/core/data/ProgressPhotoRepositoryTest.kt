package app.liora.core.data

import androidx.room.useReaderConnection
import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.body.ImportedPhoto
import app.liora.core.data.body.OfflineProgressPhotoRepository
import app.liora.core.data.body.PhotoStorage
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.model.PhotoPose
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Progress photos: added with the camera's date, tagged, moved, and deleted with their file. */
class ProgressPhotoRepositoryTest {
    private val zone = TimeZone.of("Europe/Vienna")
    private val clock = TestClock(at(2026, 10, 2, 7, 30))
    private val database = inMemoryLioraDatabase()
    private val storage = FakeStorage()
    private val stamper = SyncStamper(HybridLogicalClock(clock) { "test-device" }, clock)
    private val photos =
        OfflineProgressPhotoRepository(database.progressPhotoDao(), storage, IdGenerator(clock), stamper, clock)

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun aPhotoKeepsTheDateTheCameraGaveIt() =
        runTest {
            val august = at(2026, 8, 1, 8, 0)
            storage.dates["content://old"] = august

            val old = photos.add("content://old", PhotoPose.Front)!!
            val new = photos.add("content://new", pose = null)!!

            assertEquals(august, old.takenAt)
            // No date recorded: now.
            assertEquals(clock.now(), new.takenAt)
            // Oldest first, each with its own file.
            assertEquals(listOf(old, new), photos.photos.first())
            assertEquals(setOf(old.path, new.path), storage.files)
        }

    @Test
    fun aDateInTheFutureIsNow() =
        runTest {
            storage.dates["content://wrong-clock"] = clock.now() + 400.days
            assertEquals(clock.now(), photos.add("content://wrong-clock", PhotoPose.Back)!!.takenAt)
        }

    @Test
    fun anUnreadableImageAddsNothing() =
        runTest {
            assertNull(photos.add(FakeStorage.BROKEN, PhotoPose.Front))
            assertTrue(photos.photos.first().isEmpty())
        }

    @Test
    fun poseAndDayCanBeCorrected() =
        runTest {
            val photo = photos.add("content://photo", pose = null)!!

            photos.setPose(photo.id, PhotoPose.Side)
            photos.moveTo(photo.id, LocalDate(2026, 9, 28), zone)

            val moved = photos.photos.first().single()
            assertEquals(PhotoPose.Side, moved.pose)
            // Same time of day, on the day it was really taken.
            assertEquals(at(2026, 9, 28, 7, 30), moved.takenAt)
        }

    @Test
    fun deletingTombstonesTheRowAndRemovesTheFile() =
        runTest {
            val photo = photos.add("content://photo", PhotoPose.Front)!!

            photos.delete(photo.id)

            assertTrue(photos.photos.first().isEmpty())
            assertTrue(storage.files.isEmpty())
            assertNotNull(deletedAt(photo.id))
        }

    /** A row's tombstone time; fails if the row was hard-deleted. */
    private suspend fun deletedAt(id: String): Long? =
        database.useReaderConnection { connection ->
            connection.usePrepared("SELECT deleted_at FROM progress_photo WHERE id = ?") { row ->
                row.bindText(1, id)
                check(row.step()) { "$id was hard-deleted" }
                if (row.isNull(0)) null else row.getLong(0)
            }
        }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ) = LocalDateTime(year, month, day, hour, minute).toInstant(zone)

    /** Keeps "files" as paths in memory; [dates] are what the camera recorded per source. */
    private class FakeStorage : PhotoStorage {
        val files = mutableSetOf<String>()
        val dates = mutableMapOf<String, Instant>()

        override suspend fun import(
            source: String,
            id: String,
        ): ImportedPhoto? {
            if (source == BROKEN) return null
            val path = "/photos/$id.jpg"
            files += path
            return ImportedPhoto(path, dates[source])
        }

        override suspend fun delete(path: String) {
            files -= path
        }

        companion object {
            const val BROKEN = "content://not-an-image"
        }
    }

    private class TestClock(
        var now: Instant,
    ) : Clock {
        override fun now() = now
    }
}
