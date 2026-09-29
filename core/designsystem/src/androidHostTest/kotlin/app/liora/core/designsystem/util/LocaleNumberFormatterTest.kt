package app.liora.core.designsystem.util

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleNumberFormatterTest {
    private val german = LocaleNumberFormatter(Locale.GERMAN)
    private val english = LocaleNumberFormatter(Locale.ENGLISH)

    @Test
    fun usesTheLocaleDecimalSeparator() {
        assertEquals("82,5", german.format(82.5))
        assertEquals("82.5", english.format(82.5))
    }

    @Test
    fun dropsTrailingZerosAndRounds() {
        assertEquals("100", german.format(100.0))
        assertEquals("2,25", german.format(2.25))
        assertEquals("20,41", german.format(20.41165665))
    }

    @Test
    fun groupsThousands() {
        assertEquals("12.345", german.format(12_345))
        assertEquals("12,345", english.format(12_345))
    }
}
