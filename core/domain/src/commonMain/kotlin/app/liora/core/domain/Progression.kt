package app.liora.core.domain

import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.RoutineSet
import app.liora.core.model.SetType

/** What a planned set aims for this time: the weight to load (null with nothing to go on) and the reps. */
data class SetTarget(
    val weight: Mass?,
    val reps: Int?,
)

/** Why the targets are what they are. Structured, so each language words it itself, numbers and all. */
sealed interface ProgressionReason {
    /** Nothing to go on yet: the routine's own weights, from the bottom of each range. */
    data object FirstTime : ProgressionReason

    /** All [sets] working sets reached the top of their range ([reps]) at [from] last time: up a step, to [to]. */
    data class TopReached(
        val sets: Int,
        val reps: Int,
        val from: Mass,
        val to: Mass,
    ) : ProgressionReason

    /** Every set reached the top, but the gym loads nothing heavier than [weight] for the exercise. */
    data class NothingHeavier(
        val weight: Mass,
    ) : ProgressionReason

    /** Not every set reached the top: [weight] again, aiming a rep past last time's [reps] on each set. */
    data class KeepGoing(
        val weight: Mass,
        val reps: List<Int>,
    ) : ProgressionReason

    /** Below the bottom of the range ([reps]) two sessions in a row at [from]: down a step, to [to]. */
    data class BelowTwice(
        val reps: Int,
        val from: Mass,
        val to: Mass,
    ) : ProgressionReason
}

/** A target the safety bound flagged: [target] against [heaviestLately], the heaviest weight of the last weeks. */
data class BigJump(
    val target: Mass,
    val heaviestLately: Mass,
)

/** The next session's targets for one exercise of a routine, and why. */
data class ProgressionTarget(
    /** One per planned set, in order; null for warm-ups and drop sets, which keep the routine's values. */
    val sets: List<SetTarget?>,
    val reason: ProgressionReason,
) {
    /** The heaviest weight aimed for: the one the safety bound checks. */
    val heaviest: Mass? get() = sets.mapNotNull { it?.weight }.maxOrNull()
}

/**
 * Double progression: each working set has a rep range as its goal. When every one reached the top of its
 * range last time, each goes up a step from the weight lifted and the reps start again at the bottom. When
 * a set fell below the bottom two sessions in a row, each goes down a step. Otherwise the weights stay and
 * each set aims for a rep more than last time, up to the top.
 *
 * Planned working sets pair with last time's by count, as the Previous column pairs them; warm-ups and drop
 * sets don't count. A set skipped last time didn't reach the top, so there's no step up.
 */
object Progression {
    /**
     * The targets for [planned], the routine's sets for the exercise, from [sessions]: its latest sessions,
     * newest first, each as its sets were logged. [step] is one load up or down from a weight, as the gym
     * loads the exercise (`LoadRounding.step`).
     */
    fun next(
        planned: List<RoutineSet>,
        sessions: List<List<LoggedSet>>,
        step: (from: Mass, up: Boolean) -> Mass,
    ): ProgressionTarget {
        val counted = planned.indices.filter { planned[it].type.counts }
        val last = sessions.firstOrNull()?.working().orEmpty()
        if (last.isEmpty()) return firstTime(planned, counted)
        val sets = Pairing(planned, counted, last)
        val ranged = sets.ranged()
        val before = sessions.getOrNull(1)?.working().orEmpty()
        return when {
            ranged.isNotEmpty() && ranged.all { it.reachedTop } -> stepUp(sets, step)
            sets.anyBelow() && Pairing(planned, counted, before).anyBelow() -> stepDown(sets, step) ?: keepGoing(sets)
            else -> keepGoing(sets)
        }
    }

    /**
     * Whether [target] is a big jump worth a second look: more than [limitPercent] above [heaviest], the
     * heaviest weight lifted for the exercise lately, and more than the gym's own step above it
     * ([oneStepUp]). One step is never flagged, as a step on a light cable is already 30 % or more; this is
     * for a mistyped step or a stray weight in the history. By weight rather than e1RM, since the reps drop
     * as the weight goes up.
     */
    fun isBigJump(
        target: Mass,
        heaviest: Mass?,
        oneStepUp: Mass,
        limitPercent: Int,
    ): Boolean {
        if (heaviest == null || heaviest <= Mass.Zero) return false
        val limit = heaviest * (1 + limitPercent / PERCENT)
        return target.kilograms > limit.kilograms + TOLERANCE_KG &&
            target.kilograms > oneStepUp.kilograms + TOLERANCE_KG
    }

    private fun firstTime(
        planned: List<RoutineSet>,
        counted: List<Int>,
    ) = ProgressionTarget(
        sets =
            planned.mapIndexed { index, set ->
                if (index in
                    counted
                ) {
                    SetTarget(set.weight, set.reps?.min)
                } else {
                    null
                }
            },
        reason = ProgressionReason.FirstTime,
    )

    private fun stepUp(
        sets: Pairing,
        step: (Mass, Boolean) -> Mass,
    ): ProgressionTarget {
        val targets = sets.targets { set -> set.range?.let { SetTarget(step(set.weight, true), it.min) } ?: set.same() }
        val ranged = sets.ranged()
        val from = sets.heaviest()
        val to = targets.mapNotNull { it?.weight }.maxOrNull() ?: from
        if (ranged.all { step(it.weight, true) == it.weight }) {
            return ProgressionTarget(
                sets = sets.targets { set -> SetTarget(set.weight, set.range?.max ?: set.done?.reps) },
                reason = ProgressionReason.NothingHeavier(from),
            )
        }
        val top = ranged.firstNotNullOf { it.range }.max
        return ProgressionTarget(targets, ProgressionReason.TopReached(ranged.size, top, from, to))
    }

    /** A step down for every ranged set, or null when the gym has nothing lighter. */
    private fun stepDown(
        sets: Pairing,
        step: (Mass, Boolean) -> Mass,
    ): ProgressionTarget? {
        val ranged = sets.ranged()
        if (ranged.all { step(it.weight, false) == it.weight }) return null
        val targets =
            sets.targets { set ->
                set.range?.let { SetTarget(step(set.weight, false), it.min) } ?: set.same()
            }
        val to = targets.mapNotNull { it?.weight }.maxOrNull() ?: sets.heaviest()
        val bottom = ranged.firstNotNullOf { it.range }.min
        return ProgressionTarget(targets, ProgressionReason.BelowTwice(bottom, sets.heaviest(), to))
    }

    private fun keepGoing(sets: Pairing): ProgressionTarget =
        ProgressionTarget(
            sets =
                sets.targets { set ->
                    val range = set.range
                    val reps = set.done?.reps
                    when {
                        range == null -> set.same()
                        reps == null -> SetTarget(set.weight, range.min)
                        else -> SetTarget(set.weight, (reps + 1).coerceIn(range.min, range.max))
                    }
                },
            reason = ProgressionReason.KeepGoing(sets.heaviest(), sets.doneReps()),
        )

    /** The sets that count for progression: completed, and neither warm-ups nor drop sets. */
    private fun List<LoggedSet>.working(): List<LoggedSet> = filter { it.isCompleted && it.type.counts }

    private val SetType.counts: Boolean get() = this != SetType.Warmup && this != SetType.Drop

    /** One planned working set and its counterpart last time. */
    private class PairedSet(
        val range: RepRange?,
        /** What was done last time; null for a set skipped. */
        val done: LoggedSet?,
        /** The weight lifted last time, or for a set skipped, the nearest set's before it. */
        val weight: Mass,
    ) {
        val reachedTop: Boolean get() = range != null && (done?.reps ?: 0) >= range.max
        val below: Boolean get() = range != null && done != null && (done.reps ?: 0) < range.min

        fun same() = SetTarget(weight, done?.reps)
    }

    /** [planned]'s working sets ([counted]) side by side with a session's. */
    private class Pairing(
        private val planned: List<RoutineSet>,
        private val counted: List<Int>,
        session: List<LoggedSet>,
    ) {
        private val paired: Map<Int, PairedSet> =
            buildMap {
                var weight = Mass.Zero
                counted.forEachIndexed { ordinal, index ->
                    val done = session.getOrNull(ordinal)
                    weight =
                        when {
                            // A weighted bodyweight set without added weight is bodyweight alone.
                            done != null -> done.weight ?: Mass.Zero

                            ordinal == 0 -> planned[index].weight ?: Mass.Zero

                            // Skipped: the set before it shows where it stood.
                            else -> weight
                        }
                    put(index, PairedSet(planned[index].reps, done, weight))
                }
            }

        fun ranged(): List<PairedSet> = paired.values.filter { it.range != null }

        fun anyBelow(): Boolean = paired.values.any { it.below }

        fun heaviest(): Mass = paired.values.maxOfOrNull { it.weight } ?: Mass.Zero

        fun doneReps(): List<Int> = paired.values.mapNotNull { it.done?.reps }

        fun targets(target: (PairedSet) -> SetTarget): List<SetTarget?> =
            planned.indices.map { index -> paired[index]?.let(target) }
    }

    private const val PERCENT = 100.0

    /** A gram: weights round-trip through the gym's unit, which can leave crumbs. */
    private const val TOLERANCE_KG = 0.001
}
