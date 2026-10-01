package app.liora.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.database.model.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

private const val ACTIVE_WORKOUT =
    "SELECT * FROM workout WHERE ended_at IS NULL AND deleted_at IS NULL ORDER BY started_at DESC LIMIT 1"

private const val EXERCISES_OF_WORKOUT =
    "SELECT * FROM workout_exercise WHERE workout_id = :workoutId AND deleted_at IS NULL ORDER BY position"

private const val SETS_OF_WORKOUT =
    """
    SELECT s.* FROM workout_set s JOIN workout_exercise we ON we.id = s.workout_exercise_id
    WHERE we.workout_id = :workoutId AND s.deleted_at IS NULL AND we.deleted_at IS NULL
    ORDER BY s.position
    """

/**
 * The completed sets of each exercise's most recent finished session that started before `:before`:
 * the "previous" column and the values a set logs when ticked without typing.
 */
private const val LAST_SESSION_SETS =
    """
    SELECT we.exercise_id AS exercise_id, s.* FROM workout_set s
    JOIN workout_exercise we ON we.id = s.workout_exercise_id
    JOIN workout w ON w.id = we.workout_id
    WHERE we.exercise_id IN (:exerciseIds)
      AND s.completed_at IS NOT NULL AND s.deleted_at IS NULL AND we.deleted_at IS NULL
      AND w.ended_at IS NOT NULL AND w.deleted_at IS NULL
      AND w.id = (
        SELECT w2.id FROM workout w2
        JOIN workout_exercise we2 ON we2.workout_id = w2.id
        JOIN workout_set s2 ON s2.workout_exercise_id = we2.id
        WHERE we2.exercise_id = we.exercise_id
          AND s2.completed_at IS NOT NULL AND s2.deleted_at IS NULL AND we2.deleted_at IS NULL
          AND w2.ended_at IS NOT NULL AND w2.deleted_at IS NULL AND w2.started_at < :before
        ORDER BY w2.started_at DESC LIMIT 1
      )
    ORDER BY we.position, s.position
    """

/**
 * Every completed set of these exercises in finished workouts that started before `:before`: the
 * history personal records are measured against.
 */
private const val HISTORY_SETS =
    """
    SELECT we.exercise_id AS exercise_id, s.* FROM workout_set s
    JOIN workout_exercise we ON we.id = s.workout_exercise_id
    JOIN workout w ON w.id = we.workout_id
    WHERE we.exercise_id IN (:exerciseIds)
      AND s.completed_at IS NOT NULL AND s.deleted_at IS NULL AND we.deleted_at IS NULL
      AND w.ended_at IS NOT NULL AND w.deleted_at IS NULL AND w.started_at < :before
    """

/** History: every finished workout, newest first. */
private const val FINISHED_WORKOUTS =
    "SELECT * FROM workout WHERE ended_at IS NOT NULL AND deleted_at IS NULL ORDER BY started_at DESC"

private const val EXERCISES_OF_FINISHED =
    """
    SELECT we.* FROM workout_exercise we JOIN workout w ON w.id = we.workout_id
    WHERE w.ended_at IS NOT NULL AND w.deleted_at IS NULL AND we.deleted_at IS NULL
    """

private const val SETS_OF_FINISHED =
    """
    SELECT s.* FROM workout_set s
    JOIN workout_exercise we ON we.id = s.workout_exercise_id
    JOIN workout w ON w.id = we.workout_id
    WHERE w.ended_at IS NOT NULL AND w.deleted_at IS NULL AND we.deleted_at IS NULL AND s.deleted_at IS NULL
    """

/** A set together with the exercise it was logged for. */
data class ExerciseSetRow(
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @Embedded val set: WorkoutSetEntity,
)

@Dao
interface WorkoutDao {
    @Query(ACTIVE_WORKOUT)
    fun observeActive(): Flow<WorkoutEntity?>

    @Query(ACTIVE_WORKOUT)
    suspend fun getActive(): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun get(id: String): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE id = :id")
    fun observe(id: String): Flow<WorkoutEntity?>

    @Query(EXERCISES_OF_WORKOUT)
    fun observeExercises(workoutId: String): Flow<List<WorkoutExerciseEntity>>

    @Query(EXERCISES_OF_WORKOUT)
    suspend fun exercisesOf(workoutId: String): List<WorkoutExerciseEntity>

    @Query(SETS_OF_WORKOUT)
    fun observeSets(workoutId: String): Flow<List<WorkoutSetEntity>>

    @Query(SETS_OF_WORKOUT)
    suspend fun setsOf(workoutId: String): List<WorkoutSetEntity>

    @Query(LAST_SESSION_SETS)
    fun observeLastSessionSets(
        exerciseIds: List<String>,
        before: Long,
    ): Flow<List<ExerciseSetRow>>

    @Query(LAST_SESSION_SETS)
    suspend fun lastSessionSets(
        exerciseIds: List<String>,
        before: Long,
    ): List<ExerciseSetRow>

    @Query(HISTORY_SETS)
    fun observeHistorySets(
        exerciseIds: List<String>,
        before: Long,
    ): Flow<List<ExerciseSetRow>>

    @Query(FINISHED_WORKOUTS)
    fun observeFinished(): Flow<List<WorkoutEntity>>

    @Query(EXERCISES_OF_FINISHED)
    fun observeFinishedExercises(): Flow<List<WorkoutExerciseEntity>>

    @Query(SETS_OF_FINISHED)
    fun observeFinishedSets(): Flow<List<WorkoutSetEntity>>

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
