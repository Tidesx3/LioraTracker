package app.liora.android.workout

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.liora.android.di.ProcessScope
import app.liora.core.data.workout.ActiveWorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Holds Liora in the foreground while a workout runs ([WorkoutService]), so the end of a rest can play its
 * sound with the screen off: since Android 17, an app off screen plays sound only from a foreground service
 * it started while on screen, and otherwise it's muted without a word. So it starts the service only then:
 * with the workout, or the next time Liora comes on screen when it couldn't (a workout brought back after a
 * reboot or an update). The service ends itself with the workout.
 */
class WorkoutForeground(
    private val app: Application,
    private val workouts: ActiveWorkoutRepository,
) {
    /** How many of Liora's activities are on screen (started), counted on the main thread. */
    @Volatile
    private var onScreen = 0

    /** Call on the main thread, where activities report starting and stopping. */
    fun start(scope: CoroutineScope) {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    if (onScreen++ == 0) scope.launch { if (workouts.activeWorkout.first() != null) hold() }
                }

                override fun onActivityStopped(activity: Activity) {
                    onScreen--
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
        scope.launch {
            workouts.activeWorkout.map { it != null }.distinctUntilChanged().collect { running ->
                if (running && onScreen > 0) hold()
            }
        }
    }

    private fun hold() {
        if (WorkoutService.running) return
        try {
            ContextCompat.startForegroundService(app, Intent(app, WorkoutService::class.java))
        } catch (_: IllegalStateException) {
            // Liora left the screen in the meantime: it starts the next time Liora is back.
        }
    }
}

/**
 * The foreground service a workout runs in, from [WorkoutForeground]. Its notification is the workout's own,
 * which [WorkoutNotifier] goes on posting, so it adds nothing to the shade. It stops itself once the workout
 * is over: a service started this way has to be in the foreground before it may stop, or Android ends the app.
 */
class WorkoutService :
    Service(),
    KoinComponent {
    private val scope = MainScope()
    private var watching: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val notifier = get<WorkoutNotifier>()
        ServiceCompat.startForeground(this, WorkoutNotifications.ID_WORKOUT, notifier.foregroundNotification, TYPE)
        running = true
        // What it showed may be a moment behind the workout: post what's current.
        get<ProcessScope>().launch { notifier.refresh() }
        if (watching == null) {
            watching =
                scope.launch {
                    get<ActiveWorkoutRepository>().activeWorkout.first { it == null }
                    ServiceCompat.stopForeground(this@WorkoutService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
        }
        // Should Android end the process, it starts again only off screen, where the service couldn't play
        // sound anyway; Liora starts it again when it's back on screen.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Whether it's in the foreground now, so coming back on screen doesn't start it again. */
        @Volatile
        var running = false
            private set

        private val TYPE =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
    }
}
