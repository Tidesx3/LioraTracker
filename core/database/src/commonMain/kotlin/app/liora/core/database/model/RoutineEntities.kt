package app.liora.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.liora.core.model.SetType

// No foreign-key constraints on synced tables: during a sync, children can arrive before their
// parents. Repositories keep references consistent and cascade tombstones.

@Entity(tableName = "routine_folder")
data class RoutineFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int,
    @Embedded val sync: SyncMetadata,
)

@Entity(tableName = "routine", indices = [Index("folder_id")])
data class RoutineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "folder_id") val folderId: String?,
    val name: String,
    val notes: String?,
    val position: Int,
    @Embedded val sync: SyncMetadata,
)

@Entity(tableName = "routine_exercise", indices = [Index("routine_id"), Index("exercise_id")])
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "routine_id") val routineId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val position: Int,
    @ColumnInfo(name = "superset_group") val supersetGroup: Int?,
    @ColumnInfo(name = "rest_sec") val restSeconds: Int?,
    val notes: String?,
    @Embedded val sync: SyncMetadata,
)

@Entity(tableName = "routine_set", indices = [Index("routine_exercise_id")])
data class RoutineSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "routine_exercise_id") val routineExerciseId: String,
    val position: Int,
    @ColumnInfo(name = "set_type") val setType: SetType,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Double?,
    @ColumnInfo(name = "target_reps_min") val targetRepsMin: Int?,
    @ColumnInfo(name = "target_reps_max") val targetRepsMax: Int?,
    @ColumnInfo(name = "target_duration_sec") val targetDurationSeconds: Int?,
    @ColumnInfo(name = "target_distance_m") val targetDistanceMeters: Double?,
    @ColumnInfo(name = "target_rpe") val targetRpe: Double?,
    @Embedded val sync: SyncMetadata,
)
