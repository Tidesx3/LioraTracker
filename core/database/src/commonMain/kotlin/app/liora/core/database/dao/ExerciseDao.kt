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

    @Query("DELETE FROM exercise_name WHERE exercise_id = :exerciseId")
    suspend fun deleteNames(exerciseId: String)

    @Query("SELECT * FROM exercise_settings WHERE exercise_id = :exerciseId")
    suspend fun getSettings(exerciseId: String): ExerciseSettingsEntity?

    /** Whether any live workout or routine refers to the exercise (it then can't be deleted, only archived). */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM workout_exercise we JOIN workout w ON w.id = we.workout_id
            WHERE we.exercise_id = :exerciseId AND we.deleted_at IS NULL AND w.deleted_at IS NULL
        ) OR EXISTS(
            SELECT 1 FROM routine_exercise re JOIN routine r ON r.id = re.routine_id
            WHERE re.exercise_id = :exerciseId AND re.deleted_at IS NULL AND r.deleted_at IS NULL
        )
        """,
    )
    suspend fun isInUse(exerciseId: String): Boolean

    /** Whether sets were logged for the exercise; its tracking type is then locked so no data is lost. */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM workout_set s
            JOIN workout_exercise we ON we.id = s.workout_exercise_id
            JOIN workout w ON w.id = we.workout_id
            WHERE we.exercise_id = :exerciseId
              AND s.deleted_at IS NULL AND we.deleted_at IS NULL AND w.deleted_at IS NULL
        )
        """,
    )
    suspend fun hasLoggedSets(exerciseId: String): Boolean
}
