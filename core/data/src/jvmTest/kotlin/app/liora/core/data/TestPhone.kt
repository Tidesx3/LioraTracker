package app.liora.core.data

import app.liora.core.common.Hlc
import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.backup.ExportFiles
import app.liora.core.data.backup.OfflineBackupRepository
import app.liora.core.data.backup.SyncedTables
import app.liora.core.data.body.ImportedPhoto
import app.liora.core.data.body.OfflineBodyRepository
import app.liora.core.data.body.OfflineProgressPhotoRepository
import app.liora.core.data.body.PhotoStorage
import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.gym.OfflineGymProfileRepository
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.settings.OfflineSettingsRepository
import app.liora.core.data.sync.DeviceIdentity
import app.liora.core.data.sync.NEWEST_MERGED_HLC
import app.liora.core.data.sync.SyncEngine
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.workout.LocalRestTimerRepository
import app.liora.core.data.workout.OfflineActiveWorkoutRepository
import app.liora.core.data.workout.OfflineWorkoutHistoryRepository
import app.liora.core.database.LioraDatabase
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * One phone's database and everything that writes to it, for tests that move data from phone to phone.
 * [files] is where it saves backups; [restarted] is the same phone after its process restarted.
 */
internal class TestPhone(
    private val name: String,
    val clock: TestClock = TestClock(),
    private val files: ExportFiles = NoFiles,
    val database: LioraDatabase = inMemoryLioraDatabase(),
    val storage: FakePhotos = FakePhotos(),
) {
    private val transactions = TransactionRunner(database)
    private val ids = IdGenerator(clock)
    private val meta = database.localMetaDao()
    private val stamper =
        SyncStamper(HybridLogicalClock(clock, seen = { meta.get(NEWEST_MERGED_HLC)?.let(Hlc::parse) }) { name }, clock)
    val exercises = OfflineExerciseRepository(database.exerciseDao(), transactions, ids, stamper)
    val routines = OfflineRoutineRepository(database.routineDao(), transactions, ids, stamper)
    private val restTimer = LocalRestTimerRepository(meta, clock)
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
        OfflineGymProfileRepository(database.gymProfileDao(), meta, settings, transactions, ids, stamper)
    private val tables = SyncedTables(database.backupDao(), database.exerciseDao(), meta, stamper)
    val backups = OfflineBackupRepository(tables, files, storage, transactions, clock)
    val sync =
        SyncEngine(
            tables,
            database.syncDao(),
            meta,
            DeviceIdentity(meta, ids),
            storage,
            transactions,
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

    /** This phone after Android ended its process: the same database and files, everything else new. */
    fun restarted() = TestPhone(name, clock, files, database, storage)

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
            ZONE,
        )
        photos.add("front.jpg", PhotoPose.Front)
        clock.advance(1.days)
        // Still going when the data leaves the phone.
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

    companion object {
        val ZONE = TimeZone.of("Europe/Berlin")
        const val SQUAT = "fedb.Squat"
    }
}

/** Images as bytes in memory, under the paths the real storage would give them. */
internal class FakePhotos : PhotoStorage {
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

internal class TestClock(
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

/** For phones that never save files. */
private object NoFiles : ExportFiles {
    override suspend fun writeText(
        destination: String,
        text: String,
    ) = error("No files here")

    override suspend fun readText(source: String) = error("No files here")

    override suspend fun writeArchive(
        destination: String,
        entries: suspend app.liora.core.data.backup.ArchiveWriter.() -> Unit,
    ) = error("No files here")

    override suspend fun readArchive(
        source: String,
        names: Set<String>,
        onEntry: suspend (name: String, bytes: ByteArray) -> Unit,
    ) = error("No files here")
}
