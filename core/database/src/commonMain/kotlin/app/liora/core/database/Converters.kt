package app.liora.core.database

import androidx.room.TypeConverter
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseCategory
import app.liora.core.model.Muscle
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType

/**
 * Enums are stored by their stable `key`, never by ordinal or name. Unknown keys (data synced from a
 * newer app version) fall back to a safe default instead of crashing.
 */
@Suppress("TooManyFunctions") // one pair per stored type
internal class Converters {
    @TypeConverter
    fun trackingTypeToKey(value: TrackingType): String = value.key

    @TypeConverter
    fun keyToTrackingType(key: String): TrackingType = TrackingType.fromKey(key) ?: TrackingType.WeightReps

    @TypeConverter
    fun equipmentToKey(value: Equipment): String = value.key

    @TypeConverter
    fun keyToEquipment(key: String): Equipment = Equipment.fromKey(key) ?: Equipment.Other

    @TypeConverter
    fun categoryToKey(value: ExerciseCategory): String = value.key

    @TypeConverter
    fun keyToCategory(key: String): ExerciseCategory = ExerciseCategory.fromKey(key) ?: ExerciseCategory.Strength

    @TypeConverter
    fun setTypeToKey(value: SetType): String = value.key

    @TypeConverter
    fun keyToSetType(key: String): SetType = SetType.fromKey(key) ?: SetType.Normal

    @TypeConverter
    fun musclesToKeys(value: Set<Muscle>): String = value.joinToString(LIST_SEPARATOR) { it.key }

    @TypeConverter
    fun keysToMuscles(keys: String): Set<Muscle> = splitList(keys).mapNotNull(Muscle::fromKey).toSet()

    @TypeConverter
    fun stringsToText(value: List<String>): String = value.joinToString(LIST_SEPARATOR)

    @TypeConverter
    fun textToStrings(text: String): List<String> = splitList(text)

    private fun splitList(text: String): List<String> = if (text.isEmpty()) emptyList() else text.split(LIST_SEPARATOR)

    private companion object {
        /** ASCII unit separator: never appears in names or instructions. */
        const val LIST_SEPARATOR = "\u001F"
    }
}
