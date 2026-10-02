package app.liora.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * The camera app writes into a scratch file shared through the app's FileProvider, and the system photo
 * picker hands back what was chosen. Neither needs a permission.
 */
@Composable
actual fun rememberPhotoSource(onPhotos: (List<String>) -> Unit): PhotoSource {
    val context = LocalContext.current
    val currentOnPhotos by rememberUpdatedState(onPhotos)
    // Where the camera is writing, kept through the app being stopped while the camera is open.
    var capture by rememberSaveable { mutableStateOf<String?>(null) }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val uri = capture
            capture = null
            if (saved && uri != null) currentOnPhotos(listOf(uri))
        }
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED)) { uris ->
            if (uris.isNotEmpty()) currentOnPhotos(uris.map(Uri::toString))
        }
    return remember(context, camera, gallery) {
        object : PhotoSource {
            override fun takePhoto() {
                val uri = newCaptureUri(context)
                capture = uri.toString()
                try {
                    camera.launch(uri)
                } catch (_: ActivityNotFoundException) {
                    // No camera app on this device.
                    capture = null
                }
            }

            override fun pickPhotos() {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }
}

/** A fresh file for the camera; earlier captures were imported already, so they go. */
private fun newCaptureUri(context: Context): Uri {
    val directory = File(context.cacheDir, CAPTURE_DIRECTORY)
    directory.listFiles()?.forEach(File::delete)
    directory.mkdirs()
    val file = File(directory, "capture-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, context.packageName + PROVIDER_SUFFIX, file)
}

/** Declared in app/android's manifest, with `res/xml/photo_paths.xml` naming the directory. */
private const val PROVIDER_SUFFIX = ".photos"
private const val CAPTURE_DIRECTORY = "camera"

/** A session's worth of photos and then some; the picker caps what it allows. */
private const val MAX_PICKED = 20
