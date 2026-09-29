package app.liora.android.workout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.liora.android.MainActivity
import app.liora.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Ids, actions and intents shared by the workout notification, its buttons and the rest alarm.

internal object WorkoutNotifications {
    const val CHANNEL_WORKOUT = "workout"
    const val CHANNEL_REST_OVER = "rest_over"
    const val ID_WORKOUT = 1
    const val ID_REST_OVER = 2

    const val ACTION_COMPLETE_SET = "app.liora.action.COMPLETE_SET"
    const val ACTION_ADD_REST = "app.liora.action.ADD_REST"
    const val ACTION_SKIP_REST = "app.liora.action.SKIP_REST"
    const val ACTION_REST_OVER = "app.liora.action.REST_OVER"

    /** Tells [MainActivity] to show the logger. */
    const val ACTION_OPEN_LOGGER = "app.liora.action.OPEN_LOGGER"

    const val EXTRA_ENDS_AT = "ends_at"

    /**
     * The ongoing workout notification stays quiet; the end of a rest gets its own channel with sound and
     * vibration, so each can be tuned in the system settings on its own.
     */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WORKOUT,
                context.getString(R.string.notif_channel_workout),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notif_channel_workout_desc)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REST_OVER,
                context.getString(R.string.notif_channel_rest),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_rest_desc)
                enableVibration(true)
                vibrationPattern = REST_OVER_VIBRATION
            },
        )
    }

    fun openLogger(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_OPEN_LOGGER,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_LOGGER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun action(
        context: Context,
        action: String,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, WorkoutActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private const val REQUEST_OPEN_LOGGER = 1

    /** Three short buzzes: noticeable through a pocket, not mistaken for a call. */
    private val REST_OVER_VIBRATION = longArrayOf(0, 250, 150, 250, 150, 250)
}

/** Where broadcast receivers finish their work after returning (they hold the process with `goAsync`). */
internal val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
