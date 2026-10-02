package app.liora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.liora.core.database.model.MeasurementEntity
import kotlinx.coroutines.flow.Flow

/** A few measurements a week at most, so screens observe the whole table and group it in memory. */
@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurement WHERE deleted_at IS NULL ORDER BY taken_at")
    fun observeAll(): Flow<List<MeasurementEntity>>

    /** One type's live rows taken in `[from, until)`, oldest first. */
    @Query(
        """
        SELECT * FROM measurement
        WHERE type = :type AND taken_at >= :from AND taken_at < :until AND deleted_at IS NULL
        ORDER BY taken_at
        """,
    )
    suspend fun between(
        type: String,
        from: Long,
        until: Long,
    ): List<MeasurementEntity>

    @Upsert
    suspend fun upsertAll(measurements: List<MeasurementEntity>)
}
