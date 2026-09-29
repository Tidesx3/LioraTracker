package app.liora.feature.history

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.navigation.HistoryRoute

fun EntryProviderScope<NavKey>.historyEntries() {
    entry<HistoryRoute> {
        HistoryScreen()
    }
}
