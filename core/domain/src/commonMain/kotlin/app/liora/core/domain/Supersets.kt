package app.liora.core.domain

/**
 * Superset bookkeeping for an ordered list of exercises, given as each exercise's group number. A
 * superset is a run of at least two adjacent exercises sharing a number. Reordering or removing can
 * break that, so every change ends in [normalize].
 */
object Supersets {
    /**
     * Repairs and renumbers: a group that fell apart becomes one group per remaining run, an exercise
     * left alone leaves its superset, and groups are numbered 1, 2, 3… from the top.
     */
    fun normalize(groups: List<Int?>): List<Int?> {
        val result = MutableList<Int?>(groups.size) { null }
        var next = 1
        var start = 0
        while (start < groups.size) {
            val group = groups[start]
            var end = start
            while (group != null && end + 1 < groups.size && groups[end + 1] == group) end++
            if (end > start) {
                for (index in start..end) result[index] = next
                next++
            }
            start = end + 1
        }
        return result
    }

    /** Joins the exercise at [index] and the next one, together with any superset either is in already. */
    fun linkWithNext(
        groups: List<Int?>,
        index: Int,
    ): List<Int?> {
        require(index in 0 until groups.lastIndex) { "No exercise after index $index" }
        val next = groups[index + 1]
        val target = groups[index] ?: next ?: ((groups.filterNotNull().maxOrNull() ?: 0) + 1)
        val result = groups.toMutableList()
        result[index] = target
        var member = index + 1
        do {
            result[member] = target
            member++
        } while (next != null && member < groups.size && groups[member] == next)
        return normalize(result)
    }

    /** Takes the exercise at [index] out of its superset; neighbors it separated fall apart too. */
    fun unlink(
        groups: List<Int?>,
        index: Int,
    ): List<Int?> = normalize(groups.toMutableList().also { it[index] = null })

    /** Whether the exercise at [index] continues the superset of the one above it. */
    fun continuesAbove(
        groups: List<Int?>,
        index: Int,
    ): Boolean = index > 0 && groups[index] != null && groups[index] == groups[index - 1]
}
