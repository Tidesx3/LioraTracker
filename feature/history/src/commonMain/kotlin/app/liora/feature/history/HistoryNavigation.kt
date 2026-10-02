package app.liora.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.navigation.EditWorkoutRoute
import app.liora.core.navigation.ExerciseDetailRoute
import app.liora.core.navigation.HistoryRoute
import app.liora.core.navigation.ListDetail
import app.liora.core.navigation.LoggerRoute
import app.liora.core.navigation.Navigator
import app.liora.core.navigation.RoutineDetailRoute
import app.liora.core.navigation.TrainRoute
import app.liora.core.navigation.WorkoutDetailRoute
import app.liora.feature.history.detail.WorkoutDetailNavigation
import app.liora.feature.history.detail.WorkoutDetailScreen
import app.liora.feature.history.detail.WorkoutDetailViewModel
import app.liora.feature.history.resources.Res
import app.liora.feature.history.resources.history_pick_body
import app.liora.feature.history.resources.history_pick_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

fun EntryProviderScope<NavKey>.historyEntries(navigator: Navigator) {
    entry<HistoryRoute>(
        metadata =
            ListDetail.listPane {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = LioraIcons.History,
                        title = stringResource(Res.string.history_pick_title),
                        body = stringResource(Res.string.history_pick_body),
                    )
                }
            },
    ) {
        HistoryScreen(
            // Shown beside the list on wide screens; this highlights the workout open there.
            selectedWorkoutId =
                navigator.backStack
                    .filterIsInstance<WorkoutDetailRoute>()
                    .lastOrNull()
                    ?.workoutId,
            onOpenWorkout = { navigator.openFromList(HistoryRoute, WorkoutDetailRoute(it)) },
        )
    }
    entry<WorkoutDetailRoute>(metadata = ListDetail.detailPane()) { route ->
        WorkoutDetailScreen(
            viewModel = koinViewModel(key = route.workoutId) { parametersOf(route.workoutId) },
            navigation =
                WorkoutDetailNavigation(
                    onBack = navigator::goBack,
                    onOpenExercise = { navigator.navigate(ExerciseDetailRoute(it)) },
                    onOpenLogger = { navigator.navigate(LoggerRoute) },
                    // Routines live on the Train tab; the new one opens there, with the list to go back to.
                    onOpenRoutine = { routineId ->
                        navigator.selectTopLevel(TrainRoute)
                        navigator.openFromList(TrainRoute, RoutineDetailRoute(routineId))
                    },
                    onEdit = { navigator.navigate(EditWorkoutRoute(route.workoutId)) },
                ),
        )
    }
}

val historyModule =
    module {
        viewModelOf(::HistoryViewModel)
        viewModel { (workoutId: String) -> WorkoutDetailViewModel(workoutId, get(), get(), get(), get()) }
    }
