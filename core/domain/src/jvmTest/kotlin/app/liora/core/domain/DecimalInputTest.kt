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

    @Test
    fun clockDigitsFillFromTheRight() {
        assertEquals(90, parseClockDigits("130"))
        assertEquals(45, parseClockDigits("45"))
        assertEquals(180, parseClockDigits("300"))
        assertEquals(300, parseClockDigits("500"))
        assertEquals(5, parseClockDigits("5"))
        assertEquals(0, parseClockDigits("000"))
        // Formatted text reads back the same, and overflowing seconds roll over.
        assertEquals(90, parseClockDigits("1:30"))
        assertEquals(90, parseClockDigits("90"))
        assertNull(parseClockDigits(""))
        assertNull(parseClockDigits("123456"))
    }

    @Test
    fun clockDigitsRoundTrip() {
        for (seconds in listOf(0, 5, 45, 60, 90, 300, 3_599)) {
            assertEquals(seconds, parseClockDigits(clockDigits(seconds)))
        }
        assertEquals("130", clockDigits(90))
    }

    @Test
    fun clockDigitsShowAsTheyAreTyped() {
        assertEquals("0:09", clockDigitsText("9"))
        assertEquals("1:30", clockDigitsText("130"))
        assertEquals("0:90", clockDigitsText("90"))
        assertEquals("", clockDigitsText(""))
    }
}
