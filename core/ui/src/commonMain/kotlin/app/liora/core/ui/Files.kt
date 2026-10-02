package app.liora.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Asks the system's file picker where to save a new file. What comes back is a platform location (a
 * content URI on Android) to write to; nothing when the user backs out.
 */
@Stable
fun interface FileSaver {
    /** Opens the picker with [suggestedName] filled in. */
    fun save(suggestedName: String)
}

/** Asks the system's file picker for a file to open; what comes back is its platform location. */
@Stable
fun interface FileOpener {
    fun open()
}

/** A [FileSaver] for files of [mimeType], whose location goes to [onPick]. */
@Composable
expect fun rememberFileSaver(
    mimeType: String,
    onPick: (String) -> Unit,
): FileSaver

/** A [FileOpener] offering files of [mimeTypes], whose location goes to [onPick]. */
@Composable
expect fun rememberFileOpener(
    mimeTypes: List<String>,
    onPick: (String) -> Unit,
): FileOpener
