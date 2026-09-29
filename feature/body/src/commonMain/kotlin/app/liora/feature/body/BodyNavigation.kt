package app.liora.feature.body

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.navigation.BodyRoute
import app.liora.core.navigation.Navigator

fun EntryProviderScope<NavKey>.bodyEntries(navigator: Navigator) {
    entry<BodyRoute> {
        BodyScreen(onBack = navigator::goBack)
    }
}
