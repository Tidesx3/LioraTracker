package app.liora.core.ui

import app.liora.core.model.Equipment
import app.liora.core.model.Muscle
import app.liora.core.model.TrackingType
import app.liora.core.ui.resources.Res
import app.liora.core.ui.resources.badge_custom
import app.liora.core.ui.resources.equipment_band
import app.liora.core.ui.resources.equipment_barbell
import app.liora.core.ui.resources.equipment_bodyweight
import app.liora.core.ui.resources.equipment_cable
import app.liora.core.ui.resources.equipment_dumbbell
import app.liora.core.ui.resources.equipment_exercise_ball
import app.liora.core.ui.resources.equipment_ez_bar
import app.liora.core.ui.resources.equipment_foam_roll
import app.liora.core.ui.resources.equipment_kettlebell
import app.liora.core.ui.resources.equipment_machine
import app.liora.core.ui.resources.equipment_medicine_ball
import app.liora.core.ui.resources.equipment_other
import app.liora.core.ui.resources.muscle_abdominals
import app.liora.core.ui.resources.muscle_abductors
import app.liora.core.ui.resources.muscle_adductors
import app.liora.core.ui.resources.muscle_biceps
import app.liora.core.ui.resources.muscle_calves
import app.liora.core.ui.resources.muscle_chest
import app.liora.core.ui.resources.muscle_forearms
import app.liora.core.ui.resources.muscle_glutes
import app.liora.core.ui.resources.muscle_hamstrings
import app.liora.core.ui.resources.muscle_lats
import app.liora.core.ui.resources.muscle_lower_back
import app.liora.core.ui.resources.muscle_middle_back
import app.liora.core.ui.resources.muscle_neck
import app.liora.core.ui.resources.muscle_quadriceps
import app.liora.core.ui.resources.muscle_shoulders
import app.liora.core.ui.resources.muscle_traps
import app.liora.core.ui.resources.muscle_triceps
import app.liora.core.ui.resources.tracking_assisted_bodyweight
import app.liora.core.ui.resources.tracking_assisted_bodyweight_example
import app.liora.core.ui.resources.tracking_bodyweight_reps
import app.liora.core.ui.resources.tracking_bodyweight_reps_example
import app.liora.core.ui.resources.tracking_distance_duration
import app.liora.core.ui.resources.tracking_distance_duration_example
import app.liora.core.ui.resources.tracking_duration
import app.liora.core.ui.resources.tracking_duration_example
import app.liora.core.ui.resources.tracking_duration_weight
import app.liora.core.ui.resources.tracking_duration_weight_example
import app.liora.core.ui.resources.tracking_weight_distance
import app.liora.core.ui.resources.tracking_weight_distance_example
import app.liora.core.ui.resources.tracking_weight_reps
import app.liora.core.ui.resources.tracking_weight_reps_example
import app.liora.core.ui.resources.tracking_weighted_bodyweight
import app.liora.core.ui.resources.tracking_weighted_bodyweight_example
import org.jetbrains.compose.resources.StringResource

// Localized labels for domain enums. Exhaustive `when`s make a new enum value a compile error here
// instead of a missing label at runtime.

val Muscle.label: StringResource
    get() =
        when (this) {
            Muscle.Chest -> Res.string.muscle_chest
            Muscle.Shoulders -> Res.string.muscle_shoulders
            Muscle.Triceps -> Res.string.muscle_triceps
            Muscle.Biceps -> Res.string.muscle_biceps
            Muscle.Forearms -> Res.string.muscle_forearms
            Muscle.Lats -> Res.string.muscle_lats
            Muscle.MiddleBack -> Res.string.muscle_middle_back
            Muscle.LowerBack -> Res.string.muscle_lower_back
            Muscle.Traps -> Res.string.muscle_traps
            Muscle.Neck -> Res.string.muscle_neck
            Muscle.Abdominals -> Res.string.muscle_abdominals
            Muscle.Quadriceps -> Res.string.muscle_quadriceps
            Muscle.Hamstrings -> Res.string.muscle_hamstrings
            Muscle.Glutes -> Res.string.muscle_glutes
            Muscle.Adductors -> Res.string.muscle_adductors
            Muscle.Abductors -> Res.string.muscle_abductors
            Muscle.Calves -> Res.string.muscle_calves
        }

val Equipment.label: StringResource
    get() =
        when (this) {
            Equipment.Barbell -> Res.string.equipment_barbell
            Equipment.Dumbbell -> Res.string.equipment_dumbbell
            Equipment.Kettlebell -> Res.string.equipment_kettlebell
            Equipment.Machine -> Res.string.equipment_machine
            Equipment.Cable -> Res.string.equipment_cable
            Equipment.EzBar -> Res.string.equipment_ez_bar
            Equipment.Band -> Res.string.equipment_band
            Equipment.Bodyweight -> Res.string.equipment_bodyweight
            Equipment.MedicineBall -> Res.string.equipment_medicine_ball
            Equipment.ExerciseBall -> Res.string.equipment_exercise_ball
            Equipment.FoamRoll -> Res.string.equipment_foam_roll
            Equipment.Other -> Res.string.equipment_other
        }

val TrackingType.label: StringResource
    get() =
        when (this) {
            TrackingType.WeightReps -> Res.string.tracking_weight_reps
            TrackingType.BodyweightReps -> Res.string.tracking_bodyweight_reps
            TrackingType.WeightedBodyweight -> Res.string.tracking_weighted_bodyweight
            TrackingType.AssistedBodyweight -> Res.string.tracking_assisted_bodyweight
            TrackingType.Duration -> Res.string.tracking_duration
            TrackingType.DurationWeight -> Res.string.tracking_duration_weight
            TrackingType.DistanceDuration -> Res.string.tracking_distance_duration
            TrackingType.WeightDistance -> Res.string.tracking_weight_distance
        }

/** A few familiar examples, shown when choosing how a custom exercise is tracked. */
val TrackingType.examples: StringResource
    get() =
        when (this) {
            TrackingType.WeightReps -> Res.string.tracking_weight_reps_example
            TrackingType.BodyweightReps -> Res.string.tracking_bodyweight_reps_example
            TrackingType.WeightedBodyweight -> Res.string.tracking_weighted_bodyweight_example
            TrackingType.AssistedBodyweight -> Res.string.tracking_assisted_bodyweight_example
            TrackingType.Duration -> Res.string.tracking_duration_example
            TrackingType.DurationWeight -> Res.string.tracking_duration_weight_example
            TrackingType.DistanceDuration -> Res.string.tracking_distance_duration_example
            TrackingType.WeightDistance -> Res.string.tracking_weight_distance_example
        }

/** Label for the badge on user-created exercises. */
val customExerciseBadge: StringResource get() = Res.string.badge_custom
