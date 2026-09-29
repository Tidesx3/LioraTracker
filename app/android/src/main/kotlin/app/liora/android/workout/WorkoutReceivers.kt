package app.liora.android.workout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.liora.core.data.workout.RestTimerRepository
import app.liora.core.data.workout.SetLogger
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.time.Duration.Companion.seconds

/**
 * The workout notification's buttons: log the set that's up next, add 30 seconds of rest, or skip the
 * rest. Works from the lock screen, without opening the app; the logger shows the change next time.
 */
class WorkoutActionReceiver :
    BroadcastReceiver(),
    KoinComponent {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        val pending = goAsync()
        receiverScope.launch {
            try {
                when (action) {
                    WorkoutNotifications.ACTION_COMPLETE_SET -> get<SetLogger>().completeCurrentSet()
                    WorkoutNotifications.ACTION_ADD_REST -> get<RestTimerRepository>().adjust(ADDED_REST)
                    WorkoutNotifications.ACTION_SKIP_REST -> get<RestTimerRepository>().stop()
                }
                get<WorkoutNotifier>().refresh()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ADDED_REST = 30.seconds
    }
}

/** Fires when a rest ends: the heads-up with sound and vibration, and the notification back to the next set. */
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
                val timers = get<RestTimerRepository>()
                // A rest that was extended or restarted since has an alarm of its own.
                if (timers.current()?.endsAt?.toEpochMilliseconds() == endsAt) {
                    timers.stop()
                    get<WorkoutNotifier>().alertRestOver()
                }
                get<WorkoutNotifier>().refresh()
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
