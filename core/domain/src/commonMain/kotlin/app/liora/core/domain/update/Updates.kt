package app.liora.core.domain.update

import kotlin.time.Instant

/** A Liora version, major.minor.patch: how releases are tagged ("v0.4.0") and the app names itself. */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /** "0.4.0", a tag ("v0.4.0") or a build's name ("0.4.0-debug"); null for anything else. */
        fun parse(text: String): AppVersion? =
            PATTERN
                .matchEntire(text.trim())
                ?.groupValues
                ?.drop(1)
                // A number too big for an Int isn't a version Liora makes.
                ?.mapNotNull(String::toIntOrNull)
                ?.takeIf { it.size == PARTS }
                ?.let { (major, minor, patch) -> AppVersion(major, minor, patch) }

        private const val PARTS = 3

        private val PATTERN = Regex("""v?(\d+)\.(\d+)\.(\d+)(?:-[\w.]+)?""")
    }
}

/** A release on GitHub: its version, what's new, and the APK that installs it. */
data class Release(
    val version: AppVersion,
    /** What's new as the release tells it, in Markdown with a section per language (see [ReleaseNotes]). */
    val notes: String,
    val apk: ReleaseApk,
)

/** The APK attached to a release. */
data class ReleaseApk(
    val url: String,
    val sizeBytes: Long,
    /** Its SHA-256 in hex, as GitHub works it out; null when GitHub gave none. */
    val sha256: String?,
)

/** Why an update didn't go through. */
enum class UpdateProblem {
    /** The download broke off, or GitHub was out of reach: try again later. */
    Network,

    /** The download isn't what the release said, or isn't signed like Liora: it's never installed. */
    NotGenuine,

    /** Android didn't install it, or the user said no. */
    NotInstalled,
}

/** Where updates stand, as Settings and the Train tab show them. */
sealed interface UpdateState {
    /** This build doesn't update itself: only release builds do. */
    data object Off : UpdateState

    /** Nothing newer as of [checkedAt]; null before the first check. */
    data class UpToDate(
        val checkedAt: Instant?,
    ) : UpdateState

    data object Checking : UpdateState

    /** GitHub was out of reach at the last try; [checkedAt] is the last time it wasn't. */
    data class Unreachable(
        val checkedAt: Instant?,
    ) : UpdateState

    /**
     * [release] is newer than what's installed. [dismissed]: Not now was tapped on it, so only Settings
     * offers it. [duringWorkout]: a workout is running, and the update waits until it's finished.
     */
    data class Available(
        val release: Release,
        val dismissed: Boolean,
        val duringWorkout: Boolean,
    ) : UpdateState

    /** Downloading [release]; [fraction] done, null while its size is unknown. */
    data class Downloading(
        val release: Release,
        val fraction: Float?,
    ) : UpdateState

    /** Downloaded and checked, waiting to be installed. */
    data class ReadyToInstall(
        val release: Release,
        val duringWorkout: Boolean,
    ) : UpdateState

    /** Handed to Android, which installs it and restarts Liora. */
    data class Installing(
        val release: Release,
    ) : UpdateState

    data class Failed(
        val release: Release,
        val problem: UpdateProblem,
    ) : UpdateState
}

/** The release an update state is about, if any. */
val UpdateState.release: Release?
    get() =
        when (this) {
            is UpdateState.Available -> release
            is UpdateState.Downloading -> release
            is UpdateState.ReadyToInstall -> release
            is UpdateState.Installing -> release
            is UpdateState.Failed -> release
            else -> null
        }
