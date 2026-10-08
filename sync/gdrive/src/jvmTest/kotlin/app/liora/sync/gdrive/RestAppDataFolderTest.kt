package app.liora.sync.gdrive

import app.liora.core.data.sync.SyncSignInNeededException
import app.liora.core.data.sync.SyncStoreFullException
import app.liora.core.data.sync.SyncUnreachableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Drive's REST API as Liora calls it: what goes out, and what a failure means for syncing. */
class RestAppDataFolderTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val tokens = CountingTokens()

    @Test
    fun listingSearchesTheAppDataFolderPageByPage() =
        runBlocking<Unit> {
            val folder =
                folder { request ->
                    if (request.url.parameters["pageToken"] == null) {
                        json("""{"files": [{"id": "a", "name": "one"}], "nextPageToken": "p2"}""")
                    } else {
                        json("""{"files": [{"id": "b", "name": "two", "appProperties": {"kind": "blob"}}]}""")
                    }
                }

            val files = folder.list("trashed = false")

            assertEquals(listOf("a", "b"), files.map { it.id })
            assertEquals(mapOf("kind" to "blob"), files[1].properties)
            val first = requests.first()
            assertEquals("appDataFolder", first.url.parameters["spaces"])
            assertEquals("trashed = false", first.url.parameters["q"])
            assertEquals("Bearer token-1", first.headers[HttpHeaders.Authorization])
        }

    @Test
    fun aNewFileGoesUpWithItsPropertiesInTheAppDataFolder() =
        runBlocking<Unit> {
            val folder = folder { json("""{"id": "new", "name": "changes-x-1.json.gz"}""") }

            val file = folder.create("changes-x-1.json.gz", mapOf("kind" to "changes"), byteArrayOf(1, 2, 3))

            assertEquals("new", file.id)
            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("multipart", request.url.parameters["uploadType"])
            assertTrue(
                request.body.contentType
                    .toString()
                    .startsWith("multipart/related; boundary="),
            )
            val body = request.body.toByteArray().decodeToString()
            assertContains(body, """"parents":["appDataFolder"]""")
            assertContains(body, """"appProperties":{"kind":"changes"}""")
        }

    @Test
    fun aTurnedDownTokenIsReplacedOnce() =
        runBlocking<Unit> {
            val folder =
                folder { request ->
                    if (request.headers[HttpHeaders.Authorization] == "Bearer token-1") {
                        respond("", HttpStatusCode.Unauthorized)
                    } else {
                        json("""{"files": []}""")
                    }
                }

            folder.list("q")

            assertEquals(
                listOf("Bearer token-1", "Bearer token-2"),
                requests.map { it.headers[HttpHeaders.Authorization] },
            )
        }

    @Test
    fun failuresSayWhatSyncingShouldDo() =
        runBlocking<Unit> {
            suspend fun failing(
                status: HttpStatusCode,
                reason: String,
            ) = folder {
                json("""{"error": {"errors": [{"reason": "$reason"}]}}""", status)
            }

            assertFailsWith<SyncStoreFullException> {
                failing(HttpStatusCode.Forbidden, "storageQuotaExceeded").list("q")
            }
            assertFailsWith<SyncUnreachableException> {
                failing(HttpStatusCode.Forbidden, "userRateLimitExceeded").list("q")
            }
            assertFailsWith<SyncUnreachableException> {
                failing(HttpStatusCode.ServiceUnavailable, "backendError").list("q")
            }
            assertFailsWith<SyncSignInNeededException> {
                failing(HttpStatusCode.Unauthorized, "authError").list("q")
            }
            assertFailsWith<DriveException> { failing(HttpStatusCode.BadRequest, "invalid").list("q") }
            assertFailsWith<SyncUnreachableException> { folder { throw IOException("offline") }.list("q") }
        }

    @Test
    fun deletingAFileThatsGoneIsFine() =
        runBlocking<Unit> {
            folder { respond("", HttpStatusCode.NotFound) }.delete("gone")
            assertEquals(HttpMethod.Delete, requests.single().method)
        }

    private fun folder(answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        RestAppDataFolder(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    // A request that loops would otherwise run until memory runs out.
                    check(requests.size < MAX_REQUESTS) { "Too many requests" }
                    answer(request)
                },
            ),
            tokens,
        )

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private companion object {
        const val MAX_REQUESTS = 10
    }

    /** Hands out token-1, then token-2 when that's turned down, and so on. */
    private class CountingTokens : AccessTokens {
        private var count = 1

        override suspend fun token() = "token-$count"

        override suspend fun refresh(stale: String): String {
            count++
            return token()
        }
    }
}
