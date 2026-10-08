package app.liora.android.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.liora.core.data.sync.SyncRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Asks for a sync when there's likely something new to share: a workout just finished, or Liora left
 * the screen. Asking does nothing while sync is off, and the twice-daily sync catches the rest.
 */
class SyncTriggers(
    private val sync: SyncRepository,
    private val workouts: ActiveWorkoutRepository,
) {
    /** Call on the main thread, which the process lifecycle needs. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            var previous: String? = null
            workouts.activeWorkout.map { it?.id }.distinctUntilChanged().collect { id ->
                if (previous != null && id == null) sync.syncNow()
                previous = id
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    scope.launch { sync.syncNow() }
                }
            },
        )
    }
}
