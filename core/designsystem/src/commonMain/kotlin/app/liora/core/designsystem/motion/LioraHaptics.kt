package app.liora.core.designsystem.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * The haptics Liora plays, named by what happened rather than how it feels, so the same moment always
 * feels the same. They follow the system's touch feedback setting.
 */
@Stable
class LioraHaptics internal constructor(
    private val feedback: HapticFeedback,
    private val scope: CoroutineScope,
) {
    /** A set ticked off: one crisp tick. */
    fun setLogged() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** A set that couldn't be ticked off, as a value is missing. */
    fun rejected() = feedback.performHapticFeedback(HapticFeedbackType.Reject)

    /** A personal record: a heavier double beat, after the set's own tick. */
    fun record() {
        scope.launch {
            delay(RECORD_DELAY)
            feedback.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(RECORD_BEAT)
            feedback.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
}

@Composable
fun rememberLioraHaptics(): LioraHaptics {
    val feedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    return remember(feedback, scope) { LioraHaptics(feedback, scope) }
}

/** Far enough from the set's tick that the record reads as a beat of its own. */
private val RECORD_DELAY = 120.milliseconds
private val RECORD_BEAT = 90.milliseconds
