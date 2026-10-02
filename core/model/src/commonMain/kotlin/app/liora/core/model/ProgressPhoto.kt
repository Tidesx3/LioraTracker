package app.liora.core.model

import kotlin.time.Instant

/** A progress photo, kept in the app's own storage so it stays private and off the phone's gallery. */
data class ProgressPhoto(
    val id: String,
    val takenAt: Instant,
    /** Null until the user says how it was taken. */
    val pose: PhotoPose?,
    /** The image file on this device. */
    val path: String,
)

/** How a progress photo was taken, so like is compared with like. Keys are persisted and synced. */
enum class PhotoPose(
    val key: String,
) {
    Front("front"),
    Side("side"),
    Back("back"),
    ;

    companion object {
        fun fromKey(key: String): PhotoPose? = entries.firstOrNull { it.key == key }
    }
}
