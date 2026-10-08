package app.liora.android.health

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.liora.core.data.health.HealthRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Trades with Health Connect while Liora is on screen, which reading from it needs: when Liora comes up,
 * and when a workout finishes. Without Health Connect or its permissions, nothing happens.
 */
class HealthTriggers(
    private val health: HealthRepository,
    private val workouts: ActiveWorkoutRepository,
) {
    /** Call on the main thread, which the process lifecycle needs. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            var previous: String? = null
            workouts.activeWorkout.map { it?.id }.distinctUntilChanged().collect { id ->
                if (previous != null && id == null) health.exchange()
                previous = id
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    scope.launch { health.exchange() }
                }
            },
        )
    }
}
