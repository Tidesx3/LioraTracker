package app.liora.core.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class MassTest {
    @Test
    fun poundsConvertToKilograms() {
        assertClose(20.41165665, Mass.of(45.0, WeightUnit.Pound).kilograms)
    }

    @Test
    fun roundTripKeepsTheEnteredValue() {
        assertClose(225.0, Mass.of(225.0, WeightUnit.Pound).inUnit(WeightUnit.Pound))
        assertClose(102.5, Mass.of(102.5, WeightUnit.Kilogram).inUnit(WeightUnit.Kilogram))
    }

    @Test
    fun arithmeticAndOrdering() {
        val bar = Mass(20.0)
        val plates = Mass(2.5) * 4.0
        assertClose(30.0, (bar + plates).kilograms)
        assertTrue(bar < bar + plates)
    }

    private fun assertClose(
        expected: Double,
        actual: Double,
    ) {
        assertTrue(abs(expected - actual) < 1e-6, "expected $expected but was $actual")
    }
}
