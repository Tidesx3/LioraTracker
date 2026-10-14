package app.liora.core.domain

import app.liora.core.model.LoggedSet
import app.liora.core.model.RepRange
import app.liora.core.model.RoutineSet

/** Of a session's working sets with a rep range ([ranged]), how many reached its top ([reached]). */
data class RangeTops(
    val reached: Int,
    val ranged: Int,
)

/**
 * The top of a rep range, where double progression steps up: set by set on the way there, and for a whole
 * session of an exercise without progression. An exact rep count has no top to reach, so only real ranges count.
 */
object TopOfRange {
    /** Whether [set] is a working set done at the top of its rep range. */
    fun reached(set: LoggedSet): Boolean {
        val range = set.targetReps ?: return false
        return set.isCompleted && set.type.countsForProgression && range.isRange && (set.reps ?: 0) >= range.max
    }

    /** How many of [sets]' working sets with a rep range reached its top: the way to a step, set by set. */
    fun tops(sets: List<LoggedSet>): RangeTops {
        val ranged = sets.filter { it.type.countsForProgression && it.targetReps?.isRange == true }
        return RangeTops(reached = ranged.count(::reached), ranged = ranged.size)
    }

    /**
     * Whether [session] reached the top of every rep range [planned] gives the working sets, paired set by set
     * as [Progression.next] pairs them: for an exercise without progression, a sign it's ready for more weight.
     * A plan with an exact count says no.
     */
    fun readyForMore(
        planned: List<RoutineSet>,
        session: List<LoggedSet>,
    ): Boolean {
        val ranges =
            planned
                .filter { it.type.countsForProgression }
                .map { it.reps?.takeIf(RepRange::isRange) ?: return false }
        if (ranges.isEmpty()) return false
        val done = session.progressionSets()
        return ranges.withIndex().all { (ordinal, range) -> (done.getOrNull(ordinal)?.reps ?: 0) >= range.max }
    }
}
