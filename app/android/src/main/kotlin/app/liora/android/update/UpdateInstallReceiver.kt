package app.liora.android.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import app.liora.core.domain.update.AppVersion
import app.liora.core.domain.update.UpdateProblem
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android's answers about an update handed to it ([AppUpdater.install]): show its confirmation, which
 * opens over Liora since the user just tapped Update; or it was turned down or failed. When it works,
 * Android replaces Liora, which restarts on its next start.
 */
class UpdateInstallReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val updater: AppUpdater by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_STATUS) return
        val version = intent.getStringExtra(EXTRA_VERSION)?.let(AppVersion::parse) ?: return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat
                    .getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Unit
            }

            // The user said no: it's still downloaded, ready for Install.
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                updater.installEnded(version, problem = null)
            }

            else -> {
                updater.installEnded(version, UpdateProblem.NotInstalled)
            }
        }
    }

    internal companion object {
        const val ACTION_STATUS = "app.liora.action.UPDATE_STATUS"
        const val EXTRA_VERSION = "version"
    }
}
