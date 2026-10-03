package app.liora.core.domain

import app.liora.core.model.ActiveWorkout
import app.liora.core.model.LoggedSet
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.core.model.WorkoutExercise

/**
 * "Update the routine with today's values": the routine rebuilt from what was actually done in a
 * workout started from it, so next time starts where this one ended.
 */
object RoutineUpdate {
    /**
     * [routine] with the workout's exercises, in its order, and their completed sets: today's weights,
     * times and distances. A rep range stays the goal; an exact rep target becomes what was done.
     *
     * What was planned but not done stays as planned, so a set or an exercise skipped on a tired day
     * doesn't shrink the plan; one deleted from the workout leaves it. Sets and exercises added today
     * join only once logged. The routine's own rows are reused where the exercise is the same, so only
     * what really changed gets synced.
     */
    fun fromWorkout(
        routine: Routine,
        workout: ActiveWorkout,
        newId: () -> String,
    ): Routine = fromExercises(routine, workout.exercises, newId)

    /**
     * [routine] rebuilt from [workoutExercises], the exercises of any workout: see [fromWorkout]. With an empty
     * routine this turns a finished workout into a new routine, or into the plan for repeating it.
     */
    fun fromExercises(
        routine: Routine,
        workoutExercises: List<WorkoutExercise>,
        newId: () -> String,
    ): Routine {
        val unmatched = routine.exercises.toMutableList()
        val exercises =
            workoutExercises.mapNotNull { done ->
                val match = unmatched.firstOrNull { it.exerciseId == done.exerciseId }?.also { unmatched.remove(it) }
                val sets = setsFor(done.sets, match?.sets.orEmpty(), newId)
                if (sets.isEmpty()) return@mapNotNull null
                RoutineExercise(
                    id = match?.id ?: newId(),
                    exerciseId = done.exerciseId,
                    supersetGroup = done.supersetGroup,
                    restSeconds = done.restSeconds,
                    // Today's notes stay with the workout; they don't become the plan.
                    notes = match?.notes,
                    sets = sets,
                )
            }
        val groups = Supersets.normalize(exercises.map { it.supersetGroup })
        return routine.copy(
            exercises = exercises.zip(groups) { exercise, group -> exercise.copy(supersetGroup = group) },
        )
    }

    /**
     * An exercise's sets in the routine. Each of today's sets, in order, stands for the next planned
     * set of its type: a logged one takes its place with today's values, an open one keeps it as it
     * was. An open set with no planned set left was added today and never done, so it's left out.
     */
    private fun setsFor(
        done: List<LoggedSet>,
        planned: List<RoutineSet>,
        newId: () -> String,
    ): List<RoutineSet> {
        val remaining = planned.toMutableList()
        return done.mapNotNull { set ->
            val plan = remaining.firstOrNull { it.type == set.type }?.also { remaining.remove(it) }
            if (!set.isCompleted) return@mapNotNull plan
            RoutineSet(
                id = plan?.id ?: newId(),
                type = set.type,
                weight = set.weight,
                reps = set.targetReps?.takeIf { it.isRange } ?: set.reps?.let(::RepRange),
                duration = set.duration,
                distanceMeters = set.distanceMeters,
                rpe = plan?.rpe,
            )
        }
    }

    /** Whether updating would change anything; row ids don't count. */
    fun changes(
        routine: Routine,
        updated: Routine,
    ): Boolean = routine.withoutIds() != updated.withoutIds()

    /**
     * What updating [routine] to [updated] would change about its shape, beyond weights and reps:
     * exercises swapped, added or dropped, a different number of sets, a new order. Empty when only
     * values change. [updated] must come from [fromWorkout], which keeps the ids of exercises that stay.
     */
    fun changeList(
        routine: Routine,
        updated: Routine,
    ): List<RoutineChange> {
        val stayed = routine.exercises.map { it.id }.toSet() intersect updated.exercises.map { it.id }.toSet()
        val dropped = gaps(routine.exercises, stayed)
        val added = gaps(updated.exercises, stayed)
        val changes = mutableListOf<RoutineChange>()
        // An exercise added where one was dropped, between the same neighbours, replaced it.
        val replacedIds = mutableSetOf<String>()
        for ((after, exercises) in added) {
            val gone = dropped[after].orEmpty()
            exercises.forEachIndexed { index, exercise ->
                val before = gone.getOrNull(index)
                if (before != null) {
                    replacedIds += before.id
                    changes += RoutineChange.Replaced(before.exerciseId, exercise.exerciseId)
                } else {
                    changes += RoutineChange.Added(exercise.exerciseId)
                }
            }
        }
        val previous = routine.exercises.associateBy { it.id }
        updated.exercises.forEach { exercise ->
            val was = previous[exercise.id]?.sets?.size ?: return@forEach
            if (was !=
                exercise.sets.size
            ) {
                changes += RoutineChange.SetCount(exercise.exerciseId, exercise.sets.size, was)
            }
        }
        routine.exercises
            .filter { it.id !in stayed && it.id !in replacedIds }
            .forEach { changes += RoutineChange.Dropped(it.exerciseId) }
        val oldOrder = routine.exercises.map { it.id }.filter { it in stayed }
        val newOrder = updated.exercises.map { it.id }.filter { it in stayed }
        if (oldOrder != newOrder) changes += RoutineChange.Reordered
        return changes.sortedBy { order(it, updated, routine) }
    }

    /**
     * The exercises not in [stayed], grouped by the one that stayed before them (null at the start),
     * in order: the places where exercises came or went.
     */
    private fun gaps(
        exercises: List<RoutineExercise>,
        stayed: Set<String>,
    ): Map<String?, List<RoutineExercise>> {
        var after: String? = null
        val gaps = mutableMapOf<String?, MutableList<RoutineExercise>>()
        exercises.forEach { exercise ->
            if (exercise.id in stayed) {
                after = exercise.id
            } else {
                gaps.getOrPut(after) { mutableListOf() } += exercise
            }
        }
        return gaps
    }

    /** Changes read top to bottom like the routine: by where their exercise sits, a new order last. */
    private fun order(
        change: RoutineChange,
        updated: Routine,
        routine: Routine,
    ): Int {
        val now = updated.exercises.map { it.exerciseId }
        val before = routine.exercises.map { it.exerciseId }
        return when (change) {
            is RoutineChange.Replaced -> now.indexOf(change.to)

            is RoutineChange.Added -> now.indexOf(change.exerciseId)

            is RoutineChange.SetCount -> now.indexOf(change.exerciseId)

            // A dropped exercise sits where it was.
            is RoutineChange.Dropped -> before.indexOf(change.exerciseId)

            RoutineChange.Reordered -> Int.MAX_VALUE
        }
    }

    private fun Routine.withoutIds() =
        exercises.map { exercise -> exercise.copy(id = "", sets = exercise.sets.map { it.copy(id = "") }) }
}

/** One change to a routine's shape that updating it from a workout would make; exercises by id. */
sealed interface RoutineChange {
    /** [from] swapped for [to] in the same place. */
    data class Replaced(
        val from: String,
        val to: String,
    ) : RoutineChange

    data class Added(
        val exerciseId: String,
    ) : RoutineChange

    data class Dropped(
        val exerciseId: String,
    ) : RoutineChange

    /** The exercise now has [sets] sets, where the routine had [was]. */
    data class SetCount(
        val exerciseId: String,
        val sets: Int,
        val was: Int,
    ) : RoutineChange

    /** The exercises that stay come in a different order. */
    data object Reordered : RoutineChange
}
