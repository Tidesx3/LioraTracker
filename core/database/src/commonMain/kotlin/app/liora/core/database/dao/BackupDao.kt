package app.liora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
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
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.database.model.WorkoutSetEntity

/**
 * Every synced table whole, tombstones included: what a backup is made from, and what a restored one
 * is merged into. A user's lifetime of training is tens of thousands of rows, which fit in memory.
 */
@Dao
@Suppress("TooManyFunctions") // a read and a write per synced table
interface BackupDao {
    @Query("SELECT * FROM exercise WHERE is_custom = 1")
    suspend fun customExercises(): List<ExerciseEntity>

    /** Built-in ids, which a backup can't overwrite with a custom exercise. */
    @Query("SELECT id FROM exercise WHERE is_custom = 0")
    suspend fun builtInExerciseIds(): List<String>

    @Query("SELECT * FROM exercise_settings")
    suspend fun exerciseSettings(): List<ExerciseSettingsEntity>

    @Query("SELECT * FROM routine_folder")
    suspend fun routineFolders(): List<RoutineFolderEntity>

    @Query("SELECT * FROM routine")
    suspend fun routines(): List<RoutineEntity>

    @Query("SELECT * FROM routine_exercise")
    suspend fun routineExercises(): List<RoutineExerciseEntity>

    @Query("SELECT * FROM routine_set")
    suspend fun routineSets(): List<RoutineSetEntity>

    @Query("SELECT * FROM workout")
    suspend fun workouts(): List<WorkoutEntity>

    @Query("SELECT * FROM workout_exercise")
    suspend fun workoutExercises(): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_set")
    suspend fun workoutSets(): List<WorkoutSetEntity>

    @Query("SELECT * FROM measurement")
    suspend fun measurements(): List<MeasurementEntity>

    @Query("SELECT * FROM progress_photo")
    suspend fun progressPhotos(): List<ProgressPhotoEntity>

    @Query("SELECT * FROM gym_profile")
    suspend fun gymProfiles(): List<GymProfileEntity>

    @Query("SELECT * FROM preference")
    suspend fun preferences(): List<PreferenceEntity>

    @Upsert
    suspend fun upsertExercises(rows: List<ExerciseEntity>)

    @Upsert
    suspend fun upsertExerciseSettings(rows: List<ExerciseSettingsEntity>)

    @Upsert
    suspend fun upsertRoutineFolders(rows: List<RoutineFolderEntity>)

    @Upsert
    suspend fun upsertRoutines(rows: List<RoutineEntity>)

    @Upsert
    suspend fun upsertRoutineExercises(rows: List<RoutineExerciseEntity>)

    @Upsert
    suspend fun upsertRoutineSets(rows: List<RoutineSetEntity>)

    @Upsert
    suspend fun upsertWorkouts(rows: List<WorkoutEntity>)

    @Upsert
    suspend fun upsertWorkoutExercises(rows: List<WorkoutExerciseEntity>)

    @Upsert
    suspend fun upsertWorkoutSets(rows: List<WorkoutSetEntity>)

    @Upsert
    suspend fun upsertMeasurements(rows: List<MeasurementEntity>)

    @Upsert
    suspend fun upsertProgressPhotos(rows: List<ProgressPhotoEntity>)

    @Upsert
    suspend fun upsertGymProfiles(rows: List<GymProfileEntity>)

    @Upsert
    suspend fun upsertPreferences(rows: List<PreferenceEntity>)
}
