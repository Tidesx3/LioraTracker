package app.liora.core.data

import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.backup.ArchiveWriter
import app.liora.core.data.backup.BackupContents
import app.liora.core.data.backup.ExportFileException
import app.liora.core.data.backup.ExportFiles
import app.liora.core.data.backup.InvalidBackupException
import app.liora.core.data.backup.NewerBackupException
import app.liora.core.data.backup.OfflineBackupRepository
import app.liora.core.data.body.ImportedPhoto
import app.liora.core.data.body.OfflineBodyRepository
import app.liora.core.data.body.OfflineProgressPhotoRepository
import app.liora.core.data.body.PhotoStorage
import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.gym.OfflineGymProfileRepository
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.settings.OfflineSettingsRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.workout.LocalRestTimerRepository
import app.liora.core.data.workout.OfflineActiveWorkoutRepository
import app.liora.core.data.workout.OfflineWorkoutHistoryRepository
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.database.model.ExerciseEntity
import app.liora.core.database.model.SyncMetadata
import app.liora.core.domain.GymProfiles
import app.liora.core.model.DistanceUnit
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseCategory
import app.liora.core.model.ExerciseDraft
import app.liora.core.model.Mass
import app.liora.core.model.MeasurementType
import app.liora.core.model.Muscle
import app.liora.core.model.PhotoPose
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Backups: everything made on one phone comes back on another, and restoring merges row by row, so
 * nothing changed since the backup is lost.
 */
class BackupRepositoryTest {
    private val zone = TimeZone.of("Europe/Berlin")

    /** Where backups are saved: shared by the phones, like a folder in the cloud. */
    private val files = FakeFiles()
    private val phone = Phone("phone", TestClock())
    private val newPhone = Phone("new-phone", TestClock())

    @AfterTest
    fun tearDown() {
        phone.database.close()
        newPhone.database.close()
    }

    @Test
    fun everythingComesBackOnANewPhone() =
        runTest {
            phone.trainForAWhile()

            phone.backups.backUp(BACKUP)
            val contents = newPhone.backups.read(BACKUP)
            assertEquals(2, contents.workouts)
            assertEquals(1, contents.routines)
            assertEquals(1, contents.customExercises)
            assertEquals(2, contents.measurements)
            assertEquals(1, contents.photos)
            assertTrue(newPhone.backups.restore(contents) > 0)

            assertEquals(phone.history.workouts.first(), newPhone.history.workouts.first())
            assertEquals(phone.routines.folders.first(), newPhone.routines.folders.first())
            assertEquals(phone.routines.routines.first(), newPhone.routines.routines.first())
            assertEquals(
                phone.exercises.observeExercises("de").first(),
                newPhone.exercises.observeExercises("de").first(),
            )
            assertEquals(phone.body.measurements.first(), newPhone.body.measurements.first())
            assertEquals(phone.gyms.profiles.first(), newPhone.gyms.profiles.first())
            assertEquals(phone.settings.current(), newPhone.settings.current())
            val photo =
                newPhone.photos.photos
                    .first()
                    .single()
            assertEquals(phone.photos.photos.first(), listOf(photo))
            assertContentEquals("front.jpg".encodeToByteArray(), newPhone.storage.files[photo.path])
            // The workout still going on the old phone wasn't history yet.
            assertNull(newPhone.workouts.activeWorkout.first())

            // Nothing lost or changed on the way: the new phone's backup is the old one's.
            newPhone.backups.backUp(SECOND_BACKUP)
            assertEquals(json(BACKUP).withoutExportTime(), json(SECOND_BACKUP).withoutExportTime())
        }

    @Test
    fun theFileIsReadableJson() =
        runTest {
            phone.trainForAWhile()
            phone.backups.backUp(BACKUP)

            val json = json(BACKUP)
            assertContains(json, "\"format\": \"liora-backup\"")
            assertContains(json, "\"setType\": \"warmup\"")
            assertContains(json, "\"weightKg\": 80.0")
            assertContains(json, "\"startedAt\": \"2026-09-28T16:00:00Z\"")
            // The image sits next to the JSON.
            val photo =
                phone.photos.photos
                    .first()
                    .single()
            assertEquals(
                listOf("liora.json", "photos/${photo.id}.jpg"),
                files.archives.getValue(BACKUP).map { it.first },
            )
        }

    @Test
    fun restoringTwiceChangesNothing() =
        runTest {
            phone.trainForAWhile()
            phone.backups.backUp(BACKUP)
            val contents = newPhone.backups.read(BACKUP)
            newPhone.backups.restore(contents)

            assertEquals(0, newPhone.backups.restore(contents))
            assertEquals(
                2,
                newPhone.history.workouts
                    .first()
                    .size,
            )
        }

    @Test
    fun changesMadeSinceTheBackupStay() =
        runTest {
            phone.trainForAWhile()
            phone.backups.backUp(BACKUP)
            val (legs, push) =
                phone.history.workouts
                    .first()
                    .map { it.id }
            phone.clock.advance(1.minutes)
            phone.history.edit(push).rename("Push heavy")
            phone.history.delete(legs)
            phone.photos.delete(
                phone.photos.photos
                    .first()
                    .single()
                    .id,
            )

            // Restoring an older backup over them: the rename and the deletions are newer.
            assertEquals(0, phone.backups.restore(phone.backups.read(BACKUP)))
            assertEquals(
                listOf("Push heavy"),
                phone.history.workouts
                    .first()
                    .map { it.name },
            )
            assertTrue(
                phone.photos.photos
                    .first()
                    .isEmpty(),
            )
            // The deleted photo's image wasn't brought back either.
            assertTrue(phone.storage.restored.isEmpty())
        }

    @Test
    fun aNewerBackupUpdatesWhatAnOlderOneRestored() =
        runTest {
            phone.trainForAWhile()
            phone.backups.backUp(BACKUP)
            val push =
                phone.history.workouts
                    .first()
                    .last()
                    .id
            phone.clock.advance(1.minutes)
            phone.history.edit(push).rename("Push heavy")
            phone.backups.backUp(SECOND_BACKUP)

            newPhone.backups.restore(newPhone.backups.read(BACKUP))
            assertEquals(1, newPhone.backups.restore(newPhone.backups.read(SECOND_BACKUP)))
            // The older backup again changes nothing: its version of the workout is older.
            assertEquals(0, newPhone.backups.restore(newPhone.backups.read(BACKUP)))
            assertEquals(
                "Push heavy",
                newPhone.history.workouts
                    .first()
                    .last()
                    .name,
            )
        }

    @Test
    fun editsAfterARestoreWinOverABackupFromAClockThatRanAhead() =
        runTest {
            phone.clock.advance(3.days)
            phone.trainForAWhile()
            phone.backups.backUp(BACKUP)
            newPhone.backups.restore(newPhone.backups.read(BACKUP))

            // The new phone's clock is days behind the HLCs it restored, yet its edit is the newer one.
            val push =
                newPhone.history.workouts
                    .first()
                    .last()
                    .id
            newPhone.history.edit(push).rename("Renamed here")
            assertEquals(0, newPhone.backups.restore(newPhone.backups.read(BACKUP)))
            assertEquals(
                "Renamed here",
                newPhone.history.workouts
                    .first()
                    .last()
                    .name,
            )
        }

    @Test
    fun aBackupCantReplaceABuiltInExercise() =
        runTest {
            files.save(
                BACKUP,
                backupJson(
                    """
                    "exercises": [{
                        "id": "$SQUAT", "name": "Not a squat", "trackingType": "duration",
                        "equipment": "machine", "category": "cardio",
                        "createdAt": "2026-09-01T10:00:00Z", "hlc": "9999999999999:0000:other"
                    }]
                    """,
                ),
            )

            assertEquals(0, newPhone.backups.restore(newPhone.backups.read(BACKUP)))
            val squat = newPhone.exercises.observeExercise(SQUAT, "en").first()
            assertEquals("Squat", squat?.name)
            assertEquals(false, squat?.isCustom)
        }

    @Test
    fun filesThatArentBackupsAreTurnedAway() =
        runTest {
            suspend fun read(json: String?): BackupContents {
                files.archives[BACKUP] = listOfNotNull(json?.let { "liora.json" to it.encodeToByteArray() })
                return newPhone.backups.read(BACKUP)
            }

            assertFailsWith<InvalidBackupException> { read(json = null) }
            assertFailsWith<InvalidBackupException> { read("""{"hello": "world"}""") }
            assertFailsWith<InvalidBackupException> { read("not json at all") }
            assertFailsWith<InvalidBackupException> {
                read("""{"format": "something-else", "version": 1, "exportedAt": "2026-10-02T10:00:00Z"}""")
            }
            val damagedHlc = """{"key": "a", "value": "b", "createdAt": "2026-10-02T10:00:00Z", "hlc": "?"}"""
            assertFailsWith<InvalidBackupException> { read(backupJson(""" "preferences": [$damagedHlc] """)) }
            val newer =
                assertFailsWith<NewerBackupException> {
                    read("""{"format": "liora-backup", "version": 2, "exportedAt": "2026-10-02T10:00:00Z"}""")
                }
            assertEquals(2, newer.version)
            assertFailsWith<ExportFileException> { newPhone.backups.read("content://gone") }
        }

    private fun json(file: String) =
        files.archives
            .getValue(file)
            .first { it.first == "liora.json" }
            .second
            .decodeToString()

    private fun String.withoutExportTime() = replace(Regex(""""exportedAt": "[^"]*""""), "")

    private fun backupJson(fields: String) =
        """{"format": "liora-backup", "version": 1, "exportedAt": "2026-10-02T10:00:00Z", $fields}"""

    /** One phone's database and everything that writes to it. */
    private inner class Phone(
        name: String,
        val clock: TestClock,
    ) {
        val database = inMemoryLioraDatabase()
        private val transactions = TransactionRunner(database)
        private val ids = IdGenerator(clock)
        private val stamper = SyncStamper(HybridLogicalClock(clock) { name }, clock)
        val storage = FakePhotos()
        val exercises = OfflineExerciseRepository(database.exerciseDao(), transactions, ids, stamper)
        val routines = OfflineRoutineRepository(database.routineDao(), transactions, ids, stamper)
        private val restTimer = LocalRestTimerRepository(database.localMetaDao(), clock)
        val workouts =
            OfflineActiveWorkoutRepository(
                database.workoutDao(),
                database.exerciseDao(),
                routines,
                restTimer,
                transactions,
                ids,
                stamper,
            )
        val history =
            OfflineWorkoutHistoryRepository(
                database.workoutDao(),
                database.exerciseDao(),
                routines,
                restTimer,
                transactions,
                ids,
                stamper,
            )
        val body = OfflineBodyRepository(database.measurementDao(), transactions, ids, stamper, clock)
        val photos = OfflineProgressPhotoRepository(database.progressPhotoDao(), storage, ids, stamper, clock)
        val settings = OfflineSettingsRepository(database.preferenceDao(), transactions, stamper)
        val gyms =
            OfflineGymProfileRepository(
                database.gymProfileDao(),
                database.localMetaDao(),
                settings,
                transactions,
                ids,
                stamper,
            )
        val backups =
            OfflineBackupRepository(
                database.backupDao(),
                database.exerciseDao(),
                files,
                storage,
                transactions,
                stamper,
                clock,
            )

        init {
            // Every phone has the built-in catalog.
            runBlocking {
                database.exerciseDao().upsertExercises(
                    listOf(
                        ExerciseEntity(
                            id = SQUAT,
                            name = "Squat",
                            trackingType = TrackingType.WeightReps,
                            equipment = Equipment.Barbell,
                            category = ExerciseCategory.Strength,
                            primaryMuscles = setOf(Muscle.Quadriceps),
                            secondaryMuscles = emptySet(),
                            instructions = emptyList(),
                            imagePaths = emptyList(),
                            isCustom = false,
                            variationOf = null,
                            notes = null,
                            rank = 1,
                            sync = SyncMetadata(createdAt = 0, hlc = "0000000000000:0000:seed", dirty = false),
                        ),
                    ),
                )
            }
        }

        /**
         * A routine in a folder, two finished workouts and a deleted one, one in progress, body
         * measurements, a photo, a gym, settings, and a built-in exercise archived.
         */
        suspend fun trainForAWhile() {
            settings.update { it.copy(rpe = true, units = Units(WeightUnit.Pound, DistanceUnit.Mile)) }
            gyms.save(GymProfiles.standard(WeightUnit.Pound, name = "Studio Nord"))
            exercises.setArchived(SQUAT, archived = true)
            pushDay(pushRoutine())
            legDay()

            clock.advance(1.days)
            workouts.startEmptyWorkout()
            workouts.rename("Deleted")
            workouts.finish()
            history.delete(
                history.workouts
                    .first()
                    .single { it.name == "Deleted" }
                    .id,
            )

            body.saveDay(
                LocalDate(2026, 9, 29),
                mapOf(MeasurementType.Bodyweight to 82.5, MeasurementType.Waist to 0.84),
                zone,
            )
            photos.add("front.jpg", PhotoPose.Front)
            clock.advance(1.days)
            // Still going when the backup is made.
            workouts.startEmptyWorkout()
        }

        /** A routine in a folder with a custom exercise; returns its id. */
        private suspend fun pushRoutine(): String {
            val legPress =
                exercises.createCustom(
                    ExerciseDraft(
                        name = "Beinpresse Studio Nord",
                        trackingType = TrackingType.WeightReps,
                        equipment = Equipment.Machine,
                        primaryMuscles = setOf(Muscle.Quadriceps),
                        notes = "Sitz auf 4",
                    ),
                )
            val folder = routines.createFolder("PPL")
            routines.save(
                Routine(
                    id = "push",
                    name = "Push",
                    folderId = folder,
                    notes = "Schwer",
                    exercises =
                        listOf(
                            RoutineExercise(
                                id = "push-press",
                                exerciseId = legPress,
                                restSeconds = 90,
                                notes = "Langsam",
                                sets =
                                    listOf(
                                        RoutineSet("w", SetType.Warmup, Mass(40.0), RepRange(10)),
                                        RoutineSet("s1", weight = Mass(80.0), reps = RepRange(8, 12)),
                                    ),
                            ),
                        ),
                ),
            )
            return "push"
        }

        private suspend fun pushDay(routineId: String) {
            val workout = workouts.startFromRoutine(routineId)
            for (set in workout.exercises.single().sets) {
                clock.advance(5.minutes)
                logger().updateSet(set.id) { copy(reps = reps ?: 9, rpe = 8.5) }
                logger().completeSet(set.id)
            }
            workouts.finish()
        }

        private suspend fun legDay() {
            clock.advance(1.days)
            workouts.startEmptyWorkout()
            workouts.rename("Legs")
            workouts.editor.addExercises(listOf(SQUAT))
            val squatSet =
                workouts.activeWorkout
                    .first()!!
                    .exercises
                    .single()
                    .sets
                    .first()
            logger().updateSet(squatSet.id) { copy(weight = Mass(100.0), reps = 5) }
            logger().completeSet(squatSet.id)
            workouts.finish()
        }

        private fun logger() = workouts.sets
    }

    /** Images as bytes in memory, under the paths the real storage would give them. */
    private class FakePhotos : PhotoStorage {
        val files = mutableMapOf<String, ByteArray>()
        val restored = mutableListOf<String>()

        override suspend fun import(
            source: String,
            id: String,
        ): ImportedPhoto {
            val path = "/photos/$id.jpg"
            files[path] = source.encodeToByteArray()
            return ImportedPhoto(path, takenAt = null)
        }

        override suspend fun delete(path: String) {
            files -= path
        }

        override suspend fun read(path: String): ByteArray? = files[path]

        override suspend fun restore(
            id: String,
            bytes: ByteArray,
        ): String {
            restored += id
            return "/photos/$id.jpg".also { files[it] = bytes }
        }
    }

    /** Archives as lists of entries, in the order they were written. */
    private class FakeFiles : ExportFiles {
        val archives = mutableMapOf<String, List<Pair<String, ByteArray>>>()

        fun save(
            name: String,
            json: String,
        ) {
            archives[name] = listOf("liora.json" to json.encodeToByteArray())
        }

        override suspend fun writeText(
            destination: String,
            text: String,
        ) = error("Backups are archives")

        override suspend fun writeArchive(
            destination: String,
            entries: suspend ArchiveWriter.() -> Unit,
        ) {
            val written = mutableListOf<Pair<String, ByteArray>>()
            ArchiveWriter { name, bytes -> written += name to bytes }.entries()
            archives[destination] = written
        }

        override suspend fun readArchive(
            source: String,
            names: Set<String>,
            onEntry: suspend (name: String, bytes: ByteArray) -> Unit,
        ) {
            val archive = archives[source] ?: throw ExportFileException(IllegalStateException("No file at $source"))
            archive.filter { it.first in names }.forEach { (name, bytes) -> onEntry(name, bytes) }
        }
    }

    private class TestClock(
        var now: Instant = START,
    ) : Clock {
        override fun now() = now

        fun advance(by: Duration) {
            now += by
        }

        companion object {
            val START: Instant = Instant.parse("2026-09-28T16:00:00Z")
        }
    }

    private companion object {
        const val BACKUP = "content://backup.zip"
        const val SECOND_BACKUP = "content://backup-2.zip"
        const val SQUAT = "fedb.Squat"
    }
}
