package app.liora.feature.exercises

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.navigation.ExerciseDetailRoute
import app.liora.core.navigation.ExerciseEditorRoute
import app.liora.core.navigation.ExercisePickerRoute
import app.liora.core.navigation.ExercisesRoute
import app.liora.core.navigation.ListDetail
import app.liora.core.navigation.NavigationResultEffect
import app.liora.core.navigation.Navigator
import app.liora.core.ui.currentLanguage
import app.liora.feature.exercises.detail.ExerciseDetailScreen
import app.liora.feature.exercises.detail.ExerciseDetailViewModel
import app.liora.feature.exercises.editor.ExerciseEditorScreen
import app.liora.feature.exercises.editor.ExerciseEditorViewModel
import app.liora.feature.exercises.library.ExerciseLibraryScreen
import app.liora.feature.exercises.library.ExerciseLibraryViewModel
import app.liora.feature.exercises.picker.ExercisePickerScreen
import app.liora.feature.exercises.picker.ExercisePickerViewModel
import app.liora.feature.exercises.resources.Res
import app.liora.feature.exercises.resources.exercises_pick_body
import app.liora.feature.exercises.resources.exercises_pick_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

fun EntryProviderScope<NavKey>.exercisesEntries(navigator: Navigator) {
    entry<ExercisesRoute>(
        metadata =
            ListDetail.listPane {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = LioraIcons.Exercises,
                        title = stringResource(Res.string.exercises_pick_title),
                        body = stringResource(Res.string.exercises_pick_body),
                    )
                }
            },
    ) {
        ExerciseLibraryScreen(
            // Shown beside the list on wide screens; this highlights the exercise open there.
            selectedId =
                navigator.backStack
                    .filterIsInstance<ExerciseDetailRoute>()
                    .lastOrNull()
                    ?.exerciseId,
            onOpenExercise = { navigator.openFromList(ExercisesRoute, ExerciseDetailRoute(it)) },
            onCreateExercise = { name ->
                navigator.openFromList(ExercisesRoute, ExerciseEditorRoute(initialName = name))
            },
        )
    }
    entry<ExerciseDetailRoute>(metadata = ListDetail.detailPane()) { route ->
        ExerciseDetailScreen(
            viewModel = koinViewModel(key = route.exerciseId) { parametersOf(route.exerciseId) },
            onBack = navigator::goBack,
            onEdit = { navigator.navigate(ExerciseEditorRoute(exerciseId = it)) },
            onCreateVariation = { navigator.navigate(ExerciseEditorRoute(variationOf = it)) },
        )
    }
    entry<ExerciseEditorRoute>(metadata = ListDetail.detailPane()) { route ->
        val language = currentLanguage()
        ExerciseEditorScreen(
            viewModel = koinViewModel(key = route.toString()) { parametersOf(route, language) },
            onClose = navigator::goBack,
            onSaveComplete = { id ->
                route.resultKey?.let { navigator.goBackWithResult(it, id) } ?: navigator.goBack()
            },
        )
    }
    // Full screen rather than a pane: it is opened from editors, which it replaces while picking.
    entry<ExercisePickerRoute> { route ->
        val viewModel: ExercisePickerViewModel = koinViewModel(key = route.requestKey)
        val createdKey = route.requestKey + CREATED_SUFFIX
        NavigationResultEffect<String>(navigator.results, createdKey, viewModel::onExerciseCreated)
        ExercisePickerScreen(
            viewModel = viewModel,
            onClose = navigator::goBack,
            onDone = { ids -> navigator.goBackWithResult(route.requestKey, ids) },
            onCreateExercise = { name ->
                navigator.navigate(ExerciseEditorRoute(initialName = name, resultKey = createdKey))
            },
        )
    }
}

/** Where the picker hears back about an exercise created from its search. */
private const val CREATED_SUFFIX = ".created"

val exercisesModule =
    module {
        viewModelOf(::ExerciseLibraryViewModel)
        viewModelOf(::ExercisePickerViewModel)
        viewModel { (exerciseId: String) -> ExerciseDetailViewModel(exerciseId, get()) }
        viewModel { (route: ExerciseEditorRoute, language: String) -> ExerciseEditorViewModel(route, language, get()) }
    }
