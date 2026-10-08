package app.liora.sync.gdrive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import app.liora.core.data.sync.SyncBackend
import app.liora.core.data.sync.SyncSignInNeededException
import app.liora.core.data.sync.SyncStores
import app.liora.core.data.sync.SyncUnreachableException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.tasks.await

/**
 * Liora's access to the user's Drive app data folder, through Google Play services. The app holds no
 * client id: Google knows it by its package name and signing key (see PLAN.md, Phase 3).
 */
class GoogleDriveAccess(
    context: Context,
) : SyncStores {
    private val client = Identity.getAuthorizationClient(context.applicationContext)
    private val http = HttpClient(OkHttp)

    /**
     * Asks Google for access to the app data folder, showing its consent screen through [consent] when the
     * user hasn't granted it yet; [consent] returns the screen's result, or null when it was cancelled.
     * Returns the account's email, or null when the user said no. Throws [DriveSignInException] when
     * Google can't be asked.
     */
    suspend fun signIn(consent: suspend (IntentSender) -> Intent?): String? =
        try {
            val result = client.authorize(request(account = null)).await()
            val granted =
                if (result.hasResolution()) {
                    val data = consent(checkNotNull(result.pendingIntent).intentSender) ?: return null
                    client.getAuthorizationResultFromIntent(data)
                } else {
                    result
                }
            accountOf(granted)
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) null else throw DriveSignInException(e)
        } catch (e: SyncUnreachableException) {
            throw DriveSignInException(e)
        }

    override fun open(account: String): SyncBackend = DriveSyncBackend(RestAppDataFolder(http, Tokens(account)))

    override suspend fun forget(account: String) {
        try {
            val request =
                RevokeAccessRequest
                    .builder()
                    .setAccount(Account(account, ACCOUNT_TYPE))
                    .setScopes(listOf(Scope(DRIVE_APP_DATA)))
                    .build()
            client.revokeAccess(request).await()
        } catch (e: ApiException) {
            throw SyncUnreachableException("Access couldn't be given back", e)
        }
    }

    /** The account access was granted for, by its email; null when the user unticked Drive. */
    private suspend fun accountOf(result: AuthorizationResult): String? {
        if (DRIVE_APP_DATA !in result.grantedScopes) return null
        val token = result.accessToken ?: return null
        return RestAppDataFolder(http, FixedToken(token)).accountEmail()
    }

    private fun request(account: String?): AuthorizationRequest =
        AuthorizationRequest
            .builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APP_DATA)))
            .apply { if (account != null) setAccount(Account(account, ACCOUNT_TYPE)) }
            .build()

    /** Tokens for background syncs, which can't show a consent screen. */
    private inner class Tokens(
        private val account: String,
    ) : AccessTokens {
        override suspend fun token(): String {
            val result =
                try {
                    client.authorize(request(account)).await()
                } catch (e: ApiException) {
                    throw when (e.statusCode) {
                        CommonStatusCodes.SIGN_IN_REQUIRED -> SyncSignInNeededException(e)
                        else -> SyncUnreachableException("Google can't be asked for access", e)
                    }
                }
            if (result.hasResolution()) throw SyncSignInNeededException()
            return result.accessToken ?: throw SyncSignInNeededException()
        }

        override suspend fun refresh(stale: String): String {
            try {
                client.clearToken(ClearTokenRequest.builder().setToken(stale).build()).await()
            } catch (e: ApiException) {
                throw SyncUnreachableException("The old token couldn't be cleared", e)
            }
            return token()
        }
    }

    /** The token just granted, to look up whose Drive it opens. */
    private class FixedToken(
        private val token: String,
    ) : AccessTokens {
        override suspend fun token() = token

        override suspend fun refresh(stale: String): String = throw SyncSignInNeededException()
    }

    private companion object {
        const val DRIVE_APP_DATA = "https://www.googleapis.com/auth/drive.appdata"
        const val ACCOUNT_TYPE = "com.google"
    }
}

/** Google couldn't be asked for access, e.g. without a network or with Play services out of date. */
class DriveSignInException(
    cause: Throwable,
) : Exception("Signing in to Google Drive failed", cause)
