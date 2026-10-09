package app.liora.core.data.update

import app.liora.core.domain.update.AppVersion
import app.liora.core.domain.update.Release
import app.liora.core.domain.update.UpdateProblem
import kotlinx.coroutines.flow.Flow

/** Where releases are published: GitHub (`integration/github`). */
fun interface ReleaseSource {
    /**
     * The latest stable release, or null when there's none yet.
     *
     * @throws UpdateUnreachableException when it can't be reached; trying again later may work.
     */
    suspend fun latest(): Release?
}

/** The releases couldn't be reached: no network, or GitHub said no for now. */
class UpdateUnreachableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The platform side of updating: this build, and downloading and installing a release (the app). */
interface UpdateInstaller {
    /** Whether this build updates itself: only release builds, signed with the release key, do. */
    val enabled: Boolean

    /** This build's name, e.g. "0.4.0" or "0.4.0-debug". */
    val versionName: String

    /** The download and install asked for, as they go. */
    val progress: Flow<InstallProgress>

    /** Downloads [release] in the background and checks it, then installs it if Liora is still on screen. */
    fun start(release: Release)

    /** Installs the update downloaded and checked, from the screen, so Android can ask. */
    fun install()
}

/** How a download and install are going, for the version they're about. */
sealed interface InstallProgress {
    data object Idle : InstallProgress

    data class Downloading(
        val version: AppVersion,
        val fraction: Float?,
    ) : InstallProgress

    data class Ready(
        val version: AppVersion,
    ) : InstallProgress

    data class Installing(
        val version: AppVersion,
    ) : InstallProgress

    data class Failed(
        val version: AppVersion,
        val problem: UpdateProblem,
    ) : InstallProgress
}
