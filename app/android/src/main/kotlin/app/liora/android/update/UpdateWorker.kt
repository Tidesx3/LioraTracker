package app.liora.android.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.liora.core.data.update.UpdateUnreachableException
import app.liora.core.domain.update.AppVersion
import app.liora.core.domain.update.UpdateProblem
import app.liora.integration.github.GitHubReleases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.MessageDigest

/**
 * Downloads one release's APK and checks it: the size and SHA-256 GitHub gave, then that it's Liora at a
 * higher version, signed with the same key ([ApkCheck]). Only a download that passes is kept for
 * [AppUpdater.install].
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val releases: GitHubReleases by inject()

    override suspend fun doWork(): Result {
        val version = inputData.getString(VERSION)?.let(AppVersion::parse) ?: return failed(UpdateProblem.NotGenuine)
        val url = inputData.getString(URL) ?: return failed(UpdateProblem.NotGenuine)
        val size = inputData.getLong(SIZE, -1)
        val sha256 = inputData.getString(SHA256)
        val apk = AppUpdater.apkFile(applicationContext, version)
        val part = apk.resolveSibling(apk.name + ".part")
        return withContext(Dispatchers.IO) {
            apk.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            var reported = -1
            try {
                part.outputStream().use { output ->
                    releases.download(
                        url,
                        write = { bytes, count ->
                            output.write(bytes, 0, count)
                            digest.update(bytes, 0, count)
                        },
                        onProgress = { soFar ->
                            // A step for each percent: WorkManager writes every update to its database.
                            val percent = if (size > 0) (soFar * PERCENT / size).toInt() else -1
                            if (percent != reported) {
                                reported = percent
                                setProgress(
                                    workDataOf(FRACTION to if (percent >= 0) percent / PERCENT.toFloat() else -1f),
                                )
                            }
                        },
                    )
                }
            } catch (_: UpdateUnreachableException) {
                part.delete()
                return@withContext failed(UpdateProblem.Network)
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            val genuine =
                (size < 0 || part.length() == size) &&
                    (sha256 == null || sha256 == hex) &&
                    ApkCheck.isGenuineUpdate(applicationContext, part)
            if (!genuine || !part.renameTo(apk)) {
                part.delete()
                return@withContext failed(UpdateProblem.NotGenuine)
            }
            Result.success()
        }
    }

    private fun failed(problem: UpdateProblem) = Result.failure(workDataOf(PROBLEM to problem.name))

    internal companion object {
        const val VERSION = "version"
        const val URL = "url"
        const val SIZE = "size"
        const val SHA256 = "sha256"
        const val FRACTION = "fraction"
        const val PROBLEM = "problem"
        private const val PERCENT = 100L
    }
}
