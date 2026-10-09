package app.liora.android.update

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.liora.core.data.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Looks for an update when Liora comes to the front; the repository decides whether one's due. */
class UpdateTriggers(
    private val updates: UpdateRepository,
) {
    /** Call on the main thread, which the process lifecycle needs. */
    fun start(scope: CoroutineScope) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    scope.launch { updates.checkIfDue() }
                }
            },
        )
    }
}
