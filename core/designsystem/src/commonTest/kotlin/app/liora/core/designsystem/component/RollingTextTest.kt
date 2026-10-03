package app.liora.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which way an odometer rolls: by the number its digits make, whatever surrounds them. */
class RollingTextTest {
    @Test
    fun growingNumbersRollUp() {
        assertTrue(rollsUp("825 kg", "1,650 kg"))
        assertTrue(rollsUp("0:59", "1:00"))
        assertTrue(rollsUp("9", "10"))
        // German separators read the same.
        assertTrue(rollsUp("2.940 kg", "3.015 kg"))
    }

    @Test
    fun aCountdownRollsDown() {
        assertFalse(rollsUp("1:30", "1:29"))
        assertFalse(rollsUp("1:00", "0:59"))
        assertFalse(rollsUp("10", "9"))
    }

    @Test
    fun leadingZerosDontCount() {
        assertFalse(rollsUp("0:10", "0:09"))
        assertTrue(rollsUp("0:09", "0:10"))
    }

    @Test
    fun textWithoutDigitsRollsUp() {
        assertTrue(rollsUp("–", "0 kg"))
        assertTrue(rollsUp("12", "–"))
    }
}
