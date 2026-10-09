package app.liora.integration.github

import app.liora.core.data.update.UpdateUnreachableException
import app.liora.core.domain.update.AppVersion
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Liora's latest release as GitHub's REST API answers, and its APK downloaded. Synthetic answers only. */
class GitHubReleasesTest {
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun theLatestReleaseWithItsApk() =
        runBlocking {
            val release = releases { json(RELEASE) }.latest()!!
            assertEquals(AppVersion(0, 5, 0), release.version)
            assertEquals("## English\n\n- Rotations", release.notes)
            assertEquals("https://github.com/Owner/Liora/releases/download/v0.5.0/liora-0.5.0.apk", release.apk.url)
            assertEquals(10_281_721, release.apk.sizeBytes)
            assertEquals("3c3783f1", release.apk.sha256)

            val request = requests.single()
            assertEquals("https://api.github.com/repos/Owner/Liora/releases/latest", request.url.toString())
            assertEquals("application/vnd.github+json", request.headers[HttpHeaders.Accept])
            assertEquals("Liora", request.headers[HttpHeaders.UserAgent])
        }

    @Test
    fun noReleaseYetOrNoApkMeansNothingToOffer() =
        runBlocking {
            assertNull(releases { respond("", HttpStatusCode.NotFound) }.latest())
            assertNull(releases { json(RELEASE.replace("liora-0.5.0.apk\"", "notes.txt\"")) }.latest())
            assertNull(releases { json(RELEASE.replace("\"v0.5.0\"", "\"nightly\"")) }.latest())
        }

    @Test
    fun outOfReachIsSaidSo() =
        runBlocking<Unit> {
            assertFailsWith<UpdateUnreachableException> { releases { throw IOException("offline") }.latest() }
            // Rate-limited.
            assertFailsWith<UpdateUnreachableException> { releases { respond("", HttpStatusCode.Forbidden) }.latest() }
            assertFailsWith<UpdateUnreachableException> { releases { json("<html>") }.latest() }
        }

    @Test
    fun theApkComesDownInPieces() =
        runBlocking<Unit> {
            val apk = ByteArray(200_000) { it.toByte() }
            val written = ByteArrayOutputStream()
            val progress = mutableListOf<Long>()
            releases { respond(apk, HttpStatusCode.OK) }.download(
                "https://github.com/Owner/Liora/releases/download/v0.5.0/liora-0.5.0.apk",
                write = { bytes, count -> written.write(bytes, 0, count) },
                onProgress = { progress += it },
            )
            assertContentEquals(apk, written.toByteArray())
            assertEquals(apk.size.toLong(), progress.last())
            assertEquals(progress.sorted(), progress)

            assertFailsWith<UpdateUnreachableException> {
                releases { respond("", HttpStatusCode.NotFound) }.download("https://x", { _, _ -> }, {})
            }
        }

    private fun releases(answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        GitHubReleases(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    answer(request)
                },
            ),
            "Owner/Liora",
        )

    private fun MockRequestHandleScope.json(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private companion object {
        /** GitHub's answer, trimmed to what's read, plus a field Liora ignores. */
        val RELEASE =
            """
            {
              "tag_name": "v0.5.0",
              "name": "Liora 0.5.0",
              "body": "## English\n\n- Rotations",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/Owner/Liora/releases/tag/v0.5.0",
              "assets": [
                {
                  "name": "liora-0.5.0.apk",
                  "size": 10281721,
                  "digest": "sha256:3C3783F1",
                  "browser_download_url": "https://github.com/Owner/Liora/releases/download/v0.5.0/liora-0.5.0.apk"
                }
              ]
            }
            """.trimIndent()
    }
}
