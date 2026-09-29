package app.liora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.liora.core.database.model.WorkoutEntity
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

    @Upsert
    suspend fun upsert(workout: WorkoutEntity)
}
