package app.liora.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DecimalInputTest {
    @Test
    fun acceptsCommaAndDot() {
        assertEquals(82.5, parseDecimalInput("82,5"))
        assertEquals(82.5, parseDecimalInput("82.5"))
        assertEquals(100.0, parseDecimalInput(" 100 "))
        assertEquals(0.5, parseDecimalInput(",5"))
        assertEquals(12.0, parseDecimalInput("12,"))
    }

    @Test
    fun rejectsAmbiguousOrInvalidInput() {
        assertNull(parseDecimalInput(""))
        assertNull(parseDecimalInput("1.234,5"))
        assertNull(parseDecimalInput("8,2,5"))
        assertNull(parseDecimalInput("-5"))
        assertNull(parseDecimalInput("12kg"))
    }
}
