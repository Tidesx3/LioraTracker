package app.liora.core.domain

import kotlin.math.floor

data class WarmupSet(
    val weight: Double,
    val reps: Int,
)

/**
 * A ramp of warm-up sets toward [workingWeight]: empty bar, then roughly 40/60/80 % (plus 90 % for a
 * single when the working weight is heavy), with fewer reps as the load climbs. Weights round down to
 * what can be loaded with [increment] steps (twice the smallest plate), in the same unit as the inputs.
 */
object WarmupGenerator {
    fun generate(
        workingWeight: Double,
        barWeight: Double,
        increment: Double,
    ): List<WarmupSet> {
        if (workingWeight <= barWeight || increment <= 0) return emptyList()
        val steps =
            buildList {
                add(Step(fraction = null, reps = BAR_REPS))
                add(Step(fraction = 0.4, reps = 8))
                add(Step(fraction = 0.6, reps = 5))
                add(Step(fraction = 0.8, reps = 3))
                if (workingWeight >= barWeight * HEAVY_MULTIPLE) add(Step(fraction = 0.9, reps = 1))
            }

        val sets = mutableListOf<WarmupSet>()
        for (step in steps) {
            val raw = step.fraction?.let { workingWeight * it } ?: barWeight
            val weight = barWeight + floor((raw - barWeight) / increment + ROUNDING_SLACK) * increment
            val previous = sets.lastOrNull()?.weight ?: Double.NEGATIVE_INFINITY
            if (weight >= barWeight && weight > previous && weight < workingWeight) sets += WarmupSet(weight, step.reps)
        }
        return sets
    }

    private data class Step(
        val fraction: Double?,
        val reps: Int,
    )

    private const val BAR_REPS = 10

    /** Working weight at which a heavy single at 90 % is worth it (3× the bar ≈ 60 kg on a 20 kg bar). */
    private const val HEAVY_MULTIPLE = 3.0

    /** Guards against 59.999… rounding down a whole step. */
    private const val ROUNDING_SLACK = 1e-9
}
