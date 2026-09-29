package app.liora.feature.logger

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.navigation.LoggerRoute
import app.liora.core.navigation.Navigator
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun EntryProviderScope<NavKey>.loggerEntries(navigator: Navigator) {
    entry<LoggerRoute> {
        LoggerScreen(onClose = navigator::goBack)
    }
}

val loggerModule =
    module {
        viewModelOf(::LoggerViewModel)
    }
