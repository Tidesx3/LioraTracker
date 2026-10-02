package app.liora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.liora.core.database.model.PreferenceEntity
import kotlinx.coroutines.flow.Flow

/** Settings, one row per choice, so two devices changing different ones never overwrite each other. */
@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preference WHERE deleted_at IS NULL")
    fun observeAll(): Flow<List<PreferenceEntity>>

    /** Every row, tombstones included, so a choice made again revives its row. */
    @Query("SELECT * FROM preference")
    suspend fun all(): List<PreferenceEntity>

    @Upsert
    suspend fun upsertAll(preferences: List<PreferenceEntity>)
}
