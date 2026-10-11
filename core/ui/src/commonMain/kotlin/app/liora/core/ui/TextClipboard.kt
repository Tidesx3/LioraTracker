package app.liora.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/** Plain text on the system clipboard, for what people copy between apps, such as an AI's answer. */
@Stable
interface TextClipboard {
    /** What's on the clipboard as text; null when there's nothing, or nothing that reads as text. */
    fun text(): String?

    /** Puts [text] on the clipboard; [label] says what it is, where the system shows it. */
    fun copy(
        label: String,
        text: String,
    )
}

@Composable
expect fun rememberTextClipboard(): TextClipboard
