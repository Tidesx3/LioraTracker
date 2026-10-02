package app.liora.core.domain

import app.liora.core.model.DumbbellRun
import app.liora.core.model.Equipment
import app.liora.core.model.GymProfile
import app.liora.core.model.Mass
import app.liora.core.model.WeightUnit
import kotlin.math.roundToLong

/**
 * The loads a gym can actually make for an exercise, by its equipment: the bar and plates for barbells
 * and EZ bars, the dumbbell rack for dumbbells, the stack for machines and cables. Anything else moves in
 * the plates' smallest step. Works in the gym's own unit, so pound plates add up exactly.
 */
object LoadRounding {
    /** The heaviest load the gym can make for [equipment] that isn't over [target]; null when even the lightest is. */
    fun roundDown(
        target: Mass,
        equipment: Equipment,
        gym: GymProfile,
    ): Mass? = loading(equipment, gym).roundDown(target.toMilli(gym.unit))?.toMass(gym.unit)

    /**
     * The next load up or down from [from] that the gym can make for [equipment], or [from] itself when
     * there is none that way. From an empty field (zero), up is the lightest load, such as the bar.
     */
    fun step(
        from: Mass,
        up: Boolean,
        equipment: Equipment,
        gym: GymProfile,
    ): Mass {
        val loading = loading(equipment, gym)
        val milli = from.toMilli(gym.unit)
        return (if (up) loading.up(milli) else loading.down(milli))?.toMass(gym.unit) ?: from
    }

    /** The bar [equipment] is loaded on with plates, or null for equipment that takes none. */
    fun barFor(
        equipment: Equipment,
        gym: GymProfile,
    ): Mass? =
        when (equipment) {
            Equipment.Barbell -> gym.barbell
            Equipment.EzBar -> gym.ezBar
            else -> null
        }

    /**
     * What goes on each side of [equipment]'s bar for [target], in the gym's unit: the closest load that
     * isn't over it. Null for equipment that takes no plates.
     */
    fun plates(
        target: Mass,
        equipment: Equipment,
        gym: GymProfile,
    ): PlateLoad? {
        val bar = barFor(equipment, gym) ?: return null
        return PlateCalculator.load(
            target = target.inGymUnit(gym.unit),
            barWeight = bar.inGymUnit(gym.unit),
            plates = gym.plates.map { PlateStock(it.weight.inGymUnit(gym.unit), it.pairs) },
        )
    }

    private fun loading(
        equipment: Equipment,
        gym: GymProfile,
    ): Loading {
        val unit = gym.unit
        val smallestPlate = gym.plates.filter { it.pairs > 0 }.minOfOrNull { it.weight.toMilli(unit) }
        // Two of the smallest plate, one each side; else the stack step; else one whole unit.
        val freeStep =
            smallestPlate?.takeIf { it > 0 }?.let { it * 2 } ?: gym.stackStep.toMilli(unit).takeIf { it > 0 } ?: MILLI
        val dumbbells =
            gym.dumbbells
                .flatMap { run -> run.weights(unit) }
                .distinct()
                .sorted()
        return when {
            equipment == Equipment.Barbell || equipment == Equipment.EzBar -> {
                Loading.Plates(checkNotNull(barFor(equipment, gym)), gym, freeStep)
            }

            equipment == Equipment.Dumbbell && dumbbells.isNotEmpty() -> {
                Loading.Rack(dumbbells)
            }

            (equipment == Equipment.Machine || equipment == Equipment.Cable) && gym.stackStep.toMilli(unit) > 0 -> {
                Loading.Steps(gym.stackStep.toMilli(unit))
            }

            else -> {
                Loading.Free(freeStep)
            }
        }
    }

    /** Loads in thousandths of the gym's unit, so 1.25 + 1.25 is exactly 2.5. */
    private sealed interface Loading {
        fun roundDown(target: Long): Long?

        fun up(from: Long): Long?

        fun down(from: Long): Long?

        /** A bar with plates on it. */
        class Plates(
            barWeight: Mass,
            private val gym: GymProfile,
            private val increment: Long,
        ) : Loading {
            private val bar = barWeight.toMilli(gym.unit)

            override fun roundDown(target: Long): Long? {
                if (target < bar) return null
                val load =
                    PlateCalculator.load(
                        target = target / MILLI_D,
                        barWeight = bar / MILLI_D,
                        plates = gym.plates.map { PlateStock(it.weight.inGymUnit(gym.unit), it.pairs) },
                    )
                return (load.total * MILLI_D).roundToLong()
            }

            // A plate short of the next step, the one after may still fit, so look a few steps ahead.
            override fun up(from: Long): Long? {
                if (from < bar) return bar
                return (1..LOOK_AHEAD).firstNotNullOfOrNull { steps ->
                    roundDown(from + steps * increment)?.takeIf { it > from }
                }
            }

            override fun down(from: Long): Long? = roundDown(from - 1)
        }

        /** Dumbbells: one of these weights. */
        class Rack(
            private val weights: List<Long>,
        ) : Loading {
            override fun roundDown(target: Long): Long? = weights.lastOrNull { it <= target }

            override fun up(from: Long): Long? = weights.firstOrNull { it > from }

            override fun down(from: Long): Long? = weights.lastOrNull { it < from }
        }

        /** Any multiple of [step], such as a machine's stack. */
        class Steps(
            private val step: Long,
        ) : Loading {
            override fun roundDown(target: Long): Long? = (target / step * step).takeIf { it > 0 }

            override fun up(from: Long): Long = (from.coerceAtLeast(0) / step + 1) * step

            override fun down(from: Long): Long? = if (from <= 0) null else ((from - 1) / step * step)
        }

        /**
         * Equipment the gym doesn't describe (kettlebells, bands): targets round to multiples of [step],
         * but ± moves a step from whatever was typed rather than onto a grid the gym may not have.
         */
        class Free(
            private val step: Long,
        ) : Loading {
            override fun roundDown(target: Long): Long? = (target / step * step).takeIf { it > 0 }

            override fun up(from: Long): Long = from.coerceAtLeast(0) + step

            override fun down(from: Long): Long? = if (from <= 0) null else (from - step).coerceAtLeast(0)
        }
    }

    private fun Mass.toMilli(unit: WeightUnit): Long = (inUnit(unit) * MILLI_D).roundToLong()

    private fun Long.toMass(unit: WeightUnit): Mass = Mass.of(this / MILLI_D, unit)

    /** The weight in the gym's unit, without the noise a round trip through kilograms leaves (45.00000000000001 lb). */
    private fun Mass.inGymUnit(unit: WeightUnit): Double = toMilli(unit) / MILLI_D

    private fun DumbbellRun.weights(unit: WeightUnit): List<Long> {
        val from = from.toMilli(unit)
        val to = to.toMilli(unit)
        val step = step.toMilli(unit)
        if (step <= 0 || from <= 0 || to < from) return emptyList()
        return generateSequence(from) { it + step }.takeWhile { it <= to }.take(MAX_DUMBBELLS).toList()
    }

    private const val MILLI = 1_000L
    private const val MILLI_D = 1_000.0

    /** How many plate steps up to look for the next load when a size has run out. */
    private const val LOOK_AHEAD = 20

    /** Enough for any real rack, and a stop for a mistyped run (0.001 kg steps). */
    private const val MAX_DUMBBELLS = 500
}
