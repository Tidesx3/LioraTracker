package app.liora.android.workout

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.liora.core.data.workout.RestTimerRepository
import app.liora.core.model.RestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Wakes the phone when a rest ends, even locked and dozing, so the alert comes on time: an exact alarm
 * that follows the rest timer as it starts, changes and stops. Without exact-alarm access it falls back
 * to an inexact one, which Android may deliver a little late while the phone sleeps.
 */
class RestAlarmScheduler(
    private val context: Context,
    private val restTimer: RestTimerRepository,
    private val clock: Clock,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun start(scope: CoroutineScope) {
        scope.launch { restTimer.timer.collect(::update) }
    }

    /** Sets the alarm for the rest in progress, e.g. after a reboot cleared all alarms. */
    suspend fun refresh() = update(restTimer.current())

    private fun update(timer: RestTimer?) {
        if (timer == null || timer.isOver(clock.now())) {
            alarms.cancel(alarmIntent(endsAt = 0))
            return
        }
        val at = timer.endsAt.toEpochMilliseconds()
        val intent = alarmIntent(at)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    /** One alarm at a time: the same request code replaces (or cancels) the previous one. */
    private fun alarmIntent(endsAt: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_REST_OVER,
            Intent(context, RestAlarmReceiver::class.java)
                .setAction(WorkoutNotifications.ACTION_REST_OVER)
                .putExtra(WorkoutNotifications.EXTRA_ENDS_AT, endsAt),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private companion object {
        const val REQUEST_REST_OVER = 1
    }
}
