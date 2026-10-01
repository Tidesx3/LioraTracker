package app.liora.core.domain

import app.liora.core.model.FinishedWorkout
import app.liora.core.model.LoggedSet
import app.liora.core.model.Muscle
import app.liora.core.model.TrackingType

/**
 * Total load moved (kg) in working sets. Counts exercises where the weight is the load being moved,
 * so assisted and time/distance exercises are left out; warm-ups never count.
 */
fun volumeOf(
    trackingType: TrackingType,
    sets: List<LoggedSet>,
): Double {
    if (trackingType != TrackingType.WeightReps && trackingType != TrackingType.WeightedBodyweight) return 0.0
    return sets
        .filter { it.isWorkingSet }
        .sumOf { set -> (set.weight?.kilograms ?: 0.0) * (set.reps ?: 0) }
}

/** An exercise's working sets, with the muscles it trains. */
data class MuscleWork(
    val primaryMuscles: Set<Muscle>,
    val secondaryMuscles: Set<Muscle>,
    val workingSets: Int,
)

/**
 * Hard sets per muscle, the usual hypertrophy volume measure: a set counts fully for each primary
 * muscle and half for each secondary one.
 */
fun setsPerMuscle(work: List<MuscleWork>): Map<Muscle, Double> {
    val totals = mutableMapOf<Muscle, Double>()
    for (item in work) {
        if (item.workingSets <= 0) continue
        item.primaryMuscles.forEach { totals[it] = (totals[it] ?: 0.0) + item.workingSets }
        (item.secondaryMuscles - item.primaryMuscles).forEach {
            totals[it] = (totals[it] ?: 0.0) + item.workingSets * SECONDARY_MUSCLE_WEIGHT
        }
    }
    return totals
}

/** Which muscles an exercise trains. */
data class MuscleTargets(
    val primary: Set<Muscle>,
    val secondary: Set<Muscle>,
)

/** Hard sets per muscle over [workouts] (see [setsPerMuscle]); exercises [targetsOf] doesn't know don't count. */
fun setsPerMuscle(
    workouts: List<FinishedWorkout>,
    targetsOf: (exerciseId: String) -> MuscleTargets?,
): Map<Muscle, Double> =
    setsPerMuscle(
        workouts.flatMap { it.exercises }.mapNotNull { exercise ->
            targetsOf(exercise.exerciseId)?.let { targets ->
                MuscleWork(targets.primary, targets.secondary, exercise.sets.count { it.isWorkingSet })
            }
        },
    )

private const val SECONDARY_MUSCLE_WEIGHT = 0.5
