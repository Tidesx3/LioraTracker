package app.liora.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseCategory
import app.liora.core.model.Muscle
import app.liora.core.model.TrackingType

/**
 * Built-in exercises come from the bundled seed (stable ids like `fedb.Barbell_Squat`, never synced;
 * every device seeds the same catalog). Custom exercises have UUIDv7 ids and sync like user data.
 */
@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    /** English name for built-ins, the user's text for custom exercises. */
    val name: String,
    @ColumnInfo(name = "tracking_type") val trackingType: TrackingType,
    val equipment: Equipment,
    val category: ExerciseCategory,
    @ColumnInfo(name = "primary_muscles") val primaryMuscles: Set<Muscle>,
    @ColumnInfo(name = "secondary_muscles") val secondaryMuscles: Set<Muscle>,
    val instructions: List<String>,
    @ColumnInfo(name = "image_paths") val imagePaths: List<String>,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean,
    @ColumnInfo(name = "variation_of") val variationOf: String?,
    val notes: String?,
    val rank: Int?,
    @Embedded val sync: SyncMetadata,
)

/**
 * Localized names and search aliases. Not synced: rebuilt from the seed for built-ins and from the
 * exercise's own name for custom exercises (locale [LOCALE_ANY]).
 */
@Entity(
    tableName = "exercise_name",
    primaryKeys = ["exercise_id", "locale", "name"],
    indices = [Index("exercise_id")],
)
data class ExerciseNameEntity(
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val locale: String,
    val name: String,
    @ColumnInfo(name = "is_alias") val isAlias: Boolean,
) {
    companion object {
        const val LOCALE_ANY = "und"
    }
}

/** The user's per-exercise overrides, for built-in and custom exercises alike. Synced. */
@Entity(tableName = "exercise_settings")
data class ExerciseSettingsEntity(
    @PrimaryKey @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "sticky_note") val stickyNote: String?,
    @ColumnInfo(name = "rest_working_sec") val restWorkingSeconds: Int?,
    @ColumnInfo(name = "rest_warmup_sec") val restWarmupSeconds: Int?,
    val archived: Boolean,
    @Embedded val sync: SyncMetadata,
)
