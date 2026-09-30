package app.liora.android.workout

import app.liora.core.model.RestTimer
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The notification's "Rest 1:29" title and when it next needs to update. */
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
        assertEquals(1_020.milliseconds, rest.untilNextStep(start, 1.seconds))
        // 89.7 s left shows 1:30 and turns to 1:29 in 0.7 s.
        assertEquals(720.milliseconds, rest.untilNextStep(start + 300.milliseconds, 1.seconds))
        assertEquals(1_020.milliseconds, rest.untilNextStep(start + 90.seconds, 1.seconds))
    }

    @Test
    fun smallerStepsStillLandOnEverySecond() {
        // 89.7 s left: the next half second is 89.5 s, 0.2 s away.
        assertEquals(220.milliseconds, rest.untilNextStep(start + 300.milliseconds, 500.milliseconds))
        // 89.1 s left: the next half second is 89.0 s, where the title turns to 1:29.
        assertEquals(120.milliseconds, rest.untilNextStep(start + 900.milliseconds, 500.milliseconds))
    }
}
