package app.liora.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals

class ValueAxisTest {
    @Test
    fun roundStepsWithRoomAboveAndBelow() {
        assertEquals(ValueAxis(min = 85.0, max = 89.0, step = 1.0), ValueAxis.of(listOf(88.0, 86.5, 85.5)))
    }

    @Test
    fun aRoundValueAtEitherEndStillGetsRoom() {
        assertEquals(ValueAxis(min = 70.0, max = 110.0, step = 10.0), ValueAxis.of(listOf(80.0, 100.0)))
    }

    @Test
    fun floatingPointNoiseNeitherAddsNorDropsAStep() {
        // 0.88 / 0.01 comes out a hair over 88, and 0.3 / 0.1 a hair under 3.
        val metres = ValueAxis.of(listOf(0.88, 0.865, 0.855))
        assertEquals(0.85, metres.min, TOLERANCE)
        assertEquals(0.89, metres.max, TOLERANCE)
        assertEquals(0.4, ValueAxis.of(listOf(0.1, 0.3)).max, TOLERANCE)
    }

    @Test
    fun aSingleValueSitsInTheMiddle() {
        assertEquals(ValueAxis(min = 75.0, max = 85.0, step = 5.0), ValueAxis.of(listOf(80.0)))
    }

    @Test
    fun neverBelowZero() {
        assertEquals(0.0, ValueAxis.of(listOf(0.0, 3.0)).min)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
