package app.liora.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.android.ui.LioraApp
import app.liora.android.workout.WorkoutNotifications
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.domain.ThemeMode
import app.liora.core.ui.LocalUnits
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    /** Set when the workout notification was tapped; the app shell then opens the logger. */
    private var openLogger by mutableStateOf(false)

    private val settingsRepository: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After a recreation the intent was already handled.
        if (savedInstanceState == null) openLogger = intent.opensLogger()
        // Read once up front, so a chosen theme and units show from the first frame.
        val initial = runBlocking { settingsRepository.current() }
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initial)
            val dark =
                when (settings.theme) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            // The system bars' icons follow the app's theme, which can differ from the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            LioraTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(LocalUnits provides settings.units) {
                    LioraApp(openLogger = openLogger, onOpenLoggerHandle = { openLogger = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.opensLogger()) openLogger = true
    }

    private fun Intent?.opensLogger() = this?.action == WorkoutNotifications.ACTION_OPEN_LOGGER
}
