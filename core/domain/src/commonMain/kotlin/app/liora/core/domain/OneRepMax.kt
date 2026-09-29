package app.liora.core.domain

import app.liora.core.model.Mass

enum class OneRepMaxFormula {
    /** w × (1 + r/30). The common default; slightly generous at higher reps. */
    Epley,

    /** w × 36 / (37 − r). More conservative above ~10 reps. */
    Brzycki,
}

/** Sets above this many reps say little about maximal strength, so they get no estimate. */
const val MAX_REPS_FOR_ESTIMATE = 12

/** Estimated one-rep max, or null when the set is outside the range where formulas are meaningful. */
fun estimateOneRepMax(
    weight: Mass,
    reps: Int,
    formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
): Mass? {
    if (reps !in 1..MAX_REPS_FOR_ESTIMATE || weight.kilograms <= 0.0) return null
    if (reps == 1) return weight
    val kg = weight.kilograms
    return Mass(
        when (formula) {
            OneRepMaxFormula.Epley -> kg * (1 + reps / EPLEY_DIVISOR)
            OneRepMaxFormula.Brzycki -> kg * BRZYCKI_NUMERATOR / (BRZYCKI_NUMERATOR + 1 - reps)
        },
    )
}

private const val EPLEY_DIVISOR = 30.0
private const val BRZYCKI_NUMERATOR = 36.0
