package app.liora.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.liora.core.model.SetType

/** A logged session. `endedAt == null` means it is the workout currently in progress. */
@Entity(tableName = "workout", indices = [Index("started_at"), Index("ended_at")])
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String?,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "routine_id") val routineId: String?,
    val notes: String?,
    /** Bodyweight at the time, so bodyweight exercises can count toward volume. */
    @ColumnInfo(name = "bodyweight_kg") val bodyweightKg: Double?,
    @Embedded val sync: SyncMetadata,
)

@Entity(tableName = "workout_exercise", indices = [Index("workout_id"), Index("exercise_id")])
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val position: Int,
    @ColumnInfo(name = "superset_group") val supersetGroup: Int?,
    @ColumnInfo(name = "rest_sec") val restSeconds: Int?,
    val notes: String?,
    @Embedded val sync: SyncMetadata,
)

@Entity(tableName = "workout_set", indices = [Index("workout_exercise_id")])
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_exercise_id") val workoutExerciseId: String,
    val position: Int,
    @ColumnInfo(name = "set_type") val setType: SetType,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    val reps: Int?,
    @ColumnInfo(name = "duration_sec") val durationSeconds: Int?,
    @ColumnInfo(name = "distance_m") val distanceMeters: Double?,
    val rpe: Double?,
    /** When the set was ticked off; null while it is still planned. */
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @Embedded val sync: SyncMetadata,
)
