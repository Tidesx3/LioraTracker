@file:Suppress("TooManyFunctions") // one pair per synced table

package app.liora.core.data.backup

import app.liora.core.database.model.ExerciseEntity
import app.liora.core.database.model.ExerciseSettingsEntity
import app.liora.core.database.model.GymProfileEntity
import app.liora.core.database.model.MeasurementEntity
import app.liora.core.database.model.PreferenceEntity
import app.liora.core.database.model.ProgressPhotoEntity
import app.liora.core.database.model.RoutineEntity
import app.liora.core.database.model.RoutineExerciseEntity
import app.liora.core.database.model.RoutineFolderEntity
import app.liora.core.database.model.RoutineSetEntity
import app.liora.core.database.model.SyncMetadata
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.database.model.WorkoutSetEntity
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseCategory
import app.liora.core.model.Muscle
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

// Rows to backup entries and back. Kinds a newer version wrote that this one doesn't know fall back like
// they do in the database (see Converters).

internal fun ExerciseEntity.toBackup() =
    BackupExercise(
        id = id,
        name = name,
        trackingType = trackingType.key,
        equipment = equipment.key,
        category = category.key,
        primaryMuscles = primaryMuscles.map { it.key },
        secondaryMuscles = secondaryMuscles.map { it.key },
        instructions = instructions,
        images = imagePaths,
        variationOf = variationOf,
        notes = notes,
        createdAt = sync.createdAt,
        hlc = sync.hlc,
    )

internal fun BackupExercise.toEntity(sync: SyncMetadata) =
    ExerciseEntity(
        id = id,
        name = name,
        trackingType = TrackingType.fromKey(trackingType) ?: TrackingType.WeightReps,
        equipment = Equipment.fromKey(equipment) ?: Equipment.Other,
        category = ExerciseCategory.fromKey(category) ?: ExerciseCategory.Strength,
        primaryMuscles = primaryMuscles.mapNotNull(Muscle::fromKey).toSet(),
        secondaryMuscles = secondaryMuscles.mapNotNull(Muscle::fromKey).toSet(),
        instructions = instructions,
        imagePaths = images,
        isCustom = true,
        variationOf = variationOf,
        notes = notes,
        rank = null,
        sync = sync,
    )

internal fun ExerciseSettingsEntity.toBackup() =
    BackupExerciseSettings(
        exerciseId = exerciseId,
        stickyNote = stickyNote,
        restWorkingSec = restWorkingSeconds,
        restWarmupSec = restWarmupSeconds,
        archived = archived,
        createdAt = sync.createdAt,
        hlc = sync.hlc,
    )

internal fun BackupExerciseSettings.toEntity(sync: SyncMetadata) =
    ExerciseSettingsEntity(
        exerciseId = exerciseId,
        stickyNote = stickyNote,
        restWorkingSeconds = restWorkingSec,
        restWarmupSeconds = restWarmupSec,
        archived = archived,
        sync = sync,
    )

internal fun RoutineFolderEntity.toBackup() =
    BackupRoutineFolder(id = id, name = name, position = position, createdAt = sync.createdAt, hlc = sync.hlc)

internal fun BackupRoutineFolder.toEntity(sync: SyncMetadata) =
    RoutineFolderEntity(id = id, name = name, position = position, sync = sync)

internal fun RoutineEntity.toBackup(exercises: List<BackupRoutineExercise>) =
    BackupRoutine(
        id = id,
        folderId = folderId,
        name = name,
        notes = notes,
        position = position,
        createdAt = sync.createdAt,
        hlc = sync.hlc,
        exercises = exercises,
    )

internal fun BackupRoutine.toEntity(sync: SyncMetadata) =
    RoutineEntity(id = id, folderId = folderId, name = name, notes = notes, position = position, sync = sync)

internal fun RoutineExerciseEntity.toBackup(sets: List<BackupRoutineSet>) =
    BackupRoutineExercise(
        id = id,
        exerciseId = exerciseId,
        position = position,
        supersetGroup = supersetGroup,
        restSec = restSeconds,
        notes = notes,
        createdAt = sync.createdAt,
        hlc = sync.hlc,
        sets = sets,
    )

internal fun BackupRoutineExercise.toEntity(
    routineId: String,
    sync: SyncMetadata,
) = RoutineExerciseEntity(
    id = id,
    routineId = routineId,
    exerciseId = exerciseId,
    position = position,
    supersetGroup = supersetGroup,
    restSeconds = restSec,
    notes = notes,
    sync = sync,
)

internal fun RoutineSetEntity.toBackup() =
    BackupRoutineSet(
        id = id,
        position = position,
        setType = setType.key,
        targetWeightKg = targetWeightKg,
        targetRepsMin = targetRepsMin,
        targetRepsMax = targetRepsMax,
        targetDurationSec = targetDurationSeconds,
        targetDistanceM = targetDistanceMeters,
        targetRpe = targetRpe,
        createdAt = sync.createdAt,
        hlc = sync.hlc,
    )

internal fun BackupRoutineSet.toEntity(
    routineExerciseId: String,
    sync: SyncMetadata,
) = RoutineSetEntity(
    id = id,
    routineExerciseId = routineExerciseId,
    position = position,
    setType = SetType.fromKey(setType) ?: SetType.Normal,
    targetWeightKg = targetWeightKg,
    targetRepsMin = targetRepsMin,
    targetRepsMax = targetRepsMax,
    targetDurationSeconds = targetDurationSec,
    targetDistanceMeters = targetDistanceM,
    targetRpe = targetRpe,
    sync = sync,
)

/** Only finished workouts go into a backup, so [WorkoutEntity.endedAt] is set. */
internal fun WorkoutEntity.toBackup(exercises: List<BackupWorkoutExercise>) =
    BackupWorkout(
        id = id,
        name = name,
        startedAt = startedAt,
        endedAt = checkNotNull(endedAt) { "Workout $id is still in progress" },
        routineId = routineId,
        notes = notes,
        bodyweightKg = bodyweightKg,
        createdAt = sync.createdAt,
        hlc = sync.hlc,
        exercises = exercises,
    )

internal fun BackupWorkout.toEntity(sync: SyncMetadata) =
    WorkoutEntity(
        id = id,
        name = name,
        startedAt = startedAt,
        endedAt = endedAt,
        routineId = routineId,
        notes = notes,
        bodyweightKg = bodyweightKg,
        sync = sync,
    )

internal fun WorkoutExerciseEntity.toBackup(sets: List<BackupWorkoutSet>) =
    BackupWorkoutExercise(
        id = id,
        exerciseId = exerciseId,
        position = position,
        supersetGroup = supersetGroup,
        restSec = restSeconds,
        notes = notes,
        createdAt = sync.createdAt,
        hlc = sync.hlc,
        sets = sets,
    )

internal fun BackupWorkoutExercise.toEntity(
    workoutId: String,
    sync: SyncMetadata,
) = WorkoutExerciseEntity(
    id = id,
    workoutId = workoutId,
    exerciseId = exerciseId,
    position = position,
    supersetGroup = supersetGroup,
    restSeconds = restSec,
    notes = notes,
    sync = sync,
)

internal fun WorkoutSetEntity.toBackup() =
    BackupWorkoutSet(
        id = id,
        position = position,
        setType = setType.key,
        weightKg = weightKg,
        reps = reps,
        durationSec = durationSeconds,
        distanceM = distanceMeters,
        rpe = rpe,
        completedAt = completedAt,
        targetRepsMin = targetRepsMin,
        targetRepsMax = targetRepsMax,
        createdAt = sync.createdAt,
        hlc = sync.hlc,
    )

internal fun BackupWorkoutSet.toEntity(
    workoutExerciseId: String,
    sync: SyncMetadata,
) = WorkoutSetEntity(
    id = id,
    workoutExerciseId = workoutExerciseId,
    position = position,
    setType = SetType.fromKey(setType) ?: SetType.Normal,
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSec,
    distanceMeters = distanceM,
    rpe = rpe,
    completedAt = completedAt,
    targetRepsMin = targetRepsMin,
    targetRepsMax = targetRepsMax,
    sync = sync,
)

internal fun MeasurementEntity.toBackup() =
    BackupMeasurement(
        id = id,
        takenAt = takenAt,
        type = type,
        value = value,
        createdAt = sync.createdAt,
        hlc = sync.hlc,
    )

internal fun BackupMeasurement.toEntity(sync: SyncMetadata) =
    MeasurementEntity(id = id, takenAt = takenAt, type = type, value = value, sync = sync)

/** [file] is where the image goes in the archive, when there is one to go. */
internal fun ProgressPhotoEntity.toBackup(file: String?) =
    BackupPhoto(
        id = id,
        takenAt = takenAt,
        pose = pose,
        file = file,
        blobId = blobId,
        notes = notes,
        createdAt = sync.createdAt,
        hlc = sync.hlc,
    )

internal fun BackupPhoto.toEntity(
    localPath: String?,
    sync: SyncMetadata,
) = ProgressPhotoEntity(
    id = id,
    takenAt = takenAt,
    pose = pose,
    localPath = localPath,
    blobId = blobId,
    notes = notes,
    sync = sync,
)

/** The equipment lists are JSON in the table already; in the backup they're part of the document. */
internal fun GymProfileEntity.toBackup() =
    BackupGym(
        id = id,
        name = name,
        unit = unit,
        barbellKg = barbellKg,
        ezBarKg = ezBarKg,
        plates = jsonList(plates),
        dumbbells = jsonList(dumbbells),
        stackStepKg = stackStepKg,
        createdAt = sync.createdAt,
        hlc = sync.hlc,
    )

internal fun BackupGym.toEntity(sync: SyncMetadata) =
    GymProfileEntity(
        id = id,
        name = name,
        unit = unit,
        barbellKg = barbellKg,
        ezBarKg = ezBarKg,
        plates = plates.toString(),
        dumbbells = dumbbells.toString(),
        stackStepKg = stackStepKg,
        sync = sync,
    )

internal fun PreferenceEntity.toBackup() =
    BackupPreference(key = key, value = value, createdAt = sync.createdAt, hlc = sync.hlc)

internal fun BackupPreference.toEntity(sync: SyncMetadata) = PreferenceEntity(key = key, value = value, sync = sync)

/** A list that doesn't parse goes in empty, as the gym reads it anyway (see `GymProfileRepository`). */
private fun jsonList(text: String) = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: JsonArray(emptyList())
