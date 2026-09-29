package app.liora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.liora.core.database.model.ExerciseEntity
import app.liora.core.database.model.ExerciseNameEntity
import app.liora.core.database.model.ExerciseSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise WHERE deleted_at IS NULL")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise_name")
    fun observeNames(): Flow<List<ExerciseNameEntity>>

    @Query("SELECT * FROM exercise_settings WHERE deleted_at IS NULL")
    fun observeSettings(): Flow<List<ExerciseSettingsEntity>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("SELECT id FROM exercise WHERE is_custom = 0")
    suspend fun builtInIds(): List<String>

    @Upsert
    suspend fun upsertExercises(exercises: List<ExerciseEntity>)

    @Upsert
    suspend fun upsertNames(names: List<ExerciseNameEntity>)

    @Upsert
    suspend fun upsertSettings(settings: ExerciseSettingsEntity)

    @Query("DELETE FROM exercise_name WHERE exercise_id IN (SELECT id FROM exercise WHERE is_custom = 0)")
    suspend fun deleteBuiltInNames()
}
