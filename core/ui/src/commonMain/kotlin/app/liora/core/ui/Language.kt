package app.liora.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.intl.Locale

/**
 * The UI language (ISO 639-1, e.g. `de`), for data that is localized in the database rather than in
 * string resources, such as exercise names. ViewModels receive it from the screen so they follow
 * language changes without being recreated.
 */
@Composable
@ReadOnlyComposable
fun currentLanguage(): String = Locale.current.language
