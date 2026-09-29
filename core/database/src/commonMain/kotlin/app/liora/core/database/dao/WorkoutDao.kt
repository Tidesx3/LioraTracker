package app.liora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.database.model.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

private const val ACTIVE_WORKOUT =
    "SELECT * FROM workout WHERE ended_at IS NULL AND deleted_at IS NULL ORDER BY started_at DESC LIMIT 1"

@Dao
interface WorkoutDao {
    @Query(ACTIVE_WORKOUT)
    fun observeActive(): Flow<WorkoutEntity?>

    @Query(ACTIVE_WORKOUT)
    suspend fun getActive(): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun get(id: String): WorkoutEntity?

    @Query("SELECT * FROM workout_exercise WHERE workout_id = :workoutId AND deleted_at IS NULL ORDER BY position")
    fun observeExercises(workoutId: String): Flow<List<WorkoutExerciseEntity>>

    @Query(
        """
        SELECT s.* FROM workout_set s JOIN workout_exercise we ON we.id = s.workout_exercise_id
        WHERE we.workout_id = :workoutId AND s.deleted_at IS NULL AND we.deleted_at IS NULL
        ORDER BY s.position
        """,
    )
    fun observeSets(workoutId: String): Flow<List<WorkoutSetEntity>>

    @Upsert
    suspend fun upsert(workout: WorkoutEntity)

    @Upsert
    suspend fun upsertExercise(exercise: WorkoutExerciseEntity)

    @Upsert
    suspend fun upsertExercises(exercises: List<WorkoutExerciseEntity>)

    @Upsert
    suspend fun upsertSet(set: WorkoutSetEntity)

    @Upsert
    suspend fun upsertSets(sets: List<WorkoutSetEntity>)
}
