package app.liora.core.data

import app.liora.core.data.backup.NewerBackupException
import app.liora.core.data.sync.PulledBatches
import app.liora.core.data.sync.SyncBackend
import app.liora.core.model.MeasurementType
import app.liora.core.model.PhotoPose
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Sync: what one phone changes reaches the other through the store, row by row, the newer version of a
 * row winning on both, deletions included.
 */
class SyncEngineTest {
    private val store = InMemoryStore()
    private val phone = TestPhone("phone")
    private val tablet = TestPhone("tablet")

    @AfterTest
    fun tearDown() {
        phone.database.close()
        tablet.database.close()
    }

    @Test
    fun whatOnePhoneLoggedReachesTheOther() =
        runTest {
            phone.trainForAWhile()

            phone.sync.sync(store)
            assertTrue(tablet.sync.sync(store).pulled > 0)

            phone.assertSameAs(tablet)
            val photo =
                tablet.photos.photos
                    .first()
                    .single()
            assertContentEquals("front.jpg".encodeToByteArray(), tablet.storage.files[photo.path])
            // The workout still going on the phone isn't history yet.
            assertNull(tablet.workouts.activeWorkout.first())
            assertNotNull(tablet.sync.lastSynced.first())
        }

    @Test
    fun nothingChangedMeansNothingPushed() =
        runTest {
            phone.trainForAWhile()
            phone.sync.sync(store)
            assertEquals(1, store.batches.size)

            assertEquals(0, phone.sync.sync(store).pushed)
            // What the tablet pulled is in the store already, so it doesn't push it back.
            assertEquals(0, tablet.sync.sync(store).pushed)
            assertEquals(1, store.batches.size)
        }

    @Test
    fun editsAndDeletionsTravelBothWays() =
        runTest {
            phone.trainForAWhile()
            phone.sync.sync(store)
            tablet.sync.sync(store)

            tablet.clock.advance(1.hours)
            val legs =
                tablet.history.workouts
                    .first()
                    .single { it.name == "Legs" }
            tablet.history.delete(legs.id)
            tablet.body.saveDay(LocalDate(2026, 9, 29), mapOf(MeasurementType.Bodyweight to 81.0), TestPhone.ZONE)
            phone.clock.advance(1.hours)
            phone.renamePush("Push A")

            tablet.sync.sync(store)
            phone.sync.sync(store)
            tablet.sync.sync(store)

            phone.assertSameAs(tablet)
            assertTrue(
                phone.history.workouts
                    .first()
                    .none { it.name == "Legs" },
            )
            assertEquals(
                81.0,
                phone.body.measurements
                    .first()
                    .single { it.type == MeasurementType.Bodyweight }
                    .value,
            )
            assertEquals("Push A", tablet.routines.get("push")?.name)
        }

    @Test
    fun theNewerEditWinsOnBoth() =
        runTest {
            phone.trainForAWhile()
            phone.sync.sync(store)
            tablet.sync.sync(store)

            // Both rename the routine before either syncs; the tablet does it later.
            tablet.clock.now = phone.clock.now
            phone.clock.advance(1.hours)
            phone.renamePush("Push (phone)")
            tablet.clock.advance(2.hours)
            tablet.renamePush("Push (tablet)")

            tablet.sync.sync(store)
            phone.sync.sync(store)
            tablet.sync.sync(store)

            assertEquals("Push (tablet)", phone.routines.get("push")?.name)
            assertEquals("Push (tablet)", tablet.routines.get("push")?.name)
        }

    @Test
    fun theWorkoutInProgressSyncsOnceItsFinished() =
        runTest {
            phone.workouts.startEmptyWorkout()
            phone.workouts.rename("Arms")
            phone.sync.sync(store)
            tablet.sync.sync(store)
            assertNull(tablet.workouts.activeWorkout.first())
            assertTrue(
                tablet.history.workouts
                    .first()
                    .isEmpty(),
            )

            phone.workouts.finish()
            phone.sync.sync(store)
            tablet.sync.sync(store)
            assertEquals(
                listOf("Arms"),
                tablet.history.workouts
                    .first()
                    .map { it.name },
            )
        }

    @Test
    fun aRowChangedWhileItsPushedGoesWithTheNextSync() =
        runTest {
            phone.body.saveDay(LocalDate(2026, 9, 29), mapOf(MeasurementType.Bodyweight to 82.0), TestPhone.ZONE)
            store.beforePush = {
                phone.clock.advance(1.minutes)
                phone.body.saveDay(LocalDate(2026, 9, 29), mapOf(MeasurementType.Bodyweight to 82.4), TestPhone.ZONE)
            }
            phone.sync.sync(store)
            store.beforePush = {}

            assertTrue(phone.sync.sync(store).pushed > 0)
            tablet.sync.sync(store)
            assertEquals(
                82.4,
                tablet.body.measurements
                    .first()
                    .single()
                    .value,
            )
        }

    @Test
    fun editsAfterARestartBeatAClockThatRanAhead() =
        runTest {
            phone.trainForAWhile()
            phone.sync.sync(store)
            // The tablet's clock runs a day ahead.
            tablet.clock.now = phone.clock.now + 24.hours
            tablet.sync.sync(store)
            tablet.renamePush("Push (tablet)")
            tablet.sync.sync(store)
            phone.sync.sync(store)

            // The phone restarts, then renames the routine again: its edit is the newer one.
            val restarted = phone.restarted()
            restarted.clock.advance(1.minutes)
            restarted.renamePush("Push (phone)")
            restarted.sync.sync(store)
            tablet.sync.sync(store)

            assertEquals("Push (phone)", tablet.routines.get("push")?.name)
        }

    @Test
    fun aDeletedPhotoLosesItsImageEverywhere() =
        runTest {
            phone.photos.add("front.jpg", PhotoPose.Front)
            phone.sync.sync(store)
            tablet.sync.sync(store)
            val photo =
                tablet.photos.photos
                    .first()
                    .single()
            assertNotNull(tablet.storage.files[photo.path])

            phone.clock.advance(1.minutes)
            phone.photos.delete(photo.id)
            phone.sync.sync(store)
            tablet.sync.sync(store)

            assertTrue(
                tablet.photos.photos
                    .first()
                    .isEmpty(),
            )
            assertNull(tablet.storage.files[photo.path])
        }

    @Test
    fun startingOverPutsEverythingInANewStore() =
        runTest {
            phone.trainForAWhile()
            phone.sync.sync(store)

            val newStore = InMemoryStore()
            phone.sync.startOver()
            phone.sync.sync(newStore)
            tablet.sync.sync(newStore)

            phone.assertSameAs(tablet)
            assertEquals(
                1,
                tablet.photos.photos
                    .first()
                    .size,
            )
        }

    @Test
    fun aBatchFromANewerAppStopsTheSyncUntilThisOneIsUpdated() =
        runTest {
            store.batches += "newer-phone" to """{"format": "liora-backup", "version": 99}""".encodeToByteArray()
            phone.body.saveDay(LocalDate(2026, 9, 29), mapOf(MeasurementType.Bodyweight to 82.0), TestPhone.ZONE)

            assertFailsWith<NewerBackupException> { phone.sync.sync(store) }
            // Nothing pushed, so nothing is lost: it all goes once this phone can read the store.
            assertEquals(1, store.batches.size)
        }

    private suspend fun TestPhone.renamePush(name: String) =
        routines.save(checkNotNull(routines.get("push")).copy(name = name))

    /** What both phones show is the same. */
    private suspend fun TestPhone.assertSameAs(other: TestPhone) {
        assertEquals(history.workouts.first(), other.history.workouts.first())
        assertEquals(routines.folders.first(), other.routines.folders.first())
        assertEquals(routines.routines.first(), other.routines.routines.first())
        assertEquals(exercises.observeExercises("de").first(), other.exercises.observeExercises("de").first())
        assertEquals(body.measurements.first(), other.body.measurements.first())
        assertEquals(gyms.profiles.first(), other.gyms.profiles.first())
        assertEquals(settings.current(), other.settings.current())
        assertEquals(
            photos.photos.first().map { it.copy(path = "") },
            other.photos.photos
                .first()
                .map { it.copy(path = "") },
        )
    }

    /** A store in memory: batches in the order they came, the cursor counting them. */
    private class InMemoryStore : SyncBackend {
        val batches = mutableListOf<Pair<String, ByteArray>>()
        val blobs = mutableMapOf<String, ByteArray>()
        var beforePush: suspend () -> Unit = {}

        override suspend fun push(
            deviceId: String,
            batch: ByteArray,
        ) {
            beforePush()
            batches += deviceId to batch
        }

        override suspend fun pull(
            deviceId: String,
            cursor: String?,
        ): PulledBatches {
            val from = cursor?.toInt() ?: 0
            val theirs = batches.drop(from).filter { it.first != deviceId }.map { it.second }
            return PulledBatches(theirs, batches.size.toString())
        }

        override suspend fun putBlob(
            name: String,
            bytes: ByteArray,
        ) {
            blobs[name] = bytes
        }

        override suspend fun getBlob(name: String): ByteArray? = blobs[name]
    }
}
