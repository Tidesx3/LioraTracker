package app.liora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.liora.core.database.model.ProgressPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressPhotoDao {
    @Query("SELECT * FROM progress_photo WHERE deleted_at IS NULL ORDER BY taken_at")
    fun observeAll(): Flow<List<ProgressPhotoEntity>>

    @Query("SELECT * FROM progress_photo WHERE id = :id")
    suspend fun get(id: String): ProgressPhotoEntity?

    @Upsert
    suspend fun upsert(photo: ProgressPhotoEntity)
}
