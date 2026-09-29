package app.liora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.liora.core.database.model.LocalMetaEntity

@Dao
interface LocalMetaDao {
    @Query("SELECT value FROM local_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entry: LocalMetaEntity)
}
