package app.liora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.liora.core.database.model.LocalMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMetaDao {
    @Query("SELECT value FROM local_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM local_meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun put(entry: LocalMetaEntity)

    @Query("DELETE FROM local_meta WHERE `key` = :key")
    suspend fun delete(key: String)
}
