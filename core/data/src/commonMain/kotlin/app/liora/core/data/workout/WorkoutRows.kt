package app.liora.core.data.workout

import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.database.model.SyncMetadata
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.database.model.WorkoutSetEntity
import app.liora.core.model.ActiveWorkout
import app.liora.core.model.ExerciseSettings
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.WorkoutExercise
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A workout as stored, with its live exercises and sets: the rows every edit reads, changes and writes back. */
internal class WorkoutRows(
    val workout: WorkoutEntity,
    val exercises: List<WorkoutExerciseEntity>,
    val sets: List<WorkoutSetEntity>,
) {
    fun toModel(): ActiveWorkout = workout.toActiveWorkout(exercises, sets)

    fun setsOf(workoutExerciseId: String): List<WorkoutSetEntity> =
        sets.filter { it.workoutExerciseId == workoutExerciseId }.sortedBy { it.position }
}

internal suspend fun WorkoutDao.activeRows(): WorkoutRows? {
    val workout = getActive() ?: return null
    return WorkoutRows(workout, exercisesOf(workout.id), setsOf(workout.id))
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

/** A set with nothing typed or logged yet. */
internal fun emptySetRow(
    id: String,
    workoutExerciseId: String,
    position: Int,
    type: SetType,
    sync: SyncMetadata,
) = WorkoutSetEntity(
    id = id,
    workoutExerciseId = workoutExerciseId,
    position = position,
    setType = type,
    weightKg = null,
    reps = null,
    durationSeconds = null,
    distanceMeters = null,
    rpe = null,
    completedAt = null,
    sync = sync,
)

internal suspend fun ExerciseDao.trackingTypeOf(exerciseId: String): TrackingType =
    get(exerciseId)?.trackingType ?: TrackingType.WeightReps

internal suspend fun ExerciseDao.settingsOf(exerciseId: String): ExerciseSettings =
    getSettings(exerciseId)?.takeIf { it.sync.deletedAt == null }?.let {
        ExerciseSettings(
            stickyNote = it.stickyNote,
            restWorkingSeconds = it.restWorkingSeconds,
            restWarmupSeconds = it.restWarmupSeconds,
            archived = it.archived,
        )
    } ?: ExerciseSettings()

internal fun WorkoutEntity.toActiveWorkout(
    exercises: List<WorkoutExerciseEntity> = emptyList(),
    sets: List<WorkoutSetEntity> = emptyList(),
): ActiveWorkout =
    ActiveWorkout(
        id = id,
        name = name,
        startedAt = Instant.fromEpochMilliseconds(startedAt),
        routineId = routineId,
        exercises = exercises.toModels(sets.groupBy { it.workoutExerciseId }),
    )

/** Exercises with their sets, in order; [setsByExercise] may hold other workouts' sets too. */
internal fun List<WorkoutExerciseEntity>.toModels(setsByExercise: Map<String, List<WorkoutSetEntity>>) =
    sortedBy { it.position }.map { exercise ->
        WorkoutExercise(
            id = exercise.id,
            exerciseId = exercise.exerciseId,
            supersetGroup = exercise.supersetGroup,
            restSeconds = exercise.restSeconds,
            notes = exercise.notes,
            sets = setsByExercise[exercise.id].orEmpty().sortedBy { it.position }.map { it.toLoggedSet() },
        )
    }

internal fun WorkoutSetEntity.toLoggedSet() =
    LoggedSet(
        id = id,
        type = setType,
        weight = weightKg?.let(::Mass),
        reps = reps,
        duration = durationSeconds?.seconds,
        distanceMeters = distanceMeters,
        rpe = rpe,
        completedAt = completedAt?.let(Instant::fromEpochMilliseconds),
        targetReps = RepRange.of(targetRepsMin, targetRepsMax),
    )

/** [set]'s values on this row; identity, position and sync stay. */
internal fun WorkoutSetEntity.withValues(set: LoggedSet) =
    copy(
        setType = set.type,
        weightKg = set.weight?.kilograms,
        reps = set.reps,
        durationSeconds = set.duration?.inWholeSeconds?.toInt(),
        distanceMeters = set.distanceMeters,
        rpe = set.rpe,
        completedAt = set.completedAt?.toEpochMilliseconds(),
        targetRepsMin = set.targetReps?.min,
        targetRepsMax = set.targetReps?.max,
    )

/** Clears the values [trackingType] has no column for, e.g. reps after switching to a timed exercise. */
internal fun LoggedSet.keepOnly(trackingType: TrackingType) =
    copy(
        weight = weight.takeIf { trackingType.usesWeight },
        reps = reps.takeIf { trackingType.usesReps },
        duration = duration.takeIf { trackingType.usesDuration },
        distanceMeters = distanceMeters.takeIf { trackingType.usesDistance },
    )
