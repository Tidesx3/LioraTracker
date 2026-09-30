package app.liora.android.workout

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.liora.android.R
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.RestTimerRepository
import app.liora.core.model.RestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * The workout's one ongoing notification, from start to finish: the next set with its values, or the
 * rest countdown with a progress bar, plus buttons to log the set, take 15 seconds off the rest or add
 * them, or skip it.
 * On Android 16 it asks to be promoted to a Live Update (status bar chip, Now Bar on the lock screen).
 *
 * It reads the same database state as the logger, so the two never disagree, and it is rebuilt from
 * that state whenever the process starts again.
 */
class WorkoutNotifier(
    private val context: Context,
    private val workouts: ActiveWorkoutRepository,
    private val restTimer: RestTimerRepository,
    private val exercises: ExerciseRepository,
    private val clock: Clock,
) {
    private val manager = NotificationManagerCompat.from(context)
    private val text = WorkoutNotificationText(context)

    private val statuses: Flow<WorkoutStatus?> =
        combine(
            workouts.activeWorkout,
            workouts.previousSets,
            exercises.observeExercises(language()).map { all -> all.associateBy { it.id } },
            restTimer.timer,
        ) { workout, previous, byId, rest -> workoutStatus(workout, previous, byId, rest, clock.now()) }
            .distinctUntilChanged()

    /** Keeps the notification in step with the workout for as long as [scope] lives. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            statuses.collectLatest { status ->
                show(status)
                // While resting, the time left and the bar move on every second. Should the app be
                // closed, the system's own countdown in the header keeps going without us.
                var rest = status?.rest
                while (status != null && rest != null) {
                    delay(rest.untilNextSecond(clock.now()))
                    rest = rest.takeUnless { it.isOver(clock.now()) }
                    show(status.copy(rest = rest))
                }
            }
        }
    }

    /** Shows the current state once, e.g. from a notification button or after a reboot. */
    suspend fun refresh() = show(statuses.first())

    /** The heads-up that rest is over, on its own channel with sound and vibration. */
    suspend fun alertRestOver() {
        val next = statuses.first()?.next
        val notification =
            NotificationCompat
                .Builder(context, WorkoutNotifications.CHANNEL_REST_OVER)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notif_rest_over_title))
                .setContentText(next?.let(text::describe))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(REST_OVER_TIMEOUT.inWholeMilliseconds)
                .setContentIntent(WorkoutNotifications.openLogger(context))
                .build()
        post(WorkoutNotifications.ID_REST_OVER, notification)
    }

    private fun show(status: WorkoutStatus?) {
        if (status == null) {
            manager.cancel(WorkoutNotifications.ID_WORKOUT)
            manager.cancel(WorkoutNotifications.ID_REST_OVER)
        } else {
            post(WorkoutNotifications.ID_WORKOUT, build(status))
        }
    }

    private fun post(
        id: Int,
        notification: Notification,
    ) {
        // Without the permission (Android 13+, denied or not asked yet) there is simply no notification.
        val allowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (allowed) manager.notify(id, notification)
    }

    private fun build(status: WorkoutStatus): Notification {
        val next = status.next
        val builder =
            NotificationCompat
                .Builder(context, WorkoutNotifications.CHANNEL_WORKOUT)
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                .setContentIntent(WorkoutNotifications.openLogger(context))
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setRequestPromotedOngoing(true)
        val rest = status.rest
        if (rest != null) {
            builder
                .setContentTitle(context.getString(R.string.notif_rest_remaining, rest.remainingClock(clock.now())))
                .setContentText(next?.let { context.getString(R.string.notif_next, text.describe(it)) })
                .setWhen(rest.endsAt.toEpochMilliseconds())
                .setChronometerCountDown(true)
                .setStyle(restProgress(rest))
                .addAction(
                    0,
                    context.getString(R.string.notif_action_remove_rest),
                    action(WorkoutNotifications.ACTION_REMOVE_REST),
                ).addAction(
                    0,
                    context.getString(R.string.notif_action_add_rest),
                    action(WorkoutNotifications.ACTION_ADD_REST),
                ).addAction(
                    0,
                    context.getString(R.string.notif_action_skip_rest),
                    action(WorkoutNotifications.ACTION_SKIP_REST),
                )
        } else {
            builder
                .setContentTitle(status.name ?: context.getString(R.string.notif_workout_default_title))
                .setContentText(next?.let(text::describe) ?: context.getString(R.string.notif_all_done))
                .setWhen(status.startedAt.toEpochMilliseconds())
                .setChronometerCountDown(false)
            if (next != null) {
                builder.setShortCriticalText(context.getString(R.string.notif_set_short, next.setNumber, next.setCount))
                if (next.canLog) {
                    builder.addAction(
                        0,
                        context.getString(R.string.notif_action_log_set),
                        action(WorkoutNotifications.ACTION_COMPLETE_SET),
                    )
                }
            }
        }
        return builder.build()
    }

    /** How far the rest has run, as a bar the system shows under the countdown. */
    private fun restProgress(rest: RestTimer): NotificationCompat.ProgressStyle {
        val total =
            rest.total.inWholeSeconds
                .toInt()
                .coerceAtLeast(1)
        val elapsed = (clock.now() - rest.startedAt).inWholeSeconds.toInt().coerceIn(0, total)
        return NotificationCompat
            .ProgressStyle()
            .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(total)))
            .setProgress(elapsed)
    }

    private fun action(name: String) = WorkoutNotifications.action(context, name)

    private fun language(): String =
        context.resources.configuration.locales[0]
            .language

    private companion object {
        val REST_OVER_TIMEOUT = 30.seconds
    }
}
