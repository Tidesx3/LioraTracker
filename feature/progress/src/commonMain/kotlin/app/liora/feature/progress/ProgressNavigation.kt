package app.liora.feature.progress

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.navigation.BodyRoute
import app.liora.core.navigation.Navigator
import app.liora.core.navigation.ProgressRoute

fun EntryProviderScope<NavKey>.progressEntries(navigator: Navigator) {
    entry<ProgressRoute> {
        ProgressScreen(onOpenBody = { navigator.navigate(BodyRoute) })
    }
}
