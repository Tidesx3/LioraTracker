package app.liora.core.domain

import app.liora.core.model.ActiveWorkout
import app.liora.core.model.ExerciseSettings
import app.liora.core.model.LoggedSet
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.WorkoutExercise
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A set's place in a workout: the exercise's index and the set's index within it. */
data class SetRef(
    val exerciseIndex: Int,
    val setIndex: Int,
)

fun ActiveWorkout.setAt(ref: SetRef): LoggedSet = exercises[ref.exerciseIndex].sets[ref.setIndex]

/** Where the set with [setId] sits, or null if it isn't in this workout. */
fun ActiveWorkout.find(setId: String): SetRef? {
    exercises.forEachIndexed { exerciseIndex, exercise ->
        val setIndex = exercise.sets.indexOfFirst { it.id == setId }
        if (setIndex >= 0) return SetRef(exerciseIndex, setIndex)
    }
    return null
}

/**
 * The order a lifter works through a workout: exercise by exercise, except that a superset rotates
 * through its exercises set by set (A1, B1, A2, B2, …). It decides which set comes next and when rest
 * starts, for the logger and the workout notification alike.
 */
object WorkoutOrder {
    /** Every set of [workout], in the order it gets done. */
    fun of(workout: ActiveWorkout): List<SetRef> {
        val exercises = workout.exercises
        val order = mutableListOf<SetRef>()
        var start = 0
        while (start < exercises.size) {
            val end = blockEnd(exercises, start)
            order += rounds(exercises, start..end)
            start = end + 1
        }
        return order
    }

    /** The sets of one block round by round: a lone exercise's sets in order, or a superset's in rotation. */
    private fun rounds(
        exercises: List<WorkoutExercise>,
        block: IntRange,
    ): List<SetRef> {
        val rounds = block.maxOf { exercises[it].sets.size }
        return (0 until rounds).flatMap { round ->
            block.filter { round < exercises[it].sets.size }.map { SetRef(it, round) }
        }
    }

    /**
     * The set to do next: the first open set after the one ticked off most recently, so skipping ahead
     * carries on from there. Falls back to the first open set, and is null once everything is done.
     */
    fun current(workout: ActiveWorkout): SetRef? {
        val order = of(workout)
        val lastDone =
            order.indices
                .filter { workout.setAt(order[it]).isCompleted }
                .maxByOrNull { workout.setAt(order[it]).completedAt!! }
        val isOpen = { ref: SetRef -> !workout.setAt(ref).isCompleted }
        return lastDone?.let { done -> order.drop(done + 1).firstOrNull(isOpen) } ?: order.firstOrNull(isOpen)
    }

    /**
     * Whether rest follows [ref]. Inside a superset the exercises of a round follow each other without
     * a break; rest comes after the round's last exercise.
     */
    fun restsAfter(
        workout: ActiveWorkout,
        ref: SetRef,
    ): Boolean {
        val order = of(workout)
        val next = order.getOrNull(order.indexOf(ref) + 1) ?: return true
        val group = workout.exercises[ref.exerciseIndex].supersetGroup
        val sameRound =
            group != null &&
                next.setIndex == ref.setIndex &&
                next.exerciseIndex > ref.exerciseIndex &&
                workout.exercises[next.exerciseIndex].supersetGroup == group
        return !sameRound
    }

    /** Last index of the block starting at [start]: the superset it opens, or just itself. */
    private fun blockEnd(
        exercises: List<WorkoutExercise>,
        start: Int,
    ): Int {
        val group = exercises[start].supersetGroup ?: return start
        var end = start
        while (end + 1 < exercises.size && exercises[end + 1].supersetGroup == group) end++
        return end
    }
}

/** Rest when neither the workout nor the exercise sets its own; adjustable in Settings. */
data class RestDefaults(
    val working: Duration = 2.minutes,
    val warmup: Duration = 1.minutes,
)

/**
 * How long to rest after [set]: warm-ups use the exercise's warm-up rest; working sets the workout's
 * own rest for the exercise (from the routine), then the exercise's, then the default. A rest of 0
 * anywhere turns the timer off. Null means no timer.
 */
fun restAfter(
    set: LoggedSet,
    exercise: WorkoutExercise,
    settings: ExerciseSettings,
    defaults: RestDefaults = RestDefaults(),
): Duration? {
    if (exercise.restSeconds == 0) return null
    val rest =
        if (set.type == SetType.Warmup) {
            settings.restWarmupSeconds?.seconds ?: defaults.warmup
        } else {
            (exercise.restSeconds ?: settings.restWorkingSeconds)?.seconds ?: defaults.working
        }
    return rest.takeIf { it > Duration.ZERO }
}

/**
 * Placeholders: what an empty field shows greyed out, and what gets logged when a set is ticked off
 * without typing. They come from last session, so repeating it is one tap per set; a routine's rep
 * target fills in when there is no last session.
 */
object SetPlaceholders {
    /**
     * Last session's counterpart of the set at [index]: the same warm-up or working set by count, so
     * adding a warm-up doesn't shift every working set's previous values.
     */
    fun previousFor(
        sets: List<LoggedSet>,
        index: Int,
        previous: List<LoggedSet>,
    ): LoggedSet? {
        val set = sets.getOrNull(index) ?: return null
        val isWarmup = set.type == SetType.Warmup
        val ordinal = sets.take(index).count { (it.type == SetType.Warmup) == isWarmup }
        return previous.filter { (it.type == SetType.Warmup) == isWarmup }.getOrNull(ordinal)
    }

    /** [set] with every empty field taken from its placeholder. */
    fun fill(
        set: LoggedSet,
        previous: LoggedSet?,
    ): LoggedSet =
        set.copy(
            weight = set.weight ?: previous?.weight,
            reps = set.reps ?: previous?.reps ?: set.targetReps?.min,
            duration = set.duration ?: previous?.duration,
            distanceMeters = set.distanceMeters ?: previous?.distanceMeters,
        )

    /**
     * The fields [trackingType] needs before a set counts. Weight is optional where it is extra load or
     * assistance on top of bodyweight; distance and time can each stand alone for cardio.
     */
    fun missingFields(
        trackingType: TrackingType,
        set: LoggedSet,
    ): Set<SetField> {
        if (trackingType == TrackingType.DistanceDuration) {
            return if (set.distanceMeters == null && set.duration == null) setOf(SetField.Distance) else emptySet()
        }
        val values =
            mapOf(
                SetField.Weight to set.weight,
                SetField.Reps to set.reps?.takeIf { it > 0 },
                SetField.Distance to set.distanceMeters,
                SetField.Duration to set.duration,
            )
        val optional = if (trackingType in LOAD_OPTIONAL) setOf(SetField.Weight) else emptySet()
        return SetField.of(trackingType).filter { it !in optional && values[it] == null }.toSet()
    }

    /** Exercises whose weight is extra load or assistance on top of bodyweight; zero is a real value. */
    private val LOAD_OPTIONAL = setOf(TrackingType.WeightedBodyweight, TrackingType.AssistedBodyweight)
}

/** The values a set can have, in the order they appear in a set row. */
enum class SetField {
    Weight,
    Reps,
    Distance,
    Duration,
    ;

    companion object {
        /** The fields [trackingType] shows, left to right. */
        fun of(trackingType: TrackingType): List<SetField> =
            buildList {
                if (trackingType.usesWeight) add(Weight)
                if (trackingType.usesReps) add(Reps)
                if (trackingType.usesDistance) add(Distance)
                if (trackingType.usesDuration) add(Duration)
            }
    }
}
