package app.liora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * What's waiting to sync, and marking rows as pushed once a sync batch holding them is in the store. Each
 * only clears `dirty` while the row still has the [hlc] it was pushed with: a row changed during the push
 * stays dirty for the next.
 */
@Dao
@Suppress("TooManyFunctions") // one per synced table
interface SyncDao {
    /**
     * How many rows the next push would send: what changed since the last one, deletions included. Built-in
     * exercises never go, and neither does the workout in progress (or one discarded) until it's finished.
     */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM exercise WHERE dirty = 1 AND is_custom = 1) +
            (SELECT COUNT(*) FROM exercise_settings WHERE dirty = 1) +
            (SELECT COUNT(*) FROM routine_folder WHERE dirty = 1) +
            (SELECT COUNT(*) FROM routine WHERE dirty = 1) +
            (SELECT COUNT(*) FROM routine_exercise WHERE dirty = 1) +
            (SELECT COUNT(*) FROM routine_set WHERE dirty = 1) +
            (SELECT COUNT(*) FROM workout WHERE dirty = 1 AND ended_at IS NOT NULL) +
            (SELECT COUNT(*) FROM workout_exercise e JOIN workout w ON w.id = e.workout_id
                WHERE e.dirty = 1 AND w.ended_at IS NOT NULL) +
            (SELECT COUNT(*) FROM workout_set s JOIN workout_exercise e ON e.id = s.workout_exercise_id
                JOIN workout w ON w.id = e.workout_id WHERE s.dirty = 1 AND w.ended_at IS NOT NULL) +
            (SELECT COUNT(*) FROM measurement WHERE dirty = 1) +
            (SELECT COUNT(*) FROM progress_photo WHERE dirty = 1) +
            (SELECT COUNT(*) FROM gym_profile WHERE dirty = 1) +
            (SELECT COUNT(*) FROM preference WHERE dirty = 1)
        """,
    )
    fun observePending(): Flow<Int>

    @Query("UPDATE exercise SET dirty = 0 WHERE id = :id AND hlc = :hlc")
    suspend fun pushedExercise(
        id: String,
        hlc: String,
    )

    @Query("UPDATE exercise_settings SET dirty = 0 WHERE exercise_id = :exerciseId AND hlc = :hlc")
    suspend fun pushedExerciseSettings(
        exerciseId: String,
        hlc: String,
    )

    @Query("UPDATE routine_folder SET dirty = 0 WHERE id = :id AND hlc = :hlc")
    suspend fun pushedRoutineFolder(
        id: String,
        hlc: String,
    )

    @Query("UPDATE routine SET dirty = 0 WHERE id = :id AND hlc = :hlc")
    suspend fun pushedRoutine(
        id: String,
        hlc: String,
    )

    @Query("UPDATE routine_exercise SET dirty = 0 WHERE id = :id AND hlc = :hlc")
    suspend fun pushedRoutineExercise(
        id: String,
        hlc: String,
    )

    @Query("UPDATE routine_set SET dirty = 0 WHERE id = :id AND hlc = :hlc")
    suspend fun pushedRoutineSet(
        id: String,
        hlc: String,
    )

    @Query("UPDATE workout SET dirty = 0 WHERE id = :id AND hlc = :hlc")
    suspend fun pushedWorkout(
        id: String,
        hlc: String,
    )

    @Query("UPDATE workout_exercise SET dirty = 0 WHERE id = :id AND hlc = :hlc")
    suspend fun pushedWorkoutExercise(
        id: String,
        hlc: String,
    )

    @Query("UPDATE workout_set SET dirty = 0 WHERE id = :id AND hlc = :hlc")
    suspend fun pushedWorkoutSet(
        id: String,
        hlc: String,
    )

    @Query("UPDATE measurement SET dirty = 0 WHERE id = :id AND hlc = :hlc")
    suspend fun pushedMeasurement(
        id: String,
        hlc: String,
    )

    @Query("UPDATE progress_photo SET dirty = 0 WHERE id = :id AND hlc = :hlc")
    suspend fun pushedProgressPhoto(
        id: String,
        hlc: String,
    )

    @Query("UPDATE gym_profile SET dirty = 0 WHERE id = :id AND hlc = :hlc")
    suspend fun pushedGymProfile(
        id: String,
        hlc: String,
    )

    @Query("UPDATE preference SET dirty = 0 WHERE `key` = :key AND hlc = :hlc")
    suspend fun pushedPreference(
        key: String,
        hlc: String,
    )
}
