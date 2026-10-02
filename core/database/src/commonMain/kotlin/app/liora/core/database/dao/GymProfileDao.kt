package app.liora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.liora.core.database.model.GymProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GymProfileDao {
    /** The gyms in the order they were added. */
    @Query("SELECT * FROM gym_profile WHERE deleted_at IS NULL ORDER BY created_at, id")
    fun observeAll(): Flow<List<GymProfileEntity>>

    @Query("SELECT * FROM gym_profile WHERE deleted_at IS NULL ORDER BY created_at, id")
    suspend fun all(): List<GymProfileEntity>

    @Query("SELECT * FROM gym_profile WHERE id = :id")
    suspend fun get(id: String): GymProfileEntity?

    @Upsert
    suspend fun upsert(profile: GymProfileEntity)
}
