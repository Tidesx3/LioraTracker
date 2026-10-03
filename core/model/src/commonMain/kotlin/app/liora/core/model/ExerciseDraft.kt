package app.liora.core.model

/** What the user fills in when creating or editing a custom exercise. */
data class ExerciseDraft(
    val name: String = "",
    val trackingType: TrackingType = TrackingType.WeightReps,
    val equipment: Equipment = Equipment.Other,
    val primaryMuscles: Set<Muscle> = emptySet(),
    val secondaryMuscles: Set<Muscle> = emptySet(),
    /** The exercise's own note, as in [ExerciseSettings.stickyNote]: shown wherever the exercise appears. */
    val notes: String = "",
    /** The built-in exercise this one is a variation of, if any. */
    val variationOf: String? = null,
) {
    val isValid: Boolean get() = name.isNotBlank()

    companion object {
        /** Starts a variation of [base]: same movement pattern, the user adjusts name and details. */
        fun variationOf(
            base: Exercise,
            name: String,
        ) = ExerciseDraft(
            name = name,
            trackingType = base.trackingType,
            equipment = base.equipment,
            primaryMuscles = base.primaryMuscles,
            secondaryMuscles = base.secondaryMuscles,
            variationOf = base.variationOf ?: base.id.takeUnless { base.isCustom },
        )

        fun from(exercise: Exercise) =
            ExerciseDraft(
                name = exercise.name,
                trackingType = exercise.trackingType,
                equipment = exercise.equipment,
                primaryMuscles = exercise.primaryMuscles,
                secondaryMuscles = exercise.secondaryMuscles,
                // Notes from before they moved to the settings are picked up, and move there on saving.
                notes = exercise.settings.stickyNote ?: exercise.notes.orEmpty(),
                variationOf = exercise.variationOf,
            )
    }
}
