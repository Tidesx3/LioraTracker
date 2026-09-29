package app.liora.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.liora.android.ui.LioraApp
import app.liora.android.workout.WorkoutNotifications
import app.liora.core.designsystem.theme.LioraTheme

class MainActivity : ComponentActivity() {
    /** Set when the workout notification was tapped; the app shell then opens the logger. */
    private var openLogger by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After a recreation the intent was already handled.
        if (savedInstanceState == null) openLogger = intent.opensLogger()
        setContent {
            LioraTheme {
                LioraApp(openLogger = openLogger, onOpenLoggerHandle = { openLogger = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.opensLogger()) openLogger = true
    }

    private fun Intent?.opensLogger() = this?.action == WorkoutNotifications.ACTION_OPEN_LOGGER
}
