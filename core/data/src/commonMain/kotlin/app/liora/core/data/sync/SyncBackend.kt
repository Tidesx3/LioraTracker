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

    /** How much of the user's space the store takes, in bytes; null when it can't say. */
    suspend fun usedBytes(): Long? = null

    /** Whether [deviceId]'s batches have piled up, so it should hand over everything it has in one. */
    suspend fun wantsSnapshot(deviceId: String): Boolean = false

    /**
     * Stores [snapshot], everything [deviceId] has (deletions included), in place of its batches so far:
     * a device that hasn't read them reads the snapshot instead.
     */
    suspend fun putSnapshot(
        deviceId: String,
        snapshot: ByteArray,
    ) = Unit
}

/** The store couldn't be reached (no network, or it's busy); trying again later may work. */
class SyncUnreachableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The store won't let this device in until the user signs in again. */
class SyncSignInNeededException(
    cause: Throwable? = null,
) : Exception("Signing in again is needed", cause)

/** The store is out of space. */
class SyncStoreFullException(
    cause: Throwable? = null,
) : Exception("The store is full", cause)

/** Batches from other devices, and where to pull from next time. */
class PulledBatches(
    val batches: List<ByteArray>,
    val cursor: String?,
)
