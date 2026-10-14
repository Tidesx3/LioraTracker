package app.liora.core.domain

import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.RoutineSet
import app.liora.core.model.SetType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The top of a rep range: set by set on the way to a step, and a whole session ready for more weight. */
class TopOfRangeTest {
    private val plan = List(3) { RoutineSet("p$it", reps = RepRange(8, 12)) }

    @Test
    fun aSetReachesTheTopOfItsRange() {
        val top = set(80.0, 12).copy(targetReps = RepRange(8, 12))
        assertTrue(TopOfRange.reached(top))
        assertFalse(TopOfRange.reached(top.copy(reps = 11)))
        assertFalse(TopOfRange.reached(top.copy(completedAt = null)))
        assertFalse(TopOfRange.reached(top.copy(type = SetType.Warmup)))
        // An exact count has no top to reach.
        assertFalse(TopOfRange.reached(top.copy(targetReps = RepRange(12))))
        assertFalse(TopOfRange.reached(top.copy(targetReps = null)))
    }

    @Test
    fun theTopsCountTheSetsWithARange() {
        val top = set(80.0, 12).copy(targetReps = RepRange(8, 12))
        val sets = listOf(top, top.copy(reps = 10), top.copy(reps = null, completedAt = null), set(40.0, 12))

        assertEquals(RangeTops(reached = 1, ranged = 3), TopOfRange.tops(sets))
    }

    @Test
    fun readyForMoreAtTheTopOfEveryRange() {
        assertTrue(TopOfRange.readyForMore(plan, session(80.0, 12, 12, 12)))
        assertFalse(TopOfRange.readyForMore(plan, session(80.0, 12, 12, 11)))
        assertFalse(TopOfRange.readyForMore(plan, session(80.0, 12, 12)))
        // Warm-ups neither count nor need a range.
        val warmup = RoutineSet("w", type = SetType.Warmup, reps = RepRange(10))
        assertTrue(
            TopOfRange.readyForMore(
                listOf(warmup) + plan,
                listOf(set(40.0, 10, SetType.Warmup)) + session(80.0, 12, 12, 12),
            ),
        )
        // An exact count has no top to reach.
        val exact = List(3) { RoutineSet("e$it", reps = RepRange(10)) }
        assertFalse(TopOfRange.readyForMore(exact, session(80.0, 10, 10, 10)))
        assertFalse(TopOfRange.readyForMore(emptyList(), session(80.0, 12)))
    }

    private fun session(
        kilograms: Double,
        vararg reps: Int,
    ) = reps.map { set(kilograms, it) }

    private fun set(
        kilograms: Double,
        reps: Int,
        type: SetType = SetType.Normal,
    ) = LoggedSet("s", type = type, weight = Mass(kilograms), reps = reps, completedAt = DONE)

    private companion object {
        val DONE = Instant.parse("2026-10-01T18:00:00Z")
    }
}
