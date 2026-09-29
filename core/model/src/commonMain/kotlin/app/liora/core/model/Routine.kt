package app.liora.core.model

import kotlin.time.Duration

/** Groups routines on the Train tab, e.g. "Push Pull Legs". */
data class RoutineFolder(
    val id: String,
    val name: String,
)

/** A saved workout plan: exercises in order, each with its target sets. Starting it prefills a workout. */
data class Routine(
    val id: String,
    val name: String,
    val folderId: String? = null,
    val notes: String? = null,
    val exercises: List<RoutineExercise> = emptyList(),
) {
    val isValid: Boolean get() = name.isNotBlank()
}

data class RoutineExercise(
    val id: String,
    val exerciseId: String,
    /** Adjacent exercises that share a group form a superset; null means not in one. */
    val supersetGroup: Int? = null,
    /** Rest after a set of this exercise; null uses the exercise's own default. */
    val restSeconds: Int? = null,
    val notes: String? = null,
    val sets: List<RoutineSet> = emptyList(),
)

/** One planned set. Which targets apply depends on the exercise's [TrackingType]. */
data class RoutineSet(
    val id: String,
    val type: SetType = SetType.Normal,
    val weight: Mass? = null,
    val reps: RepRange? = null,
    val duration: Duration? = null,
    val distanceMeters: Double? = null,
    val rpe: Double? = null,
)

/** Target reps: exactly [min] when [max] equals it, otherwise a range such as 8–12 for double progression. */
data class RepRange(
    val min: Int,
    val max: Int = min,
) {
    init {
        require(min in 1..max) { "Invalid rep range $min–$max" }
    }

    val isRange: Boolean get() = max > min

    companion object {
        /** From two optional inputs; a lone or reversed value still makes a sensible target. */
        fun of(
            min: Int?,
            max: Int?,
        ): RepRange? {
            val values = listOfNotNull(min, max).filter { it > 0 }
            return if (values.isEmpty()) null else RepRange(values.min(), values.max())
        }
    }
}
