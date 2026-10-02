package app.liora.core.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Brings in photos: from the camera or from the phone's gallery. What comes back are platform
 * locations (content URIs on Android) for a `PhotoStorage` to import; nothing when cancelled.
 */
@Stable
interface PhotoSource {
    fun takePhoto()

    fun pickPhotos()
}

/** A [PhotoSource] whose photos go to [onPhotos]. */
@Composable
expect fun rememberPhotoSource(onPhotos: (List<String>) -> Unit): PhotoSource

/**
 * A photo stored on this device, on a tonal ground until it has loaded (or for good, if it can't), so a
 * photo fitted into a larger space isn't framed by it.
 */
@Composable
fun StoredPhoto(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var loaded by remember(path) { mutableStateOf(false) }
    AsyncImage(
        model = path,
        contentDescription = contentDescription,
        contentScale = contentScale,
        onSuccess = { loaded = true },
        modifier =
            modifier.background(
                if (loaded) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    )
}
