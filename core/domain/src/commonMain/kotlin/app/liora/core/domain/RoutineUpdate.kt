package app.liora.core.domain

import app.liora.core.model.ActiveWorkout
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
     * Exercises with nothing logged drop out. The routine's own rows are reused where the exercise is
     * the same, so only what really changed gets synced.
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
                val sets = done.sets.filter { it.isCompleted }
                if (sets.isEmpty()) return@mapNotNull null
                val match = unmatched.firstOrNull { it.exerciseId == done.exerciseId }?.also { unmatched.remove(it) }
                RoutineExercise(
                    id = match?.id ?: newId(),
                    exerciseId = done.exerciseId,
                    supersetGroup = done.supersetGroup,
                    restSeconds = done.restSeconds,
                    notes = done.notes,
                    sets =
                        sets.mapIndexed { index, set ->
                            val planned = match?.sets?.getOrNull(index)
                            RoutineSet(
                                id = planned?.id ?: newId(),
                                type = set.type,
                                weight = set.weight,
                                reps = set.targetReps?.takeIf { it.isRange } ?: set.reps?.let(::RepRange),
                                duration = set.duration,
                                distanceMeters = set.distanceMeters,
                                rpe = planned?.rpe,
                            )
                        },
                )
            }
        val groups = Supersets.normalize(exercises.map { it.supersetGroup })
        return routine.copy(
            exercises = exercises.zip(groups) { exercise, group -> exercise.copy(supersetGroup = group) },
        )
    }

    /** Whether updating would change anything; row ids don't count. */
    fun changes(
        routine: Routine,
        updated: Routine,
    ): Boolean = routine.withoutIds() != updated.withoutIds()

    private fun Routine.withoutIds() =
        exercises.map { exercise -> exercise.copy(id = "", sets = exercise.sets.map { it.copy(id = "") }) }
}
