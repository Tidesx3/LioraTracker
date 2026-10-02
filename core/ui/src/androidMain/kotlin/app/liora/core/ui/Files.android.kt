package app.liora.core.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * The Storage Access Framework: the user picks a place on the phone, in Drive or on a USB stick, and the
 * app may use that one file without any storage permission.
 */
@Composable
actual fun rememberFileSaver(
    mimeType: String,
    onPick: (String) -> Unit,
): FileSaver {
    val currentOnPick by rememberUpdatedState(onPick)
    val contract = remember(mimeType) { ActivityResultContracts.CreateDocument(mimeType) }
    val launcher = rememberLauncherForActivityResult(contract) { uri -> uri?.let { currentOnPick(it.toString()) } }
    return remember(launcher) { FileSaver { name -> launchIfPossible { launcher.launch(name) } } }
}

@Composable
actual fun rememberFileOpener(
    mimeTypes: List<String>,
    onPick: (String) -> Unit,
): FileOpener {
    val currentOnPick by rememberUpdatedState(onPick)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { currentOnPick(it.toString()) }
        }
    val types = remember(mimeTypes) { mimeTypes.toTypedArray() }
    return remember(launcher, types) { FileOpener { launchIfPossible { launcher.launch(types) } } }
}

/** Phones without a file picker (some locked-down devices) do nothing rather than crash. */
private inline fun launchIfPossible(launch: () -> Unit) {
    try {
        launch()
    } catch (_: ActivityNotFoundException) {
        Unit
    }
}
