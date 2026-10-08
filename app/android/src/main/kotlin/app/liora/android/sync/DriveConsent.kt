package app.liora.android.sync

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import app.liora.core.ui.CloudConnection
import app.liora.core.ui.CloudConsent
import app.liora.sync.gdrive.DriveSignInException
import app.liora.sync.gdrive.GoogleDriveAccess
import kotlinx.coroutines.CompletableDeferred

/** Makes the consent screen for sync from an activity. Tests swap in one that answers at once. */
fun interface CloudConsentFactory {
    fun create(activity: ComponentActivity): CloudConsent
}

/**
 * Google's consent screen for Drive, shown from [activity]. Create it in `onCreate`: the activity
 * registers for the screen's result before it starts.
 */
class DriveConsent(
    activity: ComponentActivity,
    private val drive: GoogleDriveAccess,
) : CloudConsent {
    private var waiting: CompletableDeferred<ActivityResult>? = null
    private val launcher =
        activity.registerForActivityResult(StartIntentSenderForResult()) { result -> waiting?.complete(result) }

    override suspend fun connect(): CloudConnection =
        try {
            val account =
                drive.signIn { sender ->
                    val result = CompletableDeferred<ActivityResult>().also { waiting = it }
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                    result.await().takeIf { it.resultCode == Activity.RESULT_OK }?.data
                }
            account?.let(CloudConnection::Connected) ?: CloudConnection.Declined
        } catch (_: DriveSignInException) {
            CloudConnection.Failed
        }
}
