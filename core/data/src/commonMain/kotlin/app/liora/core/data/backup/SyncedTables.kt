package app.liora.core.data.backup

import app.liora.core.data.sync.NEWEST_MERGED_HLC
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.dao.BackupDao
import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.dao.LocalMetaDao
import app.liora.core.database.model.ExerciseNameEntity
import app.liora.core.database.model.LocalMetaEntity
import app.liora.core.database.model.ProgressPhotoEntity
import app.liora.core.database.model.SyncMetadata
import app.liora.core.database.model.Syncable
import app.liora.core.database.model.SyncedRow

/** Which rows of the synced tables a [Snapshot] takes. */
internal enum class RowSelection {
    /** What's live: a backup. */
    Live,

    /** What changed since the last push, deletions too, each with its parents to nest under: a sync batch. */
    Changed,

    /** Everything, deletions too: what a store needs to start from. */
    All,
    ;

    fun takes(row: Syncable): Boolean =
        when (this) {
            Live -> row.sync.deletedAt == null
            Changed -> row.sync.dirty
            All -> true
        }

    /** Whether [row] goes in, given the [children] under it that go in: a change brings its parents along. */
    fun takesParent(
        row: Syncable,
        children: List<*>,
    ): Boolean = takes(row) || (this == Changed && children.isNotEmpty())
}

/** A backup of the rows a [RowSelection] took, and where each photo's image is stored, by its name in the backup. */
internal class Snapshot(
    val backup: Backup,
    val images: Map<String, String>,
)

/** What a merge did: how many rows it wrote, and the images of the photos it deleted, to remove once committed. */
internal class Merged(
    val written: Int,
    val deletedImages: List<String>,
)

/**
 * The synced tables read as a backup, and backups merged back into them: what backups and sync batches
 * are made from and merged into. A merge keeps, per row, whichever version is newer by HLC, so merging the
 * same rows again changes nothing.
 */
internal class SyncedTables(
    private val dao: BackupDao,
    private val exerciseDao: ExerciseDao,
    private val meta: LocalMetaDao,
    private val stamper: SyncStamper,
) {
    /**
     * The rows [selection] takes, as a backup made at [madeAt]. [imageName] names a photo's image in it.
     * The workout in progress isn't history yet, so it stays out. Run it in a transaction.
     */
    suspend fun snapshot(
        selection: RowSelection,
        madeAt: Long,
        imageName: (photoId: String) -> String,
    ): Snapshot {
        val photos = dao.progressPhotos().filter(selection::takes).sortedBy { it.takenAt }
        val backup =
            Backup(
                format = BackupJson.FORMAT,
                version = BackupJson.VERSION,
                exportedAt = madeAt,
                exercises =
                    dao
                        .customExercises()
                        .filter(selection::takes)
                        .sortedBy { it.sync.createdAt }
                        .map { it.toBackup() },
                exerciseSettings =
                    dao
                        .exerciseSettings()
                        .filter(selection::takes)
                        .sortedBy { it.exerciseId }
                        .map { it.toBackup() },
                routineFolders =
                    dao
                        .routineFolders()
                        .filter(selection::takes)
                        .sortedBy { it.position }
                        .map { it.toBackup() },
                routines = routines(selection),
                workouts = workouts(selection),
                measurements =
                    dao
                        .measurements()
                        .filter(selection::takes)
                        .sortedBy { it.takenAt }
                        .map { it.toBackup() },
                photos = photos.map { photo -> photo.toBackup(file = photo.image()?.let { imageName(photo.id) }) },
                gyms =
                    dao
                        .gymProfiles()
                        .filter(selection::takes)
                        .sortedBy { it.sync.createdAt }
                        .map { it.toBackup() },
                preferences =
                    dao
                        .preferences()
                        .filter(selection::takes)
                        .sortedBy { it.key }
                        .map { it.toBackup() },
            )
        val images = photos.mapNotNull { photo -> photo.image()?.let { imageName(photo.id) to it } }.toMap()
        return Snapshot(backup, images)
    }

    /** Routines with their exercises and sets, each in order. */
    private suspend fun routines(selection: RowSelection): List<BackupRoutine> {
        val sets = dao.routineSets().filter(selection::takes).groupBy { it.routineExerciseId }
        val exercises = dao.routineExercises().withChildren(selection, sets).groupBy { it.first.routineId }
        return dao
            .routines()
            .withChildren(selection, exercises)
            .sortedBy { it.first.position }
            .map { (routine, exercises) ->
                routine.toBackup(
                    exercises.sortedBy { it.first.position }.map { (exercise, sets) ->
                        exercise.toBackup(sets.sortedBy { it.position }.map { it.toBackup() })
                    },
                )
            }
    }

    /** Finished workouts, oldest first, with their exercises and sets. */
    private suspend fun workouts(selection: RowSelection): List<BackupWorkout> {
        val sets = dao.workoutSets().filter(selection::takes).groupBy { it.workoutExerciseId }
        val exercises = dao.workoutExercises().withChildren(selection, sets).groupBy { it.first.workoutId }
        return dao
            .workouts()
            .filter { it.endedAt != null }
            .withChildren(selection, exercises)
            .sortedBy { it.first.startedAt }
            .map { (workout, exercises) ->
                workout.toBackup(
                    exercises.sortedBy { it.first.position }.map { (exercise, sets) ->
                        exercise.toBackup(sets.sortedBy { it.position }.map { it.toBackup() })
                    },
                )
            }
    }

    /**
     * The photos in [backup] newer than this device's version, whose image comes with them and isn't here
     * yet: each image's name in the backup to its photo's id.
     */
    suspend fun imagesWanted(backup: Backup): Map<String, String> {
        val local = dao.progressPhotos().associateBy { it.id }
        return backup.photos
            .filter { it.deletedAt == null && local[it.id]?.image() == null }
            .newerThan(local.mapValues { it.value.sync }, { it.id }, { it.hlc })
            .mapNotNull { photo -> photo.file?.let { it to photo.id } }
            .toMap()
    }

    /**
     * Writes every row of [backup] that's newer than this device's version of it; [images] are the photos'
     * images fetched beforehand, by photo id. Rows are [dirty] when the sync store may not have them (a
     * restored backup), not when they came from there. Run it in a transaction.
     */
    @Suppress("LongMethod") // one step per table, in the order references point
    suspend fun merge(
        backup: Backup,
        images: Map<String, String>,
        dirty: Boolean,
    ): Merged {
        var written = 0

        suspend fun restored(
            createdAt: Long,
            hlc: String,
            deletedAt: Long?,
        ) = stamper.restored(createdAt, hlc, deletedAt, dirty)

        // A backup can't turn a built-in exercise into a custom one.
        val builtIns = dao.builtInExerciseIds().toSet()
        val exercises =
            backup.exercises
                .filter { it.id !in builtIns }
                .newerThan(dao.customExercises().syncById { it.id }, { it.id }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc, it.deletedAt)) }
        dao.upsertExercises(exercises)
        exercises.forEach { exercise ->
            // Custom exercises are found by their own name, as when they're created; deleted ones aren't.
            exerciseDao.deleteNames(exercise.id)
            if (exercise.sync.deletedAt == null) {
                val name =
                    ExerciseNameEntity(exercise.id, ExerciseNameEntity.LOCALE_ANY, exercise.name, isAlias = false)
                exerciseDao.upsertNames(listOf(name))
            }
        }
        written += exercises.size

        val settings =
            backup.exerciseSettings
                .newerThan(dao.exerciseSettings().syncById { it.exerciseId }, { it.exerciseId }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc, it.deletedAt)) }
        dao.upsertExerciseSettings(settings)
        written += settings.size

        val folders =
            backup.routineFolders
                .newerThan(dao.routineFolders().syncById { it.id }, { it.id }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc, it.deletedAt)) }
        dao.upsertRoutineFolders(folders)
        written += folders.size

        val routines =
            backup.routines
                .newerThan(dao.routines().syncById { it.id }, { it.id }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc, it.deletedAt)) }
        dao.upsertRoutines(routines)
        written += routines.size

        val routineExercises =
            backup.routines
                .flatMap { routine -> routine.exercises.map { routine.id to it } }
                .newerThan(dao.routineExercises().syncById { it.id }, { it.second.id }, { it.second.hlc })
                .map { (routineId, exercise) ->
                    exercise.toEntity(routineId, restored(exercise.createdAt, exercise.hlc, exercise.deletedAt))
                }
        dao.upsertRoutineExercises(routineExercises)
        written += routineExercises.size

        val routineSets =
            backup.routines
                .flatMap { it.exercises }
                .flatMap { exercise -> exercise.sets.map { exercise.id to it } }
                .newerThan(dao.routineSets().syncById { it.id }, { it.second.id }, { it.second.hlc })
                .map { (exerciseId, set) -> set.toEntity(exerciseId, restored(set.createdAt, set.hlc, set.deletedAt)) }
        dao.upsertRoutineSets(routineSets)
        written += routineSets.size

        val workouts =
            backup.workouts
                .newerThan(dao.workouts().syncById { it.id }, { it.id }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc, it.deletedAt)) }
        dao.upsertWorkouts(workouts)
        written += workouts.size

        val workoutExercises =
            backup.workouts
                .flatMap { workout -> workout.exercises.map { workout.id to it } }
                .newerThan(dao.workoutExercises().syncById { it.id }, { it.second.id }, { it.second.hlc })
                .map { (workoutId, exercise) ->
                    exercise.toEntity(workoutId, restored(exercise.createdAt, exercise.hlc, exercise.deletedAt))
                }
        dao.upsertWorkoutExercises(workoutExercises)
        written += workoutExercises.size

        val workoutSets =
            backup.workouts
                .flatMap { it.exercises }
                .flatMap { exercise -> exercise.sets.map { exercise.id to it } }
                .newerThan(dao.workoutSets().syncById { it.id }, { it.second.id }, { it.second.hlc })
                .map { (exerciseId, set) -> set.toEntity(exerciseId, restored(set.createdAt, set.hlc, set.deletedAt)) }
        dao.upsertWorkoutSets(workoutSets)
        written += workoutSets.size

        val measurements =
            backup.measurements
                .newerThan(dao.measurements().syncById { it.id }, { it.id }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc, it.deletedAt)) }
        dao.upsertMeasurements(measurements)
        written += measurements.size

        val localPhotos = dao.progressPhotos().associateBy { it.id }
        val photos =
            backup.photos
                .newerThan(localPhotos.mapValues { it.value.sync }, { it.id }, { it.hlc })
                .map {
                    it.toEntity(
                        images[it.id] ?: localPhotos[it.id]?.localPath,
                        restored(it.createdAt, it.hlc, it.deletedAt),
                    )
                }
        dao.upsertProgressPhotos(photos)
        written += photos.size
        // A photo deleted elsewhere loses its image here too, as when it's deleted here.
        val deletedImages = photos.filter { it.sync.deletedAt != null }.mapNotNull { localPhotos[it.id]?.image() }

        val gyms =
            backup.gyms
                .newerThan(dao.gymProfiles().syncById { it.id }, { it.id }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc, it.deletedAt)) }
        dao.upsertGymProfiles(gyms)
        written += gyms.size

        val preferences =
            backup.preferences
                .newerThan(dao.preferences().syncById { it.key }, { it.key }, { it.hlc })
                .map { it.toEntity(restored(it.createdAt, it.hlc, it.deletedAt)) }
        dao.upsertPreferences(preferences)
        written += preferences.size

        rememberNewest(backup)
        return Merged(written, deletedImages)
    }

    /** Keeps the newest HLC merged, which the clock starts past after a restart. */
    private suspend fun rememberNewest(backup: Backup) {
        val newest = backup.hlcs().maxOrNull() ?: return
        if (meta.get(NEWEST_MERGED_HLC)?.let { newest > it } != false) {
            meta.put(LocalMetaEntity(NEWEST_MERGED_HLC, newest))
        }
    }
}

/** A photo's image, while it has one: a deleted photo's is gone. */
private fun ProgressPhotoEntity.image(): String? = localPath.takeIf { sync.deletedAt == null }

/** Each row with its [children] (by parent id), when [selection] takes it or them. */
private fun <P : SyncedRow, C> List<P>.withChildren(
    selection: RowSelection,
    children: Map<String, List<C>>,
): List<Pair<P, List<C>>> =
    mapNotNull { row ->
        val own = children[row.id].orEmpty()
        if (selection.takesParent(row, own)) row to own else null
    }

/** Each row's sync columns by its key, tombstones included: a deletion made here is a version too. */
private fun <T : Syncable> List<T>.syncById(key: (T) -> String): Map<String, SyncMetadata> =
    associate { key(it) to it.sync }

/**
 * The entries newer than this device's version of their row, by HLC (encoded HLCs sort in causal
 * order), and those it doesn't have at all. An entry as new as the row here is the same version.
 */
private fun <T> List<T>.newerThan(
    local: Map<String, SyncMetadata>,
    key: (T) -> String,
    hlc: (T) -> String,
): List<T> = filter { entry -> local[key(entry)]?.let { hlc(entry) > it.hlc } ?: true }
