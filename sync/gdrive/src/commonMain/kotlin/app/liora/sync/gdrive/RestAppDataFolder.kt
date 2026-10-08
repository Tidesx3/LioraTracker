package app.liora.sync.gdrive

import app.liora.core.data.sync.SyncSignInNeededException
import app.liora.core.data.sync.SyncStoreFullException
import app.liora.core.data.sync.SyncUnreachableException
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/** The app data folder through Drive's REST API (v3). */
internal class RestAppDataFolder(
    private val http: HttpClient,
    private val tokens: AccessTokens,
) : AppDataFolder {
    override suspend fun list(query: String): List<DriveFile> {
        val files = mutableListOf<DriveFile>()
        var page: String? = null
        do {
            val response =
                send {
                    url(FILES)
                    parameter("spaces", APP_DATA)
                    parameter("q", query)
                    parameter("fields", "nextPageToken,files(id,name,appProperties)")
                    parameter("pageSize", PAGE_SIZE)
                    page?.let { parameter("pageToken", it) }
                }
            val list = json.decodeFromString<FileList>(response.bodyAsText())
            files += list.files
            page = list.nextPageToken
        } while (page != null)
        return files
    }

    override suspend fun download(id: String): ByteArray =
        send {
            url("$FILES/$id")
            parameter("alt", "media")
        }.readRawBytes()

    override suspend fun create(
        name: String,
        properties: Map<String, String>,
        content: ByteArray,
    ): DriveFile {
        val metadata = json.encodeToString(NewFile(name, listOf(APP_DATA), properties))
        val boundary = "liora-" + Random.nextLong().toULong().toString(BOUNDARY_RADIX)
        val body =
            "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n".encodeToByteArray() +
                "--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".encodeToByteArray() +
                content +
                "\r\n--$boundary--\r\n".encodeToByteArray()
        val response =
            send {
                method = HttpMethod.Post
                url(UPLOAD)
                parameter("uploadType", "multipart")
                parameter("fields", "id,name,appProperties")
                setBody(ByteArrayContent(body, ContentType.parse("multipart/related; boundary=$boundary")))
            }
        return json.decodeFromString(response.bodyAsText())
    }

    override suspend fun update(
        id: String,
        content: ByteArray,
    ) {
        send {
            method = HttpMethod.Patch
            url("$UPLOAD/$id")
            parameter("uploadType", "media")
            setBody(ByteArrayContent(content, ContentType.Application.OctetStream))
        }
    }

    override suspend fun delete(id: String) {
        send(missingIsFine = true) {
            method = HttpMethod.Delete
            url("$FILES/$id")
        }
    }

    /** The email address of the account whose Drive this is, to show where Liora syncs to. */
    suspend fun accountEmail(): String {
        val response =
            send {
                url(ABOUT)
                parameter("fields", "user(emailAddress)")
            }
        return json.decodeFromString<About>(response.bodyAsText()).user.emailAddress
    }

    /** Sends a request, with a fresh token once if Drive turns the current one down. */
    private suspend fun send(
        missingIsFine: Boolean = false,
        configure: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val token = tokens.token()
        var response = request(token, configure)
        if (response.status == HttpStatusCode.Unauthorized) response = request(tokens.refresh(token), configure)
        return when {
            response.status.isSuccess() -> response
            missingIsFine && response.status == HttpStatusCode.NotFound -> response
            else -> throw failure(response)
        }
    }

    private suspend fun request(
        token: String,
        configure: HttpRequestBuilder.() -> Unit,
    ): HttpResponse =
        try {
            // Not `build()`: that's the builder's own function, which would leave the request unset.
            http.request {
                configure(this)
                bearerAuth(token)
            }
        } catch (e: IOException) {
            throw SyncUnreachableException("Drive can't be reached", e)
        }

    /** What a failed response means for syncing. */
    private suspend fun failure(response: HttpResponse): Exception {
        val status = response.status.value
        val body = response.bodyAsText()
        val reason = errorReason(body)
        return when {
            status == HttpStatusCode.Unauthorized.value || reason in SIGN_IN_REASONS -> {
                SyncSignInNeededException()
            }

            reason == "storageQuotaExceeded" -> {
                SyncStoreFullException()
            }

            status == TOO_MANY_REQUESTS || status >= SERVER_ERRORS || reason in BUSY_REASONS -> {
                SyncUnreachableException("Drive is busy: $status $reason")
            }

            else -> {
                DriveException(status, body)
            }
        }
    }

    /** The first reason in a Drive error body, e.g. `storageQuotaExceeded`. */
    private fun errorReason(body: String): String? =
        runCatching {
            json
                .parseToJsonElement(body)
                .jsonObject["error"]
                ?.jsonObject
                ?.get("errors")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("reason")
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()

    @Serializable
    private class FileList(
        val files: List<DriveFile> = emptyList(),
        val nextPageToken: String? = null,
    )

    @Serializable
    private class NewFile(
        val name: String,
        val parents: List<String>,
        val appProperties: Map<String, String>,
    )

    @Serializable
    private class About(
        val user: User,
    ) {
        @Serializable
        class User(
            val emailAddress: String,
        )
    }

    private companion object {
        const val FILES = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        const val ABOUT = "https://www.googleapis.com/drive/v3/about"
        const val APP_DATA = "appDataFolder"
        const val PAGE_SIZE = 1000
        const val BOUNDARY_RADIX = 36
        const val TOO_MANY_REQUESTS = 429
        const val SERVER_ERRORS = 500
        val SIGN_IN_REASONS = setOf("insufficientPermissions", "authError")
        val BUSY_REASONS = setOf("rateLimitExceeded", "userRateLimitExceeded", "backendError")
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Drive turned a request down for a reason syncing can't do anything about. */
class DriveException(
    val status: Int,
    body: String,
) : Exception("Drive answered $status: $body")
