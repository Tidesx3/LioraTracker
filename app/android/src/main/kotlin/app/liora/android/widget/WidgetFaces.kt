package app.liora.android.widget

import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.liora.android.R
import kotlin.time.Instant

// The widget's three faces. Text comes from the app's own resources: Glance runs outside Compose
// Multiplatform's resources.

/**
 * The workout as it runs: its name, its clock and the sets done. While resting, the clock counts the rest
 * down in the accent, marked "Rest" underneath.
 */
@Composable
internal fun TrainingFace(
    state: WidgetState.Training,
    now: Instant,
    wide: Boolean,
) {
    val context = LocalContext.current
    val name = state.name ?: context.getString(R.string.notif_workout_default_title)
    val rest = state.restEndsAt
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (wide) Text(name, style = titleStyle(), maxLines = 1)
        // In a row of its own: straight in the column, the clock would take all the height left.
        Row {
            if (rest != null) {
                Clock(until = rest, now = now, small = !wide)
            } else {
                Clock(since = state.startedAt, now = now, small = !wide)
            }
        }
        // Labels only beside each other: a clock in the same row would be aligned to their baseline and cut off.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (rest != null) {
                Text(context.getString(R.string.widget_rest), style = labelStyle(accent = true), maxLines = 1)
                if (wide) Spacer(GlanceModifier.width(8.dp))
            }
            if (wide) {
                val sets =
                    context.resources.getQuantityString(
                        R.plurals.widget_sets_done,
                        state.setsDone,
                        state.setsDone,
                    )
                Text(sets, style = labelStyle(), maxLines = 1)
            }
        }
    }
}

/** The routine up next in a rotation, to start in a tap. */
@Composable
internal fun NextUpFace(
    state: WidgetState.NextUp,
    wide: Boolean,
) {
    val context = LocalContext.current
    val start = actionStartActivity(WidgetIntents.startRoutine(context, state.routineId))
    if (!wide) {
        SmallFace(state.routineName, context.getString(R.string.widget_start), start)
        return
    }
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            context.getString(R.string.widget_next_up, state.folderName),
            style = labelStyle(accent = true),
            maxLines = 1,
        )
        Text(state.routineName, style = titleStyle(), maxLines = 1)
        Spacer(GlanceModifier.height(8.dp))
        StartRow(context.getString(R.string.widget_start), start, state.streakWeeks)
    }
}

/** No rotation: an empty workout to start, logged as it goes. */
@Composable
internal fun QuickStartFace(
    state: WidgetState.QuickStart,
    wide: Boolean,
) {
    val context = LocalContext.current
    val start = actionStartActivity(WidgetIntents.startEmpty(context))
    if (!wide) {
        SmallFace(context.getString(R.string.widget_quick_start), context.getString(R.string.widget_start_empty), start)
        return
    }
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(context.getString(R.string.widget_quick_start), style = titleStyle(), maxLines = 1)
        Text(context.getString(R.string.widget_quick_start_body), style = labelStyle(), maxLines = 1)
        Spacer(GlanceModifier.height(8.dp))
        StartRow(context.getString(R.string.widget_start_empty), start, state.streakWeeks)
    }
}

/** Two cells wide: the line, and a round play button that does what Start would. */
@Composable
private fun SmallFace(
    title: String,
    description: String,
    start: androidx.glance.action.Action,
) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = titleStyle(small = true), maxLines = 2, modifier = GlanceModifier.defaultWeight())
        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.ic_widget_play),
            contentDescription = description,
            onClick = start,
            backgroundColor = GlanceTheme.colors.primary,
            contentColor = GlanceTheme.colors.onPrimary,
        )
    }
}

/** The start button, and beside it the weekly streak when there is one. */
@Composable
private fun StartRow(
    label: String,
    start: androidx.glance.action.Action,
    streakWeeks: Int,
) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilledButton(text = label, onClick = start, icon = ImageProvider(R.drawable.ic_widget_play), maxLines = 1)
        if (streakWeeks > 0) {
            Spacer(GlanceModifier.width(12.dp))
            Text(
                context.resources.getQuantityString(R.plurals.widget_streak, streakWeeks, streakWeeks),
                style = labelStyle(),
                maxLines = 2,
            )
        }
    }
}

/**
 * A clock that ticks by itself: up from [since], or down to [until] in the accent. The widget is only
 * redrawn when what it shows changes, so a `Chronometer` (through [AndroidRemoteViews]) keeps the time.
 */
@Composable
private fun Clock(
    now: Instant,
    small: Boolean,
    since: Instant? = null,
    until: Instant? = null,
) {
    val context = LocalContext.current
    val countdown = until != null
    val layout = if (countdown) R.layout.widget_rest_clock else R.layout.widget_clock
    val id = if (countdown) R.id.widget_rest_clock else R.id.widget_clock
    // The Chronometer counts on the elapsed-realtime clock: the moment it reads 0:00 on it.
    val offset = if (until != null) (until - now).inWholeMilliseconds else -(now - (since ?: now)).inWholeMilliseconds
    val views =
        RemoteViews(context.packageName, layout).apply {
            setChronometer(id, SystemClock.elapsedRealtime() + offset, null, true)
            setChronometerCountDown(id, countdown)
            if (small) setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, SMALL_CLOCK_SP)
        }
    AndroidRemoteViews(views)
}

@Composable
private fun titleStyle(small: Boolean = false) =
    TextStyle(
        color = GlanceTheme.colors.onSurface,
        fontSize = if (small) 16.sp else 20.sp,
        fontWeight = FontWeight.Bold,
    )

@Composable
private fun labelStyle(accent: Boolean = false) =
    TextStyle(
        color = if (accent) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = if (accent) FontWeight.Medium else FontWeight.Normal,
    )

private const val SMALL_CLOCK_SP = 20f
