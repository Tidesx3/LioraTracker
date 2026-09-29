package app.liora.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.navigation.Navigator
import app.liora.core.navigation.SettingsRoute

fun EntryProviderScope<NavKey>.settingsEntries(navigator: Navigator) {
    entry<SettingsRoute> {
        SettingsScreen(onBack = navigator::goBack)
    }
}
