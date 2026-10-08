package app.liora.android.health

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import app.liora.core.ui.HealthConnectScreens
import app.liora.integration.healthconnect.HealthConnectStore
import kotlinx.coroutines.CompletableDeferred

/** Makes Health Connect's screens for an activity. Tests swap in ones that answer at once. */
fun interface HealthConnectScreensFactory {
    fun create(activity: ComponentActivity): HealthConnectScreens
}

/**
 * Health Connect's screens, opened from [activity]. Create it in `onCreate`: the activity registers for
 * the permission screen's result before it starts.
 */
class HealthConnectActivityScreens(
    private val activity: ComponentActivity,
) : HealthConnectScreens {
    private var waiting: CompletableDeferred<Set<String>>? = null
    private val launcher =
        activity.registerForActivityResult(HealthConnectStore.permissionRequest()) { granted ->
            waiting?.complete(granted)
        }

    override suspend fun requestPermissions(): Boolean {
        val result = CompletableDeferred<Set<String>>().also { waiting = it }
        launcher.launch(HealthConnectStore.permissions)
        return result.await().isNotEmpty()
    }

    override fun openSettings() = start(HealthConnectStore.settingsIntent())

    override fun install() = start(HealthConnectStore.installIntent(activity))

    private fun start(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Without Health Connect or the store there's nowhere to go; the screen says what's missing.
        }
    }
}
