package app.liora.core.domain

import app.liora.core.model.Routine
import kotlin.time.Instant

/** A finished workout as a rotation sees it: the routine it was started from, or its name, and when. */
data class PastWorkout(
    val routineId: String?,
    val name: String?,
    val startedAt: Instant,
)

/** Where a rotation stands: the routine up next, and when one of its routines was last trained. */
data class RotationTurn(
    val folderId: String,
    val nextRoutineId: String,
    /** Null until one of its routines has been trained. */
    val lastTrainedAt: Instant?,
)

/**
 * Rotations: a folder whose routines take turns in their order, whichever day it is. Next up is the routine
 * after the one trained last, round to the first again; starting any of them moves the rotation on from
 * there, so skipping a day is just starting another.
 */
object Rotations {
    /**
     * [folderId]'s turn, from its [routines] in their order and [workouts], newest first. A workout counts
     * for a routine when it was started from it, or, for history imported from another app (which isn't
     * tied to routines), when it has the routine's name. Null for a folder without routines.
     */
    fun turn(
        folderId: String,
        routines: List<Routine>,
        workouts: List<PastWorkout>,
    ): RotationTurn? {
        if (routines.isEmpty()) return null
        for (workout in workouts) {
            val index = routines.indexOfFirst { it.matches(workout) }
            if (index >= 0) {
                return RotationTurn(folderId, routines[(index + 1) % routines.size].id, workout.startedAt)
            }
        }
        return RotationTurn(folderId, routines.first().id, lastTrainedAt = null)
    }

    /** The rotations' turns, the one trained most recently first; the ones never trained after, in order. */
    fun order(turns: List<RotationTurn>): List<RotationTurn> =
        turns.sortedWith(compareByDescending<RotationTurn, Instant?>(nullsFirst()) { it.lastTrainedAt })

    private fun Routine.matches(workout: PastWorkout): Boolean =
        if (workout.routineId != null) {
            workout.routineId == id
        } else {
            workout.name?.trim()?.equals(name.trim(), ignoreCase = true) == true
        }
}
