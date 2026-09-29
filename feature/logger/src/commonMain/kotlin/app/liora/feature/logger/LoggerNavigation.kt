package app.liora.feature.logger

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.navigation.ExercisePickerRoute
import app.liora.core.navigation.LoggerRoute
import app.liora.core.navigation.NavigationResultEffect
import app.liora.core.navigation.Navigator
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun EntryProviderScope<NavKey>.loggerEntries(navigator: Navigator) {
    entry<LoggerRoute> {
        val viewModel: LoggerViewModel = koinViewModel()
        NavigationResultEffect<List<String>>(navigator.results, PickerKeys.ADD, viewModel::addExercises)
        NavigationResultEffect<List<String>>(navigator.results, PickerKeys.REPLACE, viewModel::onReplacementPicked)
        LoggerScreen(
            viewModel = viewModel,
            navigation =
                LoggerNavigationActions(
                    onClose = navigator::goBack,
                    onAddExercises = { navigator.navigate(ExercisePickerRoute(PickerKeys.ADD)) },
                    onReplaceExercise = {
                        navigator.navigate(
                            ExercisePickerRoute(PickerKeys.REPLACE, multiple = false),
                        )
                    },
                ),
        )
    }
}

val loggerModule =
    module {
        viewModelOf(::LoggerViewModel)
    }
