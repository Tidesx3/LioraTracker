package app.liora.core.data.workout

import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.domain.Supersets

// Writes shared by the workout in progress and finished ones being corrected. Each runs inside the
// caller's transaction and stamps only the rows it really changes.

/** Names the workout; blank goes back to the default name. */
internal suspend fun WorkoutDao.rename(
    workout: WorkoutEntity,
    name: String,
    stamper: SyncStamper,
) {
    val trimmed = name.trim().ifEmpty { null }
    if (trimmed != workout.name) upsert(workout.copy(name = trimmed, sync = stamper.touch(workout.sync)))
}

/** Tombstones sets that weren't ticked off and exercises left empty, repairing supersets they split. */
internal suspend fun WorkoutDao.dropWhatWasntDone(
    rows: WorkoutRows,
    stamper: SyncStamper,
) {
    val open = rows.sets.filter { it.completedAt == null }
    if (open.isNotEmpty()) upsertSets(open.map { it.copy(sync = stamper.tombstone(it.sync)) })
    val withSets =
        rows.sets
            .filter { it.completedAt != null }
            .map { it.workoutExerciseId }
            .toSet()
    val kept = rows.exercises.sortedBy { it.position }.filter { it.id in withSets }
    val groups = Supersets.normalize(kept.map { it.supersetGroup })
    saveExercises(
        rows.exercises,
        kept.zip(groups) { exercise, group -> exercise.copy(supersetGroup = group) },
        stamper,
    )
}

/**
 * Writes the rows of [updated] that differ from [stored] with a touched sync stamp, and tombstones
 * stored rows that are gone, so only real changes sync.
 */
internal suspend fun WorkoutDao.saveExercises(
    stored: List<WorkoutExerciseEntity>,
    updated: List<WorkoutExerciseEntity>,
    stamper: SyncStamper,
) {
    val storedById = stored.associateBy { it.id }
    val keptIds = updated.map { it.id }.toSet()
    val writes =
        updated.mapNotNull { row ->
            val previous = storedById[row.id]
            when {
                previous == null -> row
                row == previous -> null
                else -> row.copy(sync = stamper.touch(previous.sync))
            }
        } + stored.filter { it.id !in keptIds }.map { it.copy(sync = stamper.tombstone(it.sync)) }
    if (writes.isNotEmpty()) upsertExercises(writes)
}
