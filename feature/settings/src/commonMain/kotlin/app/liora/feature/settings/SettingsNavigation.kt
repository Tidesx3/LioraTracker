package app.liora.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.navigation.GymEditorRoute
import app.liora.core.navigation.GymsRoute
import app.liora.core.navigation.Navigator
import app.liora.core.navigation.SettingsRoute
import app.liora.feature.settings.gym.GymEditorScreen
import app.liora.feature.settings.gym.GymEditorViewModel
import app.liora.feature.settings.gym.GymsScreen
import app.liora.feature.settings.gym.GymsViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

fun EntryProviderScope<NavKey>.settingsEntries(navigator: Navigator) {
    entry<SettingsRoute> {
        SettingsScreen(
            viewModel = koinViewModel(),
            onBack = navigator::goBack,
            onOpenGyms = { navigator.navigate(GymsRoute) },
        )
    }
    entry<GymsRoute> {
        GymsScreen(
            viewModel = koinViewModel(),
            onBack = navigator::goBack,
            onOpen = { gymId -> navigator.navigate(GymEditorRoute(gymId)) },
        )
    }
    entry<GymEditorRoute> { route ->
        GymEditorScreen(
            viewModel = koinViewModel(key = route.toString()) { parametersOf(route) },
            onClose = navigator::goBack,
        )
    }
}

val settingsModule =
    module {
        viewModelOf(::SettingsViewModel)
        viewModelOf(::GymsViewModel)
        viewModelOf(::GymEditorViewModel)
    }
