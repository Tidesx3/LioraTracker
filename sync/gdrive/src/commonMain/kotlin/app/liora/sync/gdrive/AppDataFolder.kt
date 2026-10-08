package app.liora.sync.gdrive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A file in Liora's app data folder. [properties] (Drive's `appProperties`) say what it holds; [size] is in
 * bytes, as Drive writes it (a 64-bit number in a string).
 */
@Serializable
internal data class DriveFile(
    val id: String,
    val name: String,
    @SerialName("appProperties") val properties: Map<String, String> = emptyMap(),
    val size: String? = null,
)

/** Liora's own hidden folder in the user's Drive: the only part of it Liora can see. */
internal interface AppDataFolder {
    /**
     * The files matching a Drive search [query]. Callers still check each file's [DriveFile.properties],
     * so a store that doesn't search (as in tests) works too.
     */
    suspend fun list(query: String): List<DriveFile>

    suspend fun download(id: String): ByteArray

    suspend fun create(
        name: String,
        properties: Map<String, String>,
        content: ByteArray,
    ): DriveFile

    /** Replaces a file's content. */
    suspend fun update(
        id: String,
        content: ByteArray,
    )

    /** Deletes a file; one that's gone already is fine. */
    suspend fun delete(id: String)
}

/** Signs requests to Drive. */
interface AccessTokens {
    suspend fun token(): String

    /**
     * A new token in place of [stale], which Drive turned down. Throws `SyncSignInNeededException` when the
     * user has to sign in again.
     */
    suspend fun refresh(stale: String): String
}
