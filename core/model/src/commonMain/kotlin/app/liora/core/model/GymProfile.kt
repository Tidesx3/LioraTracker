package app.liora.core.model

/**
 * What a gym has to load weights with: its bars, its plates, its dumbbells and the step of its machine
 * stacks. Weights are stored in kilograms like every weight; [unit] is what the gym's equipment is
 * labelled in, so a gym with pound plates loads 45 lb, not 20.41 kg.
 */
data class GymProfile(
    val id: String,
    val name: String,
    val unit: WeightUnit,
    val barbell: Mass,
    val ezBar: Mass,
    /** Plate sizes, each with how many pairs the gym has. */
    val plates: List<PlatePairs>,
    /** Runs of dumbbells, e.g. 2 to 40 kg in 2 kg steps, then 42.5 to 50 kg in 2.5 kg steps. */
    val dumbbells: List<DumbbellRun>,
    /** The step of machine and cable stacks. */
    val stackStep: Mass,
)

/** Plates of one size: [pairs] of them can go on a bar, one on each side. */
data class PlatePairs(
    val weight: Mass,
    val pairs: Int,
)

/** Dumbbells from [from] to [to], one every [step]. */
data class DumbbellRun(
    val from: Mass,
    val to: Mass,
    val step: Mass,
)
