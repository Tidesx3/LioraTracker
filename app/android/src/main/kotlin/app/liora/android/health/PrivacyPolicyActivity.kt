package app.liora.android.health

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Where Health Connect sends people who want to read why Liora asks for their data: Liora's privacy
 * policy, opened in the browser.
 */
class PrivacyPolicyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY)))
        } catch (_: ActivityNotFoundException) {
            // No browser: nothing to show it in.
        }
        finish()
    }

    private companion object {
        const val PRIVACY_POLICY = "https://github.com/Tidesx3/LioraTracker/blob/main/PRIVACY.md"
    }
}
