package app.liora.core.designsystem.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ElapsedTimeTest {
    @Test
    fun formatsLikeAStopwatch() {
        assertEquals("0:00", Duration.ZERO.formatAsClock())
        assertEquals("0:42", 42.seconds.formatAsClock())
        assertEquals("12:05", (12.minutes + 5.seconds).formatAsClock())
        assertEquals("1:02:09", (1.hours + 2.minutes + 9.seconds).formatAsClock())
    }

    @Test
    fun negativeDurationsClampToZero() {
        assertEquals("0:00", (-3).seconds.formatAsClock())
    }
}
