package app.liora.feature.train

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.navigation.ExercisePickerRoute
import app.liora.core.navigation.ListDetail
import app.liora.core.navigation.LoggerRoute
import app.liora.core.navigation.NavigationResultEffect
import app.liora.core.navigation.Navigator
import app.liora.core.navigation.RoutineDetailRoute
import app.liora.core.navigation.RoutineEditorRoute
import app.liora.core.navigation.SettingsRoute
import app.liora.core.navigation.TrainRoute
import app.liora.feature.train.resources.Res
import app.liora.feature.train.resources.train_pick_body
import app.liora.feature.train.resources.train_pick_title
import app.liora.feature.train.routine.RoutineDetailScreen
import app.liora.feature.train.routine.RoutineDetailViewModel
import app.liora.feature.train.routine.RoutineEditorScreen
import app.liora.feature.train.routine.RoutineEditorViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

fun EntryProviderScope<NavKey>.trainEntries(navigator: Navigator) {
    entry<TrainRoute>(
        metadata =
            ListDetail.listPane {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = LioraIcons.Train,
                        title = stringResource(Res.string.train_pick_title),
                        body = stringResource(Res.string.train_pick_body),
                    )
                }
            },
    ) {
        TrainScreen(
            // Shown beside the list on wide screens; this highlights the routine open there.
            selectedRoutineId =
                navigator.backStack
                    .filterIsInstance<RoutineDetailRoute>()
                    .lastOrNull()
                    ?.routineId,
            navigation =
                TrainNavigationActions(
                    onOpenLogger = { navigator.navigate(LoggerRoute) },
                    onOpenSettings = { navigator.navigate(SettingsRoute) },
                    onOpenRoutine = { navigator.openFromList(TrainRoute, RoutineDetailRoute(it)) },
                    onNewRoutine = { folderId ->
                        navigator.openFromList(TrainRoute, RoutineEditorRoute(folderId = folderId))
                    },
                    onEditRoutine = { navigator.openFromList(TrainRoute, RoutineEditorRoute(routineId = it)) },
                ),
        )
    }
    entry<RoutineDetailRoute>(metadata = ListDetail.detailPane()) { route ->
        RoutineDetailScreen(
            viewModel = koinViewModel(key = route.routineId) { parametersOf(route.routineId) },
            onBack = navigator::goBack,
            onEdit = { navigator.navigate(RoutineEditorRoute(routineId = route.routineId)) },
            onOpenLogger = { navigator.navigate(LoggerRoute) },
        )
    }
    entry<RoutineEditorRoute>(metadata = ListDetail.detailPane()) { route ->
        val viewModel: RoutineEditorViewModel = koinViewModel(key = route.toString()) { parametersOf(route) }
        val pickerKey = "$route$PICKED_SUFFIX"
        NavigationResultEffect<List<String>>(navigator.results, pickerKey, viewModel::addExercises)
        RoutineEditorScreen(
            viewModel = viewModel,
            onClose = navigator::goBack,
            onSaveComplete = { routineId ->
                if (route.routineId == null) {
                    // A new routine opens right away, ready to start; back returns to the list.
                    navigator.openFromList(TrainRoute, RoutineDetailRoute(routineId))
                } else {
                    navigator.goBack()
                }
            },
            onAddExercises = { navigator.navigate(ExercisePickerRoute(requestKey = pickerKey)) },
        )
    }
}

/** Where a routine editor hears back from the exercise picker. */
private const val PICKED_SUFFIX = ".picked"

val trainModule =
    module {
        viewModelOf(::TrainViewModel)
        viewModel { (routineId: String) -> RoutineDetailViewModel(routineId, get(), get(), get()) }
        viewModel { (route: RoutineEditorRoute) -> RoutineEditorViewModel(route, get(), get(), get()) }
    }
