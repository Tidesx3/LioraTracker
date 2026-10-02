package app.liora.core.data.backup

import app.liora.core.data.body.PhotoStorage
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.BackupDao
import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.model.ExerciseNameEntity
import app.liora.core.database.model.SyncMetadata
import app.liora.core.database.model.Syncable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Backups of everything the user made, in one file they keep where they like (see [BackupJson] for
 * what's in it). Restoring merges a backup into this device rather than replacing what's here, so it
 * never loses work: each row keeps whichever version of it is newer.
 */
interface BackupRepository {
    /**
     * Writes a backup to [destination] (see [ExportFiles]). It finishes even when the screen that
     * asked for it closes. Throws [ExportFileException] when the file can't be written.
     */
    suspend fun backUp(destination: String)

    /**
     * The backup at [source], to look at before restoring it. Throws [InvalidBackupException] when the
     * file isn't a backup, [NewerBackupException] when it's from a newer app, and [ExportFileException]
     * when it can't be read.
     */
    suspend fun read(source: String): BackupContents

    /**
     * Adds what [contents] holds to this device, images included: rows that aren't here yet, and newer
     * versions of rows that are. Rows changed here since, and deletions made since, stay. Returns how
     * many rows changed; none when this device had it all already.
     */
    suspend fun restore(contents: BackupContents): Int
}

/** A backup read from a file, with what it holds. */
class BackupContents internal constructor(
    internal val source: String,
    internal val backup: Backup,
) {
    val exportedAt: Instant get() = Instant.fromEpochMilliseconds(backup.exportedAt)
    val workouts: Int get() = backup.workouts.size
    val routines: Int get() = backup.routines.size
    val customExercises: Int get() = backup.exercises.size
    val measurements: Int get() = backup.measurements.size
    val photos: Int get() = backup.photos.size
}

internal class OfflineBackupRepository(
    private val dao: BackupDao,
    private val exerciseDao: ExerciseDao,
    private val files: ExportFiles,
    private val photoStorage: PhotoStorage,
    private val transactions: TransactionRunner,
    private val stamper: SyncStamper,
    private val clock: Clock,
) : BackupRepository {
    // A backup half-written, or images restored without their rows, would be worse than finishing
    // after the screen that started it has gone.
    override suspend fun backUp(destination: String) =
        withContext(NonCancellable) {
            val (backup, images) = transactions.inTransaction { snapshot() }
            val json = withContext(Dispatchers.Default) { BackupJson.encode(backup) }
            files.writeArchive(destination) {
                add(BACKUP_JSON, json.encodeToByteArray())
                for ((file, path) in images) photoStorage.read(path)?.let { add(file, it) }
            }
        }

    override suspend fun read(source: String): BackupContents {
        var json: ByteArray? = null
        files.readArchive(source, setOf(BACKUP_JSON)) { _, bytes -> json = bytes }
        val text = json?.decodeToString() ?: throw InvalidBackupException("No $BACKUP_JSON in the file")
        return BackupContents(source, withContext(Dispatchers.Default) { BackupJson.decode(text) })
    }

    override suspend fun restore(contents: BackupContents): Int =
        withContext(NonCancellable) {
            val backup = contents.backup
            // Images first, so a restored photo never points at a file that isn't there yet. Only photos
            // whose rows will be restored need theirs.
            val localPhotos = dao.progressPhotos().associate { it.id to it.sync }
            val wanted =
                backup.photos
                    .newerThan(localPhotos, { it.id }, { it.hlc })
                    .mapNotNull { photo -> photo.file?.let { it to photo.id } }
                    .toMap()
            val images = mutableMapOf<String, String>()
            if (wanted.isNotEmpty()) {
                files.readArchive(contents.source, wanted.keys) { file, bytes ->
                    val id = wanted.getValue(file)
                    photoStorage.restore(id, bytes)?.let { images[id] = it }
                }
            }
            transactions.inTransaction { merge(backup, images) }
        }

    /** Everything live, and where each photo's image is stored, by its name in the archive. */
    private suspend fun snapshot(): Pair<Backup, Map<String, String>> {
        val photos = dao.progressPhotos().live().sortedBy { it.takenAt }
        val backup =
            Backup(
                format = BackupJson.FORMAT,
                version = BackupJson.VERSION,
                exportedAt = clock.now().toEpochMilliseconds(),
                exercises =
                    dao
                        .customExercises()
                        .live()
                        .sortedBy { it.sync.createdAt }
                        .map { it.toBackup() },
                exerciseSettings =
                    dao
                        .exerciseSettings()
                        .live()
                        .sortedBy { it.exerciseId }
                        .map { it.toBackup() },
                routineFolders =
                    dao
                        .routineFolders()
                        .live()
                        .sortedBy { it.position }
                        .map { it.toBackup() },
                routines = routines(),
                workouts = workouts(),
                measurements =
                    dao
                        .measurements()
                        .live()
                        .sortedBy { it.takenAt }
                        .map { it.toBackup() },
                photos = photos.map { it.toBackup(file = it.localPath?.let { _ -> photoFile(it.id) }) },
                gyms =
                    dao
                        .gymProfiles()
                        .live()
                        .sortedBy { it.sync.createdAt }
                        .map { it.toBackup() },
                preferences =
                    dao
                        .preferences()
                        .live()
                        .sortedBy { it.key }
                        .map { it.toBackup() },
            )
        val images = photos.mapNotNull { photo -> photo.localPath?.let { photoFile(photo.id) to it } }.toMap()
        return backup to images
    }

    /** Live routines with their live exercises and sets, each in order. */
    private suspend fun routines(): List<BackupRoutine> {
        val exercises = dao.routineExercises().live().groupBy { it.routineId }
        val sets = dao.routineSets().live().groupBy { it.routineExerciseId }
        return dao.routines().live().sortedBy { it.position }.map { routine ->
            routine.toBackup(
                exercises[routine.id].orEmpty().sortedBy { it.position }.map { exercise ->
                    exercise.toBackup(sets[exercise.id].orEmpty().sortedBy { it.position }.map { it.toBackup() })
                },
            )
        }
    }

    /** Finished workouts, oldest first, with their live exercises and sets. The one in progress isn't history yet. */
    private suspend fun workouts(): List<BackupWorkout> {
        val exercises = dao.workoutExercises().live().groupBy { it.workoutId }
        val sets = dao.workoutSets().live().groupBy { it.workoutExerciseId }
        return dao.workouts().live().filter { it.endedAt != null }.sortedBy { it.startedAt }.map { workout ->
            workout.toBackup(
                exercises[workout.id].orEmpty().sortedBy { it.position }.map { exercise ->
                    exercise.toBackup(sets[exercise.id].orEmpty().sortedBy { it.position }.map { it.toBackup() })
                },
            )
        }
    }

    /** Writes every row of [backup] that's newer than this device's version of it; returns how many. */
    @Suppress("LongMethod") // one step per table, in the order references point
    private suspend fun merge(
        backup: Backup,
        images: Map<String, String>,
    ): Int {
        var written = 0

        // A backup can't turn a built-in exercise into a custom one.
        val builtIns = dao.builtInExerciseIds().toSet()
        val exercises =
            backup.exercises
                .filter { it.id !in builtIns }
                .newerThan(dao.customExercises().syncById { it.id }, { it.id }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc)) }
        dao.upsertExercises(exercises)
        exercises.forEach { exercise ->
            // Custom exercises are found by their own name, as when they're created.
            exerciseDao.deleteNames(exercise.id)
            exerciseDao.upsertNames(
                listOf(ExerciseNameEntity(exercise.id, ExerciseNameEntity.LOCALE_ANY, exercise.name, isAlias = false)),
            )
        }
        written += exercises.size

        val settings =
            backup.exerciseSettings
                .newerThan(dao.exerciseSettings().syncById { it.exerciseId }, { it.exerciseId }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc)) }
        dao.upsertExerciseSettings(settings)
        written += settings.size

        val folders =
            backup.routineFolders
                .newerThan(dao.routineFolders().syncById { it.id }, { it.id }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc)) }
        dao.upsertRoutineFolders(folders)
        written += folders.size

        val routines =
            backup.routines
                .newerThan(dao.routines().syncById { it.id }, { it.id }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc)) }
        dao.upsertRoutines(routines)
        written += routines.size

        val routineExercises =
            backup.routines
                .flatMap { routine -> routine.exercises.map { routine.id to it } }
                .newerThan(dao.routineExercises().syncById { it.id }, { it.second.id }, { it.second.hlc })
                .map { (routineId, exercise) ->
                    exercise.toEntity(routineId, restored(exercise.createdAt, exercise.hlc))
                }
        dao.upsertRoutineExercises(routineExercises)
        written += routineExercises.size

        val routineSets =
            backup.routines
                .flatMap { it.exercises }
                .flatMap { exercise -> exercise.sets.map { exercise.id to it } }
                .newerThan(dao.routineSets().syncById { it.id }, { it.second.id }, { it.second.hlc })
                .map { (exerciseId, set) -> set.toEntity(exerciseId, restored(set.createdAt, set.hlc)) }
        dao.upsertRoutineSets(routineSets)
        written += routineSets.size

        val workouts =
            backup.workouts
                .newerThan(dao.workouts().syncById { it.id }, { it.id }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc)) }
        dao.upsertWorkouts(workouts)
        written += workouts.size

        val workoutExercises =
            backup.workouts
                .flatMap { workout -> workout.exercises.map { workout.id to it } }
                .newerThan(dao.workoutExercises().syncById { it.id }, { it.second.id }, { it.second.hlc })
                .map { (workoutId, exercise) ->
                    exercise.toEntity(workoutId, restored(exercise.createdAt, exercise.hlc))
                }
        dao.upsertWorkoutExercises(workoutExercises)
        written += workoutExercises.size

        val workoutSets =
            backup.workouts
                .flatMap { it.exercises }
                .flatMap { exercise -> exercise.sets.map { exercise.id to it } }
                .newerThan(dao.workoutSets().syncById { it.id }, { it.second.id }, { it.second.hlc })
                .map { (exerciseId, set) -> set.toEntity(exerciseId, restored(set.createdAt, set.hlc)) }
        dao.upsertWorkoutSets(workoutSets)
        written += workoutSets.size

        val measurements =
            backup.measurements
                .newerThan(dao.measurements().syncById { it.id }, { it.id }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc)) }
        dao.upsertMeasurements(measurements)
        written += measurements.size

        val localPhotos = dao.progressPhotos().associateBy { it.id }
        val photos =
            backup.photos
                .newerThan(localPhotos.mapValues { it.value.sync }, { it.id }, { it.hlc })
                .map { it.toEntity(images[it.id] ?: localPhotos[it.id]?.localPath, restored(it.createdAt, it.hlc)) }
        dao.upsertProgressPhotos(photos)
        written += photos.size

        val gyms =
            backup.gyms
                .newerThan(dao.gymProfiles().syncById { it.id }, { it.id }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc)) }
        dao.upsertGymProfiles(gyms)
        written += gyms.size

        val preferences =
            backup.preferences
                .newerThan(dao.preferences().syncById { it.key }, { it.key }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc)) }
        dao.upsertPreferences(preferences)
        written += preferences.size

        return written
    }

    private suspend fun restored(
        createdAt: Long,
        hlc: String,
    ): SyncMetadata = stamper.restored(createdAt, hlc)
}

/** Rows that aren't tombstones. */
private fun <T : Syncable> List<T>.live(): List<T> = filter { it.sync.deletedAt == null }

/** Each row's sync columns by its key, tombstones included: a deletion made here is a version too. */
private fun <T : Syncable> List<T>.syncById(key: (T) -> String): Map<String, SyncMetadata> =
    associate {
        key(it) to
            it.sync
    }

/**
 * The entries newer than this device's version of their row, by HLC (encoded HLCs sort in causal
 * order), and those it doesn't have at all. An entry as new as the row here is the same version.
 */
private fun <T> List<T>.newerThan(
    local: Map<String, SyncMetadata>,
    key: (T) -> String,
    hlc: (T) -> String,
): List<T> = filter { entry -> local[key(entry)]?.let { hlc(entry) > it.hlc } ?: true }
