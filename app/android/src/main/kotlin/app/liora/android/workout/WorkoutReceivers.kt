package app.liora.android.workout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.RestSoundPlayer
import app.liora.core.data.workout.RestTimerRepository
import app.liora.core.data.workout.SetLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.time.Duration.Companion.seconds

/**
 * The workout notification's buttons: log the set that's up next, take 15 seconds off the rest or add
 * them (with a click to feel it), or skip the rest. Works from the lock screen, without opening the app;
 * the logger shows the change next time.
 */
class WorkoutActionReceiver :
    BroadcastReceiver(),
    KoinComponent {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        // Right away, before the write: the click says the tap landed.
        if (action == WorkoutNotifications.ACTION_ADD_REST || action == WorkoutNotifications.ACTION_REMOVE_REST) {
            WorkoutHaptics.restAdjusted(context)
        }
        val pending = goAsync()
        receiverScope.launch {
            try {
                when (action) {
                    WorkoutNotifications.ACTION_COMPLETE_SET -> get<SetLogger>().completeCurrentSet()
                    WorkoutNotifications.ACTION_ADD_REST -> get<RestTimerRepository>().adjust(REST_STEP)
                    WorkoutNotifications.ACTION_REMOVE_REST -> get<RestTimerRepository>().adjust(-REST_STEP)
                    WorkoutNotifications.ACTION_SKIP_REST -> get<RestTimerRepository>().stop()
                }
                get<WorkoutNotifier>().refresh()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val REST_STEP = 15.seconds
    }
}

/**
 * Fires when a rest ends: the sound chosen in Settings, the heads-up that vibrates, and the notification
 * back to the next set. Whether the logger is open or the phone is locked, this is the one place it ends.
 */
class RestAlarmReceiver :
    BroadcastReceiver(),
    KoinComponent {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val endsAt = intent.getLongExtra(WorkoutNotifications.EXTRA_ENDS_AT, -1)
        val pending = goAsync()
        receiverScope.launch {
            try {
                // The receiver holds on until the sound has played, too.
                coroutineScope {
                    val timers = get<RestTimerRepository>()
                    // A rest that was extended or restarted since has an alarm of its own.
                    if (timers.current()?.endsAt?.toEpochMilliseconds() == endsAt) {
                        timers.stop()
                        // Alongside the heads-up, so the sound and the buzz land together.
                        launch { get<RestSoundPlayer>().play(get<SettingsRepository>().current().restSound) }
                        get<WorkoutNotifier>().alertRestOver()
                    }
                    get<WorkoutNotifier>().refresh()
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** After a reboot or an app update, a workout in progress gets its notification and rest alarm back. */
class WorkoutRestoreReceiver :
    BroadcastReceiver(),
    KoinComponent {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                get<WorkoutNotifier>().refresh()
                get<RestAlarmScheduler>().refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
