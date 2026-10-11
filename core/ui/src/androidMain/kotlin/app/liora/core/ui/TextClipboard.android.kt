package app.liora.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android's clipboard. Reading it is only allowed while Liora is in front, which a tap on Paste is; Android 12
 * and later tell the user it was read.
 */
@Composable
actual fun rememberTextClipboard(): TextClipboard {
    val context = LocalContext.current
    return remember(context) { AndroidTextClipboard(context) }
}

private class AndroidTextClipboard(
    private val context: Context,
) : TextClipboard {
    private val manager get() = context.getSystemService(ClipboardManager::class.java)

    override fun text(): String? {
        val clip = manager?.primaryClip ?: return null
        return (0 until clip.itemCount)
            .joinToString("\n") { clip.getItemAt(it).coerceToText(context) }
            .ifBlank { null }
    }

    override fun copy(
        label: String,
        text: String,
    ) {
        manager?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
