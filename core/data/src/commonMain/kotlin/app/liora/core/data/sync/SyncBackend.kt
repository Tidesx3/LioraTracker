package app.liora.core.data.sync

/**
 * Where devices leave their changes for each other: the user's Google Drive, later Liora's own server.
 * It stores sync batches (see `SyncEngine`) and photos' images, and knows nothing of what's in them.
 */
interface SyncBackend {
    /** Adds [batch] to the store as the next one from [deviceId]. */
    suspend fun push(
        deviceId: String,
        batch: ByteArray,
    )

    /**
     * The batches other devices than [deviceId] added since [cursor] (null: since the start), oldest
     * first, and the cursor to pull from next time. What a cursor means is up to the store.
     */
    suspend fun pull(
        deviceId: String,
        cursor: String?,
    ): PulledBatches

    /** Stores a photo's image under [name], replacing one stored there before. */
    suspend fun putBlob(
        name: String,
        bytes: ByteArray,
    )

    /** The image stored under [name], or null when there's none. */
    suspend fun getBlob(name: String): ByteArray?
}

/** Batches from other devices, and where to pull from next time. */
class PulledBatches(
    val batches: List<ByteArray>,
    val cursor: String?,
)
