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
import androidx.lifecycle.lifecycleScope
import app.liora.android.di.ProcessScope
import app.liora.android.health.HealthConnectScreensFactory
import app.liora.android.importing.SharedRoutines
import app.liora.android.sync.CloudConsentFactory
import app.liora.android.ui.LioraApp
import app.liora.android.widget.WidgetIntents
import app.liora.android.workout.WorkoutNotifications
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.walkthrough.WalkthroughRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.domain.ThemeMode
import app.liora.core.ui.CloudConsent
import app.liora.core.ui.HealthConnectScreens
import app.liora.core.ui.LocalCloudConsent
import app.liora.core.ui.LocalHealthConnectScreens
import app.liora.core.ui.LocalUnits
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    /** Set when the workout notification was tapped; the app shell then opens the logger. */
    private var openLogger by mutableStateOf(false)

    /** Set when routines were shared to Liora or opened with it: where they are now, for the import screen. */
    private var openRoutineImport by mutableStateOf<String?>(null)

    private val settingsRepository: SettingsRepository by inject()

    private val walkthroughRepository: WalkthroughRepository by inject()

    /** Google's consent screen for sync; it registers for its result, so it's made before the activity starts. */
    private lateinit var cloudConsent: CloudConsent

    /** Health Connect's permission screen and settings; made before the activity starts, like [cloudConsent]. */
    private lateinit var healthConnectScreens: HealthConnectScreens

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        cloudConsent = get<CloudConsentFactory>().create(this)
        healthConnectScreens = get<HealthConnectScreensFactory>().create(this)
        // After a recreation the intent was already handled.
        if (savedInstanceState == null) handle(intent)
        // Read once up front, so a chosen theme and units show from the first frame.
        val initial = runBlocking { settingsRepository.current() }
        // And whether this is the first start, so the first frame is the walkthrough or the tabs, never both.
        val walkthroughFirst = runBlocking { walkthroughRepository.settle() }
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initial)
            val walkthrough by walkthroughRepository.pending.collectAsStateWithLifecycle(walkthroughFirst)
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
            LioraTheme(
                darkTheme = dark,
                dynamicColor = settings.dynamicColor,
                glass = settings.glass,
            ) {
                CompositionLocalProvider(
                    LocalUnits provides settings.units,
                    LocalCloudConsent provides cloudConsent,
                    LocalHealthConnectScreens provides healthConnectScreens,
                ) {
                    LioraApp(
                        showWalkthrough = walkthrough,
                        openLogger = openLogger,
                        onOpenLoggerHandle = { openLogger = false },
                        openRoutineImport = openRoutineImport,
                        onOpenRoutineImportHandle = { openRoutineImport = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /**
     * What the workout notification and the widget ask for: the logger, or a workout started first. And
     * routines shared to Liora or opened with it, to import.
     */
    private fun handle(intent: Intent?) {
        val shared = get<SharedRoutines>()
        if (intent != null && shared.isFor(intent)) {
            // Read at once, while Liora may still read what it was handed.
            lifecycleScope.launch { shared.receive(intent)?.let { openRoutineImport = it } }
            return
        }
        when (intent?.action) {
            WorkoutNotifications.ACTION_OPEN_LOGGER -> {
                openLogger = true
            }

            WidgetIntents.ACTION_START_ROUTINE -> {
                val routineId = intent.getStringExtra(WidgetIntents.EXTRA_ROUTINE_ID) ?: return
                startThenOpenLogger { startFromRoutine(routineId) }
            }

            WidgetIntents.ACTION_START_EMPTY -> {
                startThenOpenLogger { startEmptyWorkout() }
            }
        }
    }

    /**
     * Starts a workout in the process's scope, so it's written even if the activity goes meanwhile, then
     * opens the logger. A workout already running opens instead; a routine deleted meanwhile opens nothing.
     */
    private fun startThenOpenLogger(start: suspend ActiveWorkoutRepository.() -> Unit) {
        val workouts = get<ActiveWorkoutRepository>()
        val started = get<ProcessScope>().async { runCatching { workouts.start() } }
        lifecycleScope.launch { if (started.await().isSuccess) openLogger = true }
    }
}
