package app.liora.integration.github

import app.liora.core.data.update.ReleaseSource
import app.liora.core.data.update.UpdateUnreachableException
import app.liora.core.domain.update.AppVersion
import app.liora.core.domain.update.Release
import app.liora.core.domain.update.ReleaseApk
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Liora's releases on GitHub, in [repo] ("owner/name"): the latest one through GitHub's REST API, which
 * needs no token for a public repo (60 requests an hour), and its APK.
 */
class GitHubReleases(
    private val http: HttpClient,
    private val repo: String,
) : ReleaseSource {
    /** The latest release with an APK attached; `releases/latest` already leaves out drafts and pre-releases. */
    override suspend fun latest(): Release? = fetchLatest()?.toRelease()

    /** GitHub's answer for the latest release, or null when there's none yet. */
    private suspend fun fetchLatest(): ReleaseJson? {
        val response =
            reach {
                http.get("$API/repos/$repo/releases/latest") {
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                    header(API_VERSION_HEADER, API_VERSION)
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }
            }
        // No release yet.
        if (response.status == HttpStatusCode.NotFound) return null
        // Rate-limited, or GitHub having trouble: later.
        if (!response.status.isSuccess()) throw UpdateUnreachableException("GitHub said ${response.status}")
        return try {
            json.decodeFromString<ReleaseJson>(reach { response.bodyAsText() })
        } catch (e: SerializationException) {
            throw UpdateUnreachableException("GitHub's answer didn't read as a release", e)
        }
    }

    /** A stable release with a version Liora reads and an APK to install, or null. */
    private fun ReleaseJson.toRelease(): Release? {
        val stable = !draft && !prerelease
        val version = AppVersion.parse(tagName)?.takeIf { stable }
        val apk = assets.firstOrNull { it.name.endsWith(".apk") }
        if (version == null || apk == null) return null
        val sha256 =
            apk.digest
                ?.takeIf { it.startsWith(SHA256) }
                ?.removePrefix(SHA256)
                ?.lowercase()
        return Release(version, body.orEmpty(), ReleaseApk(apk.browserDownloadUrl, apk.size, sha256))
    }

    /**
     * Downloads the file at [url] (a release's APK) piece by piece into [write], telling [onProgress] how many
     * bytes have come so far.
     *
     * @throws UpdateUnreachableException when the download can't be had or breaks off.
     */
    suspend fun download(
        url: String,
        write: (bytes: ByteArray, count: Int) -> Unit,
        onProgress: suspend (bytesSoFar: Long) -> Unit,
    ) = reach {
        http.prepareGet(url) { header(HttpHeaders.UserAgent, USER_AGENT) }.execute { response ->
            if (!response.status.isSuccess()) throw UpdateUnreachableException("The download said ${response.status}")
            val body = response.bodyAsChannel()
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0L
            while (true) {
                val count = body.readAvailable(buffer, 0, buffer.size)
                if (count == -1) break
                if (count > 0) {
                    write(buffer, count)
                    total += count
                    onProgress(total)
                }
            }
        }
    }

    /** Runs a request, a network failure meaning GitHub is out of reach for now. */
    private inline fun <T> reach(request: () -> T): T =
        try {
            request()
        } catch (e: IOException) {
            throw UpdateUnreachableException("GitHub is out of reach", e)
        }

    @Serializable
    private data class ReleaseJson(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<AssetJson> = emptyList(),
    )

    @Serializable
    private data class AssetJson(
        val name: String,
        val size: Long,
        @SerialName("browser_download_url") val browserDownloadUrl: String,
        /** "sha256:…", worked out by GitHub. */
        val digest: String? = null,
    )

    private companion object {
        const val API = "https://api.github.com"
        const val API_VERSION_HEADER = "X-GitHub-Api-Version"
        const val API_VERSION = "2022-11-28"
        const val USER_AGENT = "Liora"
        const val SHA256 = "sha256:"
        const val BUFFER_BYTES = 64 * 1024
        val json = Json { ignoreUnknownKeys = true }
    }
}
