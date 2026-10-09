package app.liora.core.data.update

import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.database.dao.LocalMetaDao
import app.liora.core.database.model.LocalMetaEntity
import app.liora.core.domain.update.AppVersion
import app.liora.core.domain.update.Release
import app.liora.core.domain.update.ReleaseApk
import app.liora.core.domain.update.UpdateState
import app.liora.core.domain.update.release
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Updates from GitHub (PLAN 4e): what's newer than this build, checked at most once a day, and its
 * download and install, which the platform does ([UpdateInstaller]). What a check found is kept per phone
 * in `local_meta`, so it survives a restart; a debug build never checks.
 */
interface UpdateRepository {
    val state: Flow<UpdateState>

    /** This build's name, e.g. "0.4.0". */
    val versionName: String

    /** Whether Liora looks for updates by itself; on unless turned off on this phone. */
    val autoCheck: Flow<Boolean>

    suspend fun setAutoCheck(on: Boolean)

    /** Checks when it's due: once a day while [autoCheck] is on, and an hour after a failed try. */
    suspend fun checkIfDue()

    /** Checks now, as Settings asks. */
    suspend fun check()

    /** Not now: the Train tab stops offering this version; Settings still does. */
    suspend fun dismiss()

    /** Downloads the newer version and installs it. Never during a workout. */
    suspend fun update()

    /** Installs the update already downloaded. Never during a workout. */
    suspend fun install()
}

internal class LocalUpdateRepository(
    private val meta: LocalMetaDao,
    private val source: ReleaseSource,
    private val installer: UpdateInstaller,
    private val workouts: ActiveWorkoutRepository,
    private val clock: Clock,
) : UpdateRepository {
    private val installed = AppVersion.parse(installer.versionName)
    private val checking = MutableStateFlow(false)
    private val unreachable = MutableStateFlow(false)
    private val training = workouts.activeWorkout.map { it != null }.distinctUntilChanged()

    override val versionName: String get() = installer.versionName

    override val state: Flow<UpdateState> =
        if (!installer.enabled) {
            flowOf(UpdateState.Off)
        } else {
            combine(
                meta.observe(KEY_RELEASE).map { stored -> stored?.let(::decode)?.takeIf(::isNewer) },
                meta.observe(KEY_CHECKED_AT).map { it?.toLongOrNull()?.let(Instant::fromEpochMilliseconds) },
                meta.observe(KEY_DISMISSED),
                combine(checking, unreachable, training, ::Triple),
                installer.progress,
            ) { newer, checkedAt, dismissed, (isChecking, isUnreachable, isTraining), progress ->
                when {
                    newer != null -> newer.withProgress(progress, dismissed, isTraining)
                    isChecking -> UpdateState.Checking
                    isUnreachable -> UpdateState.Unreachable(checkedAt)
                    else -> UpdateState.UpToDate(checkedAt)
                }
            }.distinctUntilChanged()
        }

    override val autoCheck: Flow<Boolean> = meta.observe(KEY_AUTO_CHECK).map { it != OFF }.distinctUntilChanged()

    override suspend fun setAutoCheck(on: Boolean) =
        if (on) meta.delete(KEY_AUTO_CHECK) else meta.put(LocalMetaEntity(KEY_AUTO_CHECK, OFF))

    override suspend fun checkIfDue() {
        if (!installer.enabled || !autoCheck.first()) return
        val now = clock.now()
        val checked = meta.get(KEY_CHECKED_AT)?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
        val tried = meta.get(KEY_TRIED_AT)?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
        val due = (checked == null || now - checked >= 1.days) && (tried == null || now - tried >= 1.hours)
        if (due) check()
    }

    override suspend fun check() {
        if (!installer.enabled || !checking.compareAndSet(expect = false, update = true)) return
        try {
            val now = clock.now()
            meta.put(LocalMetaEntity(KEY_TRIED_AT, now.toEpochMilliseconds().toString()))
            val latest = source.latest()
            unreachable.value = false
            meta.put(LocalMetaEntity(KEY_CHECKED_AT, now.toEpochMilliseconds().toString()))
            if (latest != null && isNewer(latest)) {
                meta.put(LocalMetaEntity(KEY_RELEASE, encode(latest)))
            } else {
                meta.delete(KEY_RELEASE)
            }
        } catch (_: UpdateUnreachableException) {
            unreachable.value = true
        } finally {
            checking.value = false
        }
    }

    override suspend fun dismiss() {
        val release = state.first().release ?: return
        meta.put(LocalMetaEntity(KEY_DISMISSED, release.version.toString()))
    }

    override suspend fun update() {
        val current = state.first()
        val release = current.release ?: return
        if (current is UpdateState.Available && current.duringWorkout) return
        if (current is UpdateState.Available || current is UpdateState.Failed) installer.start(release)
    }

    override suspend fun install() {
        val current = state.first()
        if (current is UpdateState.ReadyToInstall && !current.duringWorkout) installer.install()
    }

    private fun isNewer(release: Release) = installed == null || release.version > installed

    /** This newer release as the download and install of it stand. */
    private fun Release.withProgress(
        progress: InstallProgress,
        dismissed: String?,
        isTraining: Boolean,
    ): UpdateState =
        when {
            progress is InstallProgress.Downloading && progress.version == version -> {
                UpdateState.Downloading(this, progress.fraction)
            }

            progress is InstallProgress.Ready && progress.version == version -> {
                UpdateState.ReadyToInstall(this, isTraining)
            }

            progress is InstallProgress.Installing && progress.version == version -> {
                UpdateState.Installing(this)
            }

            progress is InstallProgress.Failed && progress.version == version -> {
                UpdateState.Failed(this, progress.problem)
            }

            else -> {
                UpdateState.Available(this, dismissed = dismissed == version.toString(), duringWorkout = isTraining)
            }
        }

    /** A release as kept in `local_meta`. */
    @Serializable
    private data class StoredRelease(
        val version: String,
        val notes: String,
        val url: String,
        val size: Long,
        val sha256: String? = null,
    )

    private fun encode(release: Release): String =
        json.encodeToString(
            StoredRelease(
                release.version.toString(),
                release.notes,
                release.apk.url,
                release.apk.sizeBytes,
                release.apk.sha256,
            ),
        )

    private fun decode(value: String): Release? =
        try {
            val stored = json.decodeFromString<StoredRelease>(value)
            AppVersion.parse(stored.version)?.let { version ->
                Release(version, stored.notes, ReleaseApk(stored.url, stored.size, stored.sha256))
            }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        // Kept in local_meta: each phone has its own build and its own updates.
        const val KEY_RELEASE = "update.release"
        const val KEY_CHECKED_AT = "update.checked_at"
        const val KEY_TRIED_AT = "update.tried_at"
        const val KEY_DISMISSED = "update.dismissed"
        const val KEY_AUTO_CHECK = "update.auto_check"
        const val OFF = "off"
        val json = Json { ignoreUnknownKeys = true }
    }
}
