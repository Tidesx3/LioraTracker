package app.liora.core.domain

import app.liora.core.model.Equipment
import app.liora.core.model.GymProfile
import app.liora.core.model.Mass

data class WarmupSet(
    val weight: Mass,
    val reps: Int,
)

/**
 * A ramp of warm-up sets toward [the working weight][generate]: the empty bar, then roughly 40/60/80 %
 * (plus 90 % for a single when a barbell lift is heavy), with fewer reps as the load climbs. Each weight
 * rounds down to what the gym can load for the exercise's equipment; dumbbells and machines have no bar,
 * so their ramp starts at 40 %.
 */
object WarmupGenerator {
    fun generate(
        working: Mass,
        equipment: Equipment,
        gym: GymProfile,
    ): List<WarmupSet> {
        val bar = LoadRounding.barFor(equipment, gym)
        if (working.kilograms <= (bar?.kilograms ?: 0.0) + TOLERANCE_KG) return emptyList()
        val ramp =
            buildList {
                if (bar != null) add(Step(load = bar, reps = BAR_REPS))
                add(Step(load = working * 0.4, reps = 8))
                add(Step(load = working * 0.6, reps = 5))
                add(Step(load = working * 0.8, reps = 3))
                if (bar != null && working >= bar * HEAVY_MULTIPLE) add(Step(load = working * 0.9, reps = 1))
            }
        return ramp
            .mapNotNull { step -> LoadRounding.roundDown(step.load, equipment, gym)?.let { WarmupSet(it, step.reps) } }
            .filter { it.weight.kilograms < working.kilograms - TOLERANCE_KG }
            // Rounding can land two steps on the same load; each set must be heavier than the last.
            .fold(emptyList()) { sets, set ->
                val last = sets.lastOrNull()?.weight?.kilograms ?: 0.0
                if (set.weight.kilograms > last + TOLERANCE_KG) sets + set else sets
            }
    }

    private data class Step(
        val load: Mass,
        val reps: Int,
    )

    private const val BAR_REPS = 10

    /** Working weight at which a heavy single at 90 % is worth it (3× the bar ≈ 60 kg on a 20 kg bar). */
    private const val HEAVY_MULTIPLE = 3.0

    /** Weights from pounds differ from their kilograms in the last bits; this treats them as equal. */
    private const val TOLERANCE_KG = 1e-6
}
