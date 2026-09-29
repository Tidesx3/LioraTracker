package app.liora.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupersetsTest {
    @Test
    fun normalizeRenumbersFromTheTop() {
        assertEquals(listOf(1, 1, null, 2, 2), Supersets.normalize(listOf(7, 7, null, 3, 3)))
    }

    @Test
    fun aGroupSplitByAMoveBecomesTwoOrNone() {
        // Dragging an unrelated exercise into the middle of a superset of four splits it in two.
        assertEquals(listOf(1, 1, null, 2, 2), Supersets.normalize(listOf(4, 4, null, 4, 4)))
        // A member dragged away is left alone, and so is the partner it left behind.
        assertEquals(listOf(null, null, null), Supersets.normalize(listOf(4, null, 4)))
    }

    @Test
    fun linkingStartsASuperset() {
        assertEquals(listOf(null, 1, 1, null), Supersets.linkWithNext(listOf(null, null, null, null), 1))
    }

    @Test
    fun linkingGrowsAnExistingSuperset() {
        // Bench + fly already paired; adding the next exercise makes a tri-set.
        assertEquals(listOf(1, 1, 1), Supersets.linkWithNext(listOf(1, 1, null), 1))
        // Linking the exercise above pulls it into the superset below.
        assertEquals(listOf(1, 1, 1), Supersets.linkWithNext(listOf(null, 2, 2), 0))
    }

    @Test
    fun linkingTwoSupersetsMergesThem() {
        assertEquals(listOf(1, 1, 1, 1), Supersets.linkWithNext(listOf(1, 1, 2, 2), 1))
    }

    @Test
    fun unlinkingLeavesTheRestPairedWhereTheyStillTouch() {
        assertEquals(listOf(1, 1, null), Supersets.unlink(listOf(1, 1, 1), 2))
        assertEquals(listOf(null, null), Supersets.unlink(listOf(1, 1), 0))
        // Taking out the middle of a tri-set separates the other two.
        assertEquals(listOf(null, null, null), Supersets.unlink(listOf(1, 1, 1), 1))
    }

    @Test
    fun continuesAboveMarksEveryMemberButTheFirst() {
        val groups = listOf(null, 1, 1, 1, null)
        assertFalse(Supersets.continuesAbove(groups, 1))
        assertTrue(Supersets.continuesAbove(groups, 2))
        assertTrue(Supersets.continuesAbove(groups, 3))
        assertFalse(Supersets.continuesAbove(groups, 4))
    }
}
