package app.liora.core.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Android 13's per-app languages; the app's `localeConfig` lists English and German. */
@Composable
actual fun rememberAppLanguage(): AppLanguage {
    val context = LocalContext.current
    return remember(context) { AndroidAppLanguage(context) }
}

private class AndroidAppLanguage(
    private val context: Context,
) : AppLanguage {
    override val available: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override val current: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) chosen() else null

    override fun choose(language: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) apply(language)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun chosen(): String? =
        manager()
            .applicationLocales
            .takeUnless { it.isEmpty }
            ?.get(0)
            ?.language

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun apply(language: String?) {
        manager().applicationLocales =
            language?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun manager(): LocaleManager = context.getSystemService(LocaleManager::class.java)
}
