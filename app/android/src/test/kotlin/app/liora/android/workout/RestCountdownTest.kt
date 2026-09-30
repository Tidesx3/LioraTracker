package app.liora.android.workout

import app.liora.core.model.RestTimer
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The notification's "Rest 1:29" title and when it next needs to change. */
class RestCountdownTest {
    private val start = Instant.fromEpochMilliseconds(1_000_000)
    private val rest = RestTimer(startedAt = start, endsAt = start + 90.seconds)

    @Test
    fun timeLeftRoundsUpUntilTheRestIsOver() {
        assertEquals("1:30", rest.remainingClock(start))
        assertEquals("1:30", rest.remainingClock(start + 300.milliseconds))
        assertEquals("1:29", rest.remainingClock(start + 1.seconds))
        assertEquals("0:01", rest.remainingClock(start + 89_999.milliseconds))
        assertEquals("0:00", rest.remainingClock(start + 90.seconds))
        assertEquals("0:00", rest.remainingClock(start + 120.seconds))
    }

    @Test
    fun ticksJustPastTheNextSecond() {
        assertEquals(1_020.milliseconds, rest.untilNextSecond(start))
        // 89.7 s left shows 1:30 and turns to 1:29 in 0.7 s.
        assertEquals(720.milliseconds, rest.untilNextSecond(start + 300.milliseconds))
        assertEquals(1_020.milliseconds, rest.untilNextSecond(start + 90.seconds))
    }
}
