package app.liora.core.domain

import app.liora.core.model.DumbbellRun
import app.liora.core.model.GymProfile
import app.liora.core.model.Mass
import app.liora.core.model.PlatePairs
import app.liora.core.model.WeightUnit

/** Gym equipment as most gyms have it, used until the user sets up a gym of their own. */
object GymProfiles {
    /** The id of [standard], which is never stored. */
    const val STANDARD_ID = "standard"

    /**
     * A typical gym in [unit]: a 20 kg bar and plates from 25 kg down to 1.25 kg, dumbbells in 2 kg steps
     * up to 40 kg, then 2.5 kg steps, and 5 kg stack steps; or the same in pounds.
     */
    fun standard(
        unit: WeightUnit,
        name: String = "",
    ): GymProfile {
        val inUnit = { value: Double -> Mass.of(value, unit) }
        val equipment =
            when (unit) {
                WeightUnit.Kilogram -> KILOGRAM_GYM
                WeightUnit.Pound -> POUND_GYM
            }
        return GymProfile(
            id = STANDARD_ID,
            name = name,
            unit = unit,
            barbell = inUnit(equipment.barbell),
            ezBar = inUnit(equipment.ezBar),
            plates = equipment.plates.map { (weight, pairs) -> PlatePairs(inUnit(weight), pairs) },
            dumbbells =
                equipment.dumbbells.map { (from, to, step) ->
                    DumbbellRun(inUnit(from), inUnit(to), inUnit(step))
                },
            stackStep = inUnit(equipment.stackStep),
        )
    }

    /** A gym's equipment in its own unit's numbers. */
    private class Equipment(
        val barbell: Double,
        val ezBar: Double,
        val plates: List<Pair<Double, Int>>,
        val dumbbells: List<Triple<Double, Double, Double>>,
        val stackStep: Double,
    )

    private val KILOGRAM_GYM =
        Equipment(
            barbell = 20.0,
            ezBar = 10.0,
            plates = listOf(25.0 to 4, 20.0 to 2, 15.0 to 2, 10.0 to 2, 5.0 to 2, 2.5 to 2, 1.25 to 2),
            dumbbells = listOf(Triple(2.0, 40.0, 2.0), Triple(42.5, 50.0, 2.5)),
            stackStep = 5.0,
        )

    private val POUND_GYM =
        Equipment(
            barbell = 45.0,
            ezBar = 25.0,
            plates = listOf(45.0 to 4, 35.0 to 2, 25.0 to 2, 10.0 to 2, 5.0 to 2, 2.5 to 2),
            dumbbells = listOf(Triple(5.0, 100.0, 5.0)),
            stackStep = 10.0,
        )
}
