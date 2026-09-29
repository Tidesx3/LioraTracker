package app.liora.feature.exercises

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.navigation.ExercisesRoute

fun EntryProviderScope<NavKey>.exercisesEntries() {
    entry<ExercisesRoute> {
        ExercisesScreen()
    }
}
