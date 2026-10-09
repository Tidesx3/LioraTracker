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

    /** The working sets added up to [total] reps at [from] last time, the top of their ranges: up a step, to [to]. */
    data class TotalReached(
        val total: Int,
        val from: Mass,
        val to: Mass,
    ) : ProgressionReason

    /** The first working set reached the top of its range ([reps]) at [from] last time: up a step, to [to]. */
    data class FirstSetReached(
        val reps: Int,
        val from: Mass,
        val to: Mass,
    ) : ProgressionReason

    /** The sets reached the top, but the gym loads nothing heavier than [weight] for the exercise. */
    data class NothingHeavier(
        val weight: Mass,
    ) : ProgressionReason

    /**
     * Not at the top yet: [weight] again, each set aiming for last time's [reps], more as [aim] says. With
     * one set getting a rep more, [set] is its number, or null when it has none (a failure set).
     */
    data class KeepGoing(
        val weight: Mass,
        val reps: List<Int>,
        val set: Int? = null,
        val aim: RepAims = RepAims.WeakestSet,
    ) : ProgressionReason

    /** Below the bottom of the range ([reps]) two sessions in a row at [from]: down a step, to [to]. */
    data class BelowTwice(
        val reps: Int,
        val from: Mass,
        val to: Mass,
    ) : ProgressionReason

    /** Below the bottom of the range ([reps]) at [from] last time: down a step, to [to]. */
    data class BelowOnce(
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
 * Double progression: each working set has a rep range as its goal. By Liora's defaults
 * ([ProgressionSettings], which Settings can change): when every one reached the top of its range last time,
 * each goes up a step from the weight lifted and the reps start again at the bottom. When a set fell below
 * the bottom two sessions in a row, each goes down a step. Otherwise the weights stay, each set aims for last
 * time's reps, and the weakest for one more: a rep more in all, so 8, 8, 8 becomes 9, 8, 8 and 10, 9, 8
 * becomes 10, 9, 9.
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
        settings: ProgressionSettings = ProgressionSettings(),
    ): ProgressionTarget {
        val counted = planned.indices.filter { planned[it].type.counts }
        val last = sessions.firstOrNull()?.working().orEmpty()
        if (last.isEmpty()) return firstTime(planned, counted)
        val sets = Pairing(planned, counted, last)
        val below =
            when (settings.belowRange) {
                BelowRange.TwiceInARow -> {
                    val before = sessions.getOrNull(1)?.working().orEmpty()
                    sets.anyBelow() && Pairing(planned, counted, before).anyBelow()
                }

                BelowRange.RightAway -> {
                    sets.anyBelow()
                }

                BelowRange.Never -> {
                    false
                }
            }
        return when {
            sets.reachedTop(settings.stepUp) -> stepUp(sets, step, settings.stepUp)
            below -> stepDown(sets, step, twice = settings.belowRange == BelowRange.TwiceInARow)
            else -> null
        } ?: keepGoing(sets, settings.repAims)
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
        mode: StepUpWhen,
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
        val reason =
            when (mode) {
                StepUpWhen.EverySetAtTop -> {
                    ProgressionReason.TopReached(ranged.size, ranged.firstNotNullOf { it.range }.max, from, to)
                }

                StepUpWhen.TotalReps -> {
                    ProgressionReason.TotalReached(sets.doneReps().sum(), from, to)
                }

                StepUpWhen.FirstSetAtTop -> {
                    ProgressionReason.FirstSetReached(ranged.first().done?.reps ?: 0, from, to)
                }
            }
        return ProgressionTarget(targets, reason)
    }

    /** A step down for every ranged set, or null when the gym has nothing lighter. */
    private fun stepDown(
        sets: Pairing,
        step: (Mass, Boolean) -> Mass,
        twice: Boolean,
    ): ProgressionTarget? {
        val ranged = sets.ranged()
        if (ranged.all { step(it.weight, false) == it.weight }) return null
        val targets =
            sets.targets { set ->
                set.range?.let { SetTarget(step(set.weight, false), it.min) } ?: set.same()
            }
        val from = sets.heaviest()
        val to = targets.mapNotNull { it?.weight }.maxOrNull() ?: from
        val bottom = ranged.firstNotNullOf { it.range }.min
        val reason =
            if (twice) ProgressionReason.BelowTwice(bottom, from, to) else ProgressionReason.BelowOnce(bottom, from, to)
        return ProgressionTarget(targets, reason)
    }

    /**
     * The same weights, each set aiming for last time's reps, and more as [aim] says. By default the weakest
     * set gets a rep more: one rep more in all, where it's likeliest to come, since the later sets of a
     * session tire first.
     */
    private fun keepGoing(
        sets: Pairing,
        aim: RepAims,
    ): ProgressionTarget {
        val onlyOne =
            when (aim) {
                RepAims.WeakestSet -> sets.weakest()
                RepAims.FirstBelowTop -> sets.firstBelowTop()
                RepAims.SameAsLastTime, RepAims.EverySet -> null
            }
        val more = { set: PairedSet -> aim == RepAims.EverySet || set === onlyOne }
        return ProgressionTarget(
            sets =
                sets.targets { set ->
                    val range = set.range
                    val reps = set.done?.reps
                    when {
                        range == null -> set.same()
                        reps == null -> SetTarget(set.weight, range.min)
                        else -> SetTarget(set.weight, (reps + if (more(set)) 1 else 0).coerceIn(range.min, range.max))
                    }
                },
            reason = ProgressionReason.KeepGoing(sets.heaviest(), sets.doneReps(), onlyOne?.number, aim),
        )
    }

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
        /** Its number as its badge shows it: normal sets count, other kinds have none. */
        val number: Int?,
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
                    val number =
                        planned[index].type.takeIf { it == SetType.Normal }?.let {
                            planned.take(index + 1).count { set -> set.type == SetType.Normal }
                        }
                    put(index, PairedSet(planned[index].reps, done, weight, number))
                }
            }

        fun ranged(): List<PairedSet> = paired.values.filter { it.range != null }

        fun anyBelow(): Boolean = paired.values.any { it.below }

        /** Whether the sets made it to the top, as [mode] counts it. */
        fun reachedTop(mode: StepUpWhen): Boolean {
            val ranged = ranged()
            if (ranged.isEmpty()) return false
            return when (mode) {
                StepUpWhen.EverySetAtTop -> ranged.all { it.reachedTop }
                StepUpWhen.TotalReps -> ranged.sumOf { it.done?.reps ?: 0 } >= ranged.sumOf { it.range?.max ?: 0 }
                StepUpWhen.FirstSetAtTop -> ranged.first().reachedTop
            }
        }

        /** The first set, in order, that did some reps last time and is still below its top. */
        fun firstBelowTop(): PairedSet? =
            paired.values.firstOrNull { set ->
                val reps = set.done?.reps
                val range = set.range
                reps != null && range != null && reps < range.max
            }

        fun heaviest(): Mass = paired.values.maxOfOrNull { it.weight } ?: Mass.Zero

        fun doneReps(): List<Int> = paired.values.mapNotNull { it.done?.reps }

        /** The set with the fewest reps last time that's still below its top; the first of them on a tie. */
        fun weakest(): PairedSet? =
            paired.values
                .mapNotNull { set ->
                    val reps = set.done?.reps ?: return@mapNotNull null
                    val range = set.range ?: return@mapNotNull null
                    (set to reps).takeIf { reps < range.max }
                }.minByOrNull { (_, reps) -> reps }
                ?.first

        fun targets(target: (PairedSet) -> SetTarget): List<SetTarget?> =
            planned.indices.map { index -> paired[index]?.let(target) }
    }

    private const val PERCENT = 100.0

    /** A gram: weights round-trip through the gym's unit, which can leave crumbs. */
    private const val TOLERANCE_KG = 0.001
}
