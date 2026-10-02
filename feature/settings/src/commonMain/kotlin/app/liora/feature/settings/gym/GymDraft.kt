package app.liora.feature.settings.gym

import app.liora.core.model.DumbbellRun
import app.liora.core.model.GymProfile
import app.liora.core.model.Mass
import app.liora.core.model.PlatePairs
import app.liora.core.model.WeightUnit
import kotlin.math.roundToLong

/**
 * A gym as it's being edited: numbers in the gym's own unit, null where a field is empty or doesn't
 * read as a number. Rows carry a [key][PlateRow.key] so their fields stay put while others come and go.
 */
data class GymDraft(
    val name: String,
    val unit: WeightUnit,
    val barbell: Double?,
    val ezBar: Double?,
    val plates: List<PlateRow>,
    val dumbbells: List<DumbbellRow>,
    val stackStep: Double?,
) {
    /**
     * The gym this describes, with [id]; null while something's missing or makes no sense. Empty rows
     * are left out, plates go heaviest first.
     */
    fun toProfile(id: String): GymProfile? {
        val weights = listOf(barbell, ezBar, stackStep).map { weight -> weight.positive()?.let { Mass.of(it, unit) } }
        val plates = plates.filterNot { it.isEmpty }.map { it.toPlates(unit) }
        val dumbbells = dumbbells.filterNot { it.isEmpty }.map { it.toRun(unit) }
        val rowsComplete = null !in plates && null !in dumbbells
        if (name.isBlank() || null in weights || !rowsComplete) return null
        val (barbell, ezBar, stackStep) = weights.filterNotNull()
        return GymProfile(
            id = id,
            name = name.trim(),
            unit = unit,
            barbell = barbell,
            ezBar = ezBar,
            plates = plates.filterNotNull().sortedByDescending { it.weight },
            dumbbells = dumbbells.filterNotNull(),
            stackStep = stackStep,
        )
    }

    /** Whether the equipment (not the name) is the same, ignoring row keys. */
    fun sameEquipment(other: GymDraft): Boolean =
        unit == other.unit &&
            barbell == other.barbell &&
            ezBar == other.ezBar &&
            stackStep == other.stackStep &&
            plates.map { it.copy(key = 0) } == other.plates.map { it.copy(key = 0) } &&
            dumbbells.map { it.copy(key = 0) } == other.dumbbells.map { it.copy(key = 0) }
}

/** One plate size: its weight and how many pairs there are. */
data class PlateRow(
    val key: Int,
    val weight: Double? = null,
    val pairs: Int? = null,
) {
    val isEmpty: Boolean get() = weight == null && pairs == null

    /** A row that's begun must be finished: both fields, both above zero. */
    fun toPlates(unit: WeightUnit): PlatePairs? {
        val weight = weight.positive() ?: return null
        val pairs = pairs?.takeIf { it > 0 } ?: return null
        return PlatePairs(Mass.of(weight, unit), pairs)
    }
}

/** A run of dumbbells: from one weight to another in equal steps. */
data class DumbbellRow(
    val key: Int,
    val from: Double? = null,
    val to: Double? = null,
    val step: Double? = null,
) {
    val isEmpty: Boolean get() = from == null && to == null && step == null

    /** A run that's begun must be finished, up from its start, in steps above zero. */
    fun toRun(unit: WeightUnit): DumbbellRun? {
        val from = from.positive()
        val to = to?.takeIf { from != null && it >= from }
        val step = step.positive()
        return if (from == null || to == null || step == null) {
            null
        } else {
            DumbbellRun(Mass.of(from, unit), Mass.of(to, unit), Mass.of(step, unit))
        }
    }
}

/** [this] gym as a draft in its own unit's numbers, each row keyed by [nextKey]. */
fun GymProfile.toDraft(nextKey: () -> Int): GymDraft =
    GymDraft(
        name = name,
        unit = unit,
        barbell = barbell.inGymUnit(unit),
        ezBar = ezBar.inGymUnit(unit),
        plates = plates.map { PlateRow(nextKey(), it.weight.inGymUnit(unit), it.pairs) },
        dumbbells =
            dumbbells.map {
                DumbbellRow(nextKey(), it.from.inGymUnit(unit), it.to.inGymUnit(unit), it.step.inGymUnit(unit))
            },
        stackStep = stackStep.inGymUnit(unit),
    )

private fun Double?.positive(): Double? = this?.takeIf { it > 0 }

/** Without the noise a round trip through kilograms leaves (45.00000000000001 lb). */
private fun Mass.inGymUnit(unit: WeightUnit): Double = (inUnit(unit) * THOUSANDTHS).roundToLong() / THOUSANDTHS

private const val THOUSANDTHS = 1_000.0
