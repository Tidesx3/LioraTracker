package app.liora.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * The app's own language, apart from the phone's. The system keeps the choice (it is a per-device
 * setting, not a synced one), and switching it restarts the screen in the new language.
 */
@Stable
interface AppLanguage {
    /** Whether the app can have its own language here; on Android, from Android 13. */
    val available: Boolean

    /** The chosen language (ISO 639-1, e.g. `de`); null follows the phone. */
    val current: String?

    fun choose(language: String?)
}

@Composable
expect fun rememberAppLanguage(): AppLanguage

/** The languages the app is translated into. */
val AppLanguages: List<String> = listOf("en", "de")
