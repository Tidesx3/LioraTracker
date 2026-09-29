package app.liora.feature.exercises

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.navigation.ExerciseDetailRoute
import app.liora.core.navigation.ExerciseEditorRoute
import app.liora.core.navigation.ExercisesRoute
import app.liora.core.navigation.Navigator
import app.liora.core.ui.currentLanguage
import app.liora.feature.exercises.detail.ExerciseDetailScreen
import app.liora.feature.exercises.detail.ExerciseDetailViewModel
import app.liora.feature.exercises.editor.ExerciseEditorScreen
import app.liora.feature.exercises.editor.ExerciseEditorViewModel
import app.liora.feature.exercises.library.ExerciseLibraryScreen
import app.liora.feature.exercises.library.ExerciseLibraryViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

fun EntryProviderScope<NavKey>.exercisesEntries(navigator: Navigator) {
    entry<ExercisesRoute> {
        ExerciseLibraryScreen(
            onOpenExercise = { navigator.navigate(ExerciseDetailRoute(it)) },
            onCreateExercise = { name -> navigator.navigate(ExerciseEditorRoute(initialName = name)) },
        )
    }
    entry<ExerciseDetailRoute> { route ->
        ExerciseDetailScreen(
            viewModel = koinViewModel(key = route.exerciseId) { parametersOf(route.exerciseId) },
            onBack = navigator::goBack,
            onEdit = { navigator.navigate(ExerciseEditorRoute(exerciseId = it)) },
            onCreateVariation = { navigator.navigate(ExerciseEditorRoute(variationOf = it)) },
        )
    }
    entry<ExerciseEditorRoute> { route ->
        val language = currentLanguage()
        ExerciseEditorScreen(
            viewModel = koinViewModel(key = route.toString()) { parametersOf(route, language) },
            onClose = navigator::goBack,
        )
    }
}

val exercisesModule =
    module {
        viewModelOf(::ExerciseLibraryViewModel)
        viewModel { (exerciseId: String) -> ExerciseDetailViewModel(exerciseId, get()) }
        viewModel { (route: ExerciseEditorRoute, language: String) -> ExerciseEditorViewModel(route, language, get()) }
    }
