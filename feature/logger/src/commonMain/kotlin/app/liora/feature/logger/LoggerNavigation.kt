package app.liora.feature.logger

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.navigation.EditWorkoutRoute
import app.liora.core.navigation.ExerciseDetailRoute
import app.liora.core.navigation.ExercisePickerRoute
import app.liora.core.navigation.LoggerRoute
import app.liora.core.navigation.NavigationResultEffect
import app.liora.core.navigation.Navigator
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

fun EntryProviderScope<NavKey>.loggerEntries(navigator: Navigator) {
    entry<LoggerRoute> { LoggerEntry(navigator, finishedWorkoutId = null) }
    // The same logger on a finished workout from history, to correct it.
    entry<EditWorkoutRoute> { route -> LoggerEntry(navigator, finishedWorkoutId = route.workoutId) }
}

@Composable
private fun LoggerEntry(
    navigator: Navigator,
    finishedWorkoutId: String?,
) {
    val viewModel: LoggerViewModel =
        koinViewModel(key = finishedWorkoutId?.let { "edit.$it" }) { parametersOf(finishedWorkoutId) }
    val keys = PickerKeys(finishedWorkoutId)
    NavigationResultEffect<List<String>>(navigator.results, keys.add, viewModel::addExercises)
    NavigationResultEffect<List<String>>(navigator.results, keys.replace, viewModel::onReplacementPicked)
    LoggerScreen(
        viewModel = viewModel,
        navigation =
            LoggerNavigationActions(
                onClose = navigator::goBack,
                onAddExercises = { navigator.navigate(ExercisePickerRoute(keys.add)) },
                onOpenExercise = { navigator.navigate(ExerciseDetailRoute(it)) },
                onReplaceExercise = { navigator.navigate(ExercisePickerRoute(keys.replace, multiple = false)) },
            ),
    )
}

val loggerModule =
    module {
        // A finished workout's id opens it for corrections; without one, the logger is on the workout in progress.
        viewModel { params ->
            LoggerViewModel(params.getOrNull<String>(), get(), get(), get(), get(), get(), get(), get())
        }
    }
