package app.liora.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** How asking for access to the user's Google Drive went. */
sealed interface CloudConnection {
    /** Access granted for [account], by its email address. */
    data class Connected(
        val account: String,
    ) : CloudConnection

    /** The user said no, or closed the consent screen. */
    data object Declined : CloudConnection

    /** Google couldn't be asked, e.g. without a network. */
    data object Failed : CloudConnection
}

/** Asks the user for access to their Google Drive with Google's own consent screen, for sync. */
fun interface CloudConsent {
    suspend fun connect(): CloudConnection
}

/** The app provides it (`MainActivity`), since the consent screen needs the activity. */
val LocalCloudConsent = staticCompositionLocalOf { CloudConsent { CloudConnection.Failed } }
