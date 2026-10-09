package app.liora.android.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.liora.android.BuildConfig
import app.liora.core.data.update.InstallProgress
import app.liora.core.data.update.UpdateInstaller
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.domain.update.AppVersion
import app.liora.core.domain.update.Release
import app.liora.core.domain.update.UpdateProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Downloads and installs Liora's updates (PLAN 4e): [UpdateWorker] downloads and checks a release in the
 * background; [install] hands it to Android's `PackageInstaller`, whose answers come back through
 * [UpdateInstallReceiver]. Only the release build updates itself.
 */
class AppUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val workouts: ActiveWorkoutRepository,
) : UpdateInstaller {
    private val work = WorkManager.getInstance(context)

    /** Set while Android has the update, until it answers; null otherwise. */
    private val installing = MutableStateFlow<InstallProgress?>(null)

    /** The version the user asked for in this process: installed as soon as it's ready, if Liora's in front. */
    @Volatile private var requested: AppVersion? = null

    override val enabled: Boolean = BuildConfig.UPDATES_FROM_GITHUB

    override val versionName: String = BuildConfig.VERSION_NAME

    override val progress: Flow<InstallProgress> =
        combine(work.getWorkInfosForUniqueWorkFlow(WORK), installing) { infos, handedOver ->
            handedOver ?: infos.lastOrNull()?.progress() ?: InstallProgress.Idle
        }

    override fun start(release: Release) {
        requested = release.version
        installing.value = null
        val request =
            OneTimeWorkRequestBuilder<UpdateWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(
                    workDataOf(
                        UpdateWorker.VERSION to release.version.toString(),
                        UpdateWorker.URL to release.apk.url,
                        UpdateWorker.SIZE to release.apk.sizeBytes,
                        UpdateWorker.SHA256 to release.apk.sha256,
                    ),
                ).addTag(VERSION_TAG + release.version)
                .build()
        work.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun install() {
        scope.launch {
            val ready = progress.first() as? InstallProgress.Ready ?: return@launch
            installing.value = InstallProgress.Installing(ready.version)
            try {
                withContext(Dispatchers.IO) { commit(ready.version, apkFile(context, ready.version)) }
            } catch (_: IOException) {
                installing.value = InstallProgress.Failed(ready.version, UpdateProblem.NotInstalled)
            }
        }
    }

    /**
     * Installs what the user asked for as soon as it's downloaded, while Liora is on screen (so Android can
     * ask) and no workout is running; otherwise it waits for Install. Clears out downloads already installed.
     */
    fun start() {
        scope.launch(Dispatchers.IO) { removeInstalled() }
        scope.launch {
            progress.collect { state ->
                val inFront =
                    ProcessLifecycleOwner
                        .get()
                        .lifecycle.currentState
                        .isAtLeast(Lifecycle.State.STARTED)
                if (state is InstallProgress.Ready && state.version == requested && inFront) {
                    requested = null
                    if (workouts.activeWorkout.first() == null) install()
                }
            }
        }
    }

    /** Android's answer, from [UpdateInstallReceiver]: no, or it failed. A yes restarts Liora anyway. */
    internal fun installEnded(
        version: AppVersion,
        problem: UpdateProblem?,
    ) {
        installing.value = problem?.let { InstallProgress.Failed(version, it) }
    }

    private fun commit(
        version: AppVersion,
        apk: File,
    ) {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setInstallReason(PackageManager.INSTALL_REASON_USER)
                // Once Liora has installed itself, Android lets it update without asking again.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("liora.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val status =
                Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(UpdateInstallReceiver.ACTION_STATUS)
                    .putExtra(UpdateInstallReceiver.EXTRA_VERSION, version.toString())
            // Mutable: Android fills in the result.
            val flags =
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, status, flags).intentSender)
        }
    }

    private fun removeInstalled() {
        val installed = AppVersion.parse(versionName) ?: return
        updatesDir(context).listFiles().orEmpty().forEach { file ->
            val version = AppVersion.parse(file.name.removePrefix("liora-").substringBefore(".apk"))
            if (version == null || version <= installed) file.delete()
        }
    }

    private fun WorkInfo.progress(): InstallProgress? {
        val version = tags.firstOrNull { it.startsWith(VERSION_TAG) }?.removePrefix(VERSION_TAG)?.let(AppVersion::parse)
        return version?.let {
            when (state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING -> {
                    InstallProgress.Downloading(
                        it,
                        progress.getFloat(UpdateWorker.FRACTION, -1f).takeIf { f ->
                            f >= 0f
                        },
                    )
                }

                WorkInfo.State.SUCCEEDED -> {
                    InstallProgress.Ready(it)
                }

                WorkInfo.State.FAILED -> {
                    val problem = outputData.getString(UpdateWorker.PROBLEM)?.let(UpdateProblem::valueOf)
                    InstallProgress.Failed(it, problem ?: UpdateProblem.Network)
                }

                WorkInfo.State.CANCELLED -> {
                    null
                }
            }
        }
    }

    internal companion object {
        const val WORK = "update"
        const val VERSION_TAG = "version:"

        /** Downloads live here, out of backups, until they're installed. */
        fun updatesDir(context: Context) = File(context.noBackupFilesDir, "updates")

        fun apkFile(
            context: Context,
            version: AppVersion,
        ) = File(updatesDir(context), "liora-$version.apk")
    }
}
