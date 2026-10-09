package app.liora.android.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Keeps the home-screen widget in step: when what it shows changes (a workout starts or ends, a set is
 * ticked off, a rest starts or runs out, a rotation moves on), it redraws, starting the widget's Glance
 * session again if it has ended. A running session follows the changes itself. The clocks tick on their
 * own, so nothing redraws on a timer.
 */
class WidgetUpdater(
    private val context: Context,
    private val states: WidgetStates,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            // The first state too: the process may have ended meanwhile, and the end-of-rest alarm, say,
            // starts it again just as the rest runs out.
            states.changes.collect { LioraWidget().updateAll(context) }
        }
    }
}
