package app.liora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.liora.core.database.model.RoutineEntity
import app.liora.core.database.model.RoutineExerciseEntity
import app.liora.core.database.model.RoutineFolderEntity
import app.liora.core.database.model.RoutineSetEntity
import kotlinx.coroutines.flow.Flow

/** Routines are few (dozens, not thousands), so screens observe whole tables and filter in memory. */
@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine_folder WHERE deleted_at IS NULL ORDER BY position")
    fun observeFolders(): Flow<List<RoutineFolderEntity>>

    @Query("SELECT * FROM routine WHERE deleted_at IS NULL ORDER BY position")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_exercise WHERE deleted_at IS NULL ORDER BY routine_id, position")
    fun observeExercises(): Flow<List<RoutineExerciseEntity>>

    @Query("SELECT * FROM routine_set WHERE deleted_at IS NULL ORDER BY routine_exercise_id, position")
    fun observeSets(): Flow<List<RoutineSetEntity>>

    @Query("SELECT * FROM routine_folder WHERE id = :id")
    suspend fun getFolder(id: String): RoutineFolderEntity?

    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun getRoutine(id: String): RoutineEntity?

    @Query("SELECT * FROM routine WHERE folder_id = :folderId AND deleted_at IS NULL")
    suspend fun routinesInFolder(folderId: String): List<RoutineEntity>

    @Query("SELECT * FROM routine_exercise WHERE routine_id = :routineId AND deleted_at IS NULL ORDER BY position")
    suspend fun exercisesOf(routineId: String): List<RoutineExerciseEntity>

    @Query(
        """
        SELECT s.* FROM routine_set s JOIN routine_exercise re ON re.id = s.routine_exercise_id
        WHERE re.routine_id = :routineId AND s.deleted_at IS NULL AND re.deleted_at IS NULL
        ORDER BY s.position
        """,
    )
    suspend fun setsOf(routineId: String): List<RoutineSetEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM routine WHERE deleted_at IS NULL")
    suspend fun maxRoutinePosition(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM routine_folder WHERE deleted_at IS NULL")
    suspend fun maxFolderPosition(): Int

    @Upsert
    suspend fun upsertFolder(folder: RoutineFolderEntity)

    @Upsert
    suspend fun upsertRoutine(routine: RoutineEntity)

    @Upsert
    suspend fun upsertExercises(exercises: List<RoutineExerciseEntity>)

    @Upsert
    suspend fun upsertSets(sets: List<RoutineSetEntity>)
}
