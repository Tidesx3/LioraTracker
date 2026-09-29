package app.liora.core.domain

import kotlin.math.roundToLong

/** Plates of one size available at the gym. [pairs] is how many can go on the bar (one per side each). */
data class PlateStock(
    val weight: Double,
    val pairs: Int = UNLIMITED,
) {
    companion object {
        const val UNLIMITED = Int.MAX_VALUE
    }
}

/** What to put on each side of the bar, and the total that results. */
data class PlateLoad(
    val perSide: List<Double>,
    val total: Double,
    val target: Double,
) {
    val isExact: Boolean get() = kotlin.math.abs(total - target) < WEIGHT_TOLERANCE
}

/**
 * Loads a bar as close to the target as possible without going over. Works in whatever unit the plates
 * are in (kg or lb). Uses a small branch-and-bound search, so odd plate sets (no 2.5s, only one pair
 * of 20s) still get the best answer instead of a greedy near-miss.
 */
object PlateCalculator {
    fun load(
        target: Double,
        barWeight: Double,
        plates: List<PlateStock>,
    ): PlateLoad {
        val perSideTarget = toUnits((target - barWeight) / 2)
        if (perSideTarget <= 0) return PlateLoad(emptyList(), barWeight, target)

        val stock = plates.filter { it.weight > 0 && it.pairs > 0 }.sortedByDescending { it.weight }
        val sizes = stock.map { toUnits(it.weight) }
        val counts = IntArray(stock.size)
        val best = IntArray(stock.size)
        var bestSum = 0L
        // Suffix capacity lets a branch be dropped as soon as it cannot beat the best so far.
        val capacityFrom =
            LongArray(stock.size + 1).also { capacity ->
                for (i in stock.indices.reversed()) {
                    val usable = minOf(stock[i].pairs.toLong(), perSideTarget / sizes[i])
                    capacity[i] = capacity[i + 1] + usable * sizes[i]
                }
            }

        fun search(
            index: Int,
            sum: Long,
        ) {
            if (sum > bestSum) {
                bestSum = sum
                counts.copyInto(best)
            }
            if (bestSum == perSideTarget || index == stock.size || sum + capacityFrom[index] <= bestSum) return
            val maxCount = minOf(stock[index].pairs.toLong(), (perSideTarget - sum) / sizes[index]).toInt()
            for (count in maxCount downTo 0) {
                counts[index] = count
                search(index + 1, sum + count * sizes[index])
                if (bestSum == perSideTarget) break
            }
            counts[index] = 0
        }
        search(0, 0)

        val perSide = stock.indices.flatMap { i -> List(best[i]) { stock[i].weight } }
        return PlateLoad(perSide = perSide, total = barWeight + 2 * fromUnits(bestSum), target = target)
    }

    private fun toUnits(weight: Double): Long = (weight * UNITS_PER_WEIGHT).roundToLong()

    private fun fromUnits(units: Long): Double = units / UNITS_PER_WEIGHT

    /** Integer arithmetic in thousandths avoids 1.25 + 1.25 != 2.5 surprises. */
    private const val UNITS_PER_WEIGHT = 1_000.0
}

private const val WEIGHT_TOLERANCE = 1e-6
