package app.liora.core.domain

import app.liora.core.model.LoggedSet
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.WorkoutExercise
import kotlin.math.abs

/** How a set did against its counterpart last time. */
enum class VersusLastTime { Ahead, Level, Behind }

/**
 * A workout's sets against last time's. [comparable] working sets have a counterpart last time, done yet or
 * not; [sets] says how each one done so far did, by set id; [volumeKg] and [reps] are what those add up to
 * against their counterparts.
 */
data class AgainstLastTime(
    val sets: Map<String, VersusLastTime> = emptyMap(),
    val comparable: Int = 0,
    /** Load moved more (or less) than the same sets last time, in kg; null when none of them moves load. */
    val volumeKg: Double? = null,
    /** Reps more (or fewer) than the same sets last time, among those counted in reps. */
    val reps: Int = 0,
) {
    val ahead: Int get() = sets.values.count { it == VersusLastTime.Ahead }

    /** How many of the comparable sets are done. */
    val compared: Int get() = sets.size
}

/**
 * Today against last time, set by set, as the Previous column pairs them ([SetPlaceholders.previousFor]): a
 * running score while logging, and the verdict on the summary. Only sets done count, so a workout isn't
 * behind for being unfinished. Warm-ups don't count.
 */
object LastTimeComparison {
    /** [exercises] against [previous], last session's completed sets by exercise id. */
    fun of(
        exercises: List<WorkoutExercise>,
        previous: Map<String, List<LoggedSet>>,
        trackingTypeOf: (exerciseId: String) -> TrackingType,
        formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    ): AgainstLastTime {
        val verdicts = mutableMapOf<String, VersusLastTime>()
        var comparable = 0
        var volume: Double? = null
        var reps = 0
        for (exercise in exercises) {
            val trackingType = trackingTypeOf(exercise.exerciseId)
            val last = previous[exercise.exerciseId].orEmpty()
            exercise.sets.forEachIndexed { index, set ->
                if (set.type == SetType.Warmup) return@forEachIndexed
                val counterpart = SetPlaceholders.previousFor(exercise.sets, index, last) ?: return@forEachIndexed
                comparable++
                if (!set.isCompleted) return@forEachIndexed
                verdicts[set.id] = compare(trackingType, set, counterpart, formula)
                if (movesLoad(trackingType)) {
                    volume = (volume ?: 0.0) + set.volumeKg() - counterpart.volumeKg()
                }
                if (trackingType.usesReps) reps += (set.reps ?: 0) - (counterpart.reps ?: 0)
            }
        }
        return AgainstLastTime(verdicts, comparable, volume, reps)
    }

    /**
     * How [set] did against [last], its counterpart. Ahead when at least as heavy and at least as long (more
     * reps, time or distance), and more of one; behind the other way round. When one went up and the other
     * down, weight and reps go by the estimated one-rep max, other loads by load times length. Assistance is
     * lighter the less of it there is, and a run of the same distance is ahead for being quicker.
     */
    fun compare(
        trackingType: TrackingType,
        set: LoggedSet,
        last: LoggedSet,
        formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    ): VersusLastTime {
        val load = loadOf(trackingType, set) - loadOf(trackingType, last)
        val length = lengthOf(trackingType, set) - lengthOf(trackingType, last)
        return when {
            load.isZero() && length.isZero() -> paceOf(trackingType, set, last)
            load >= -EPSILON && length >= -EPSILON -> VersusLastTime.Ahead
            load <= EPSILON && length <= EPSILON -> VersusLastTime.Behind
            else -> mixed(trackingType, set, last, formula)
        }
    }

    /** One went up and the other down: the estimated one-rep max where there is one, else load times length. */
    private fun mixed(
        trackingType: TrackingType,
        set: LoggedSet,
        last: LoggedSet,
        formula: OneRepMaxFormula,
    ): VersusLastTime {
        // Less assistance for fewer reps, or the reverse: without bodyweight, there's no telling.
        if (trackingType == TrackingType.AssistedBodyweight) return VersusLastTime.Level
        val today = strengthOf(trackingType, set, formula)
        val before = strengthOf(trackingType, last, formula)
        return verdictOf(today - before)
    }

    private fun strengthOf(
        trackingType: TrackingType,
        set: LoggedSet,
        formula: OneRepMaxFormula,
    ): Double {
        val weight = set.weight
        val reps = set.reps
        if (trackingType == TrackingType.WeightReps && weight != null && reps != null) {
            estimateOneRepMax(weight, reps, formula)?.let { return it.kilograms }
        }
        return loadOf(trackingType, set) * lengthOf(trackingType, set)
    }

    /** The same load and length: a run of the same distance is ahead for being quicker. */
    private fun paceOf(
        trackingType: TrackingType,
        set: LoggedSet,
        last: LoggedSet,
    ): VersusLastTime {
        val today = set.duration
        val before = last.duration
        return if (trackingType != TrackingType.DistanceDuration || today == null || before == null) {
            VersusLastTime.Level
        } else {
            verdictOf((before - today).inWholeMilliseconds / MILLIS_PER_SECOND)
        }
    }
}

private fun verdictOf(difference: Double): VersusLastTime =
    when {
        difference > EPSILON -> VersusLastTime.Ahead
        difference < -EPSILON -> VersusLastTime.Behind
        else -> VersusLastTime.Level
    }

/** The load: the weight, none for bodyweight alone, and assistance counted against. */
private fun loadOf(
    trackingType: TrackingType,
    set: LoggedSet,
): Double {
    val kilograms = set.weight?.kilograms ?: 0.0
    return when {
        trackingType == TrackingType.AssistedBodyweight -> -kilograms
        trackingType.usesWeight -> kilograms
        else -> 0.0
    }
}

/** How long it went on: reps, else distance, else time. */
private fun lengthOf(
    trackingType: TrackingType,
    set: LoggedSet,
): Double =
    when {
        trackingType.usesReps -> (set.reps ?: 0).toDouble()
        trackingType.usesDistance -> set.distanceMeters ?: 0.0
        else -> (set.duration?.inWholeMilliseconds ?: 0L) / MILLIS_PER_SECOND
    }

/** Whether the exercise's sets add to the volume the logger counts ([volumeOf]). */
private fun movesLoad(trackingType: TrackingType): Boolean =
    trackingType == TrackingType.WeightReps || trackingType == TrackingType.WeightedBodyweight

private fun LoggedSet.volumeKg(): Double = (weight?.kilograms ?: 0.0) * (reps ?: 0)

private fun Double.isZero(): Boolean = abs(this) <= EPSILON

/** Weights round-trip through the gym's unit, which can leave crumbs. */
private const val EPSILON = 0.001
private const val MILLIS_PER_SECOND = 1000.0
