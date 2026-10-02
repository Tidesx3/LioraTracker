package app.liora.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.navigation.Navigator
import app.liora.core.navigation.SettingsRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun EntryProviderScope<NavKey>.settingsEntries(navigator: Navigator) {
    entry<SettingsRoute> {
        SettingsScreen(viewModel = koinViewModel(), onBack = navigator::goBack)
    }
}

val settingsModule =
    module {
        viewModelOf(::SettingsViewModel)
    }
