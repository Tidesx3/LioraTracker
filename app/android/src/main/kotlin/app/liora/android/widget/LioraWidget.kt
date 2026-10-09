package app.liora.android.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.material3.ColorProviders
import app.liora.android.MainActivity
import app.liora.android.workout.WorkoutNotifications
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.designsystem.theme.LioraColorSchemes
import kotlinx.coroutines.flow.map
import org.koin.core.context.GlobalContext
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Liora on the home screen: the workout as it runs, else the routine up next in a rotation, else Quick
 * start. [WidgetUpdater] redraws it when what it shows changes; its clocks tick by themselves.
 */
class LioraWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val koin = GlobalContext.get()
        val states = koin.get<WidgetStates>()
        val settings = koin.get<SettingsRepository>()
        val clock = koin.get<Clock>()
        val firstState = states.current()
        val firstWallpaperColors = settings.current().dynamicColor
        provideContent {
            // Followed for as long as Glance keeps the widget's session; WidgetUpdater wakes it after.
            val state by states.changes.collectAsState(firstState)
            val wallpaperColors by
                remember { settings.settings.map { it.dynamicColor } }.collectAsState(firstWallpaperColors)
            // Read as each face is drawn: the clocks are set from it.
            LioraWidgetContent(state, clock.now(), wallpaperColors)
        }
    }

    companion object {
        /** Two cells wide: the main line and its button or clock. */
        val SMALL = DpSize(110.dp, 48.dp)

        /** Wide enough for a label, the line and a button, and the streak. */
        val WIDE = DpSize(250.dp, 110.dp)
    }
}

class LioraWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LioraWidget()
}

/** The widget's face for [state], as it is at [now]. */
@Composable
internal fun LioraWidgetContent(
    state: WidgetState,
    now: Instant,
    wallpaperColors: Boolean,
) {
    val dynamic = wallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    GlanceTheme(colors = if (dynamic) GlanceTheme.colors else LioraWidgetColors) {
        val context = LocalContext.current
        val wide = LocalSize.current.width >= LioraWidget.WIDE.width
        // A tap anywhere opens Liora: the logger during a workout.
        val open = if (state is WidgetState.Training) WidgetIntents.openLogger(context) else WidgetIntents.open(context)
        Scaffold(
            modifier = GlanceModifier.clickable(actionStartActivity(open)),
            backgroundColor = if (dynamic) GlanceTheme.colors.widgetBackground else LioraWidgetBackground,
            horizontalPadding = if (wide) 16.dp else 12.dp,
        ) {
            when (state) {
                is WidgetState.Training -> TrainingFace(state, now, wide)
                is WidgetState.NextUp -> NextUpFace(state, wide)
                is WidgetState.QuickStart -> QuickStartFace(state, wide)
            }
        }
    }
}

/** Liora's violet, light and dark with the phone. */
private val LioraWidgetColors = ColorProviders(light = LioraColorSchemes.light, dark = LioraColorSchemes.dark)

/** The widget sits on the home screen like one of Liora's cards: white in light, raised grey in dark. */
private val LioraWidgetBackground =
    ColorProvider(
        day = LioraColorSchemes.light.surfaceContainerLowest,
        night = LioraColorSchemes.dark.surfaceContainerHigh,
    )

/** What the widget's taps open: Liora, the logger, or a workout started from it. */
internal object WidgetIntents {
    /** Starts the routine with [EXTRA_ROUTINE_ID], then opens the logger. */
    const val ACTION_START_ROUTINE = "app.liora.action.START_ROUTINE"

    /** Starts an empty workout, then opens the logger. */
    const val ACTION_START_EMPTY = "app.liora.action.START_EMPTY"
    const val EXTRA_ROUTINE_ID = "routine_id"

    fun open(context: Context): Intent = activity(context)

    fun openLogger(context: Context): Intent = activity(context).setAction(WorkoutNotifications.ACTION_OPEN_LOGGER)

    fun startRoutine(
        context: Context,
        routineId: String,
    ): Intent = activity(context).setAction(ACTION_START_ROUTINE).putExtra(EXTRA_ROUTINE_ID, routineId)

    fun startEmpty(context: Context): Intent = activity(context).setAction(ACTION_START_EMPTY)

    private fun activity(context: Context) =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
