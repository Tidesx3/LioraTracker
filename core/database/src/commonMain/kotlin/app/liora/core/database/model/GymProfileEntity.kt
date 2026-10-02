package app.liora.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A gym's equipment. Weights are in kilograms; [unit] (`kg` or `lb`) is what the gym's plates and
 * dumbbells are labelled in. [plates] and [dumbbells] are JSON arrays, e.g.
 * `[{"kg":25.0,"pairs":4}]` and `[{"fromKg":2.0,"toKg":40.0,"stepKg":2.0}]`. Which gym is in use is
 * kept per device in `local_meta`.
 */
@Entity(tableName = "gym_profile")
data class GymProfileEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val unit: String,
    @ColumnInfo(name = "barbell_kg") val barbellKg: Double,
    @ColumnInfo(name = "ez_bar_kg") val ezBarKg: Double,
    val plates: String,
    val dumbbells: String,
    @ColumnInfo(name = "stack_step_kg") val stackStepKg: Double,
    @Embedded override val sync: SyncMetadata,
) : SyncedRow
