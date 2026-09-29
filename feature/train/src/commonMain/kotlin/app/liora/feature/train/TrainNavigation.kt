package app.liora.feature.train

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.navigation.LoggerRoute
import app.liora.core.navigation.Navigator
import app.liora.core.navigation.SettingsRoute
import app.liora.core.navigation.TrainRoute
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun EntryProviderScope<NavKey>.trainEntries(navigator: Navigator) {
    entry<TrainRoute> {
        TrainScreen(
            onOpenLogger = { navigator.navigate(LoggerRoute) },
            onOpenSettings = { navigator.navigate(SettingsRoute) },
        )
    }
}

val trainModule =
    module {
        viewModelOf(::TrainViewModel)
    }
