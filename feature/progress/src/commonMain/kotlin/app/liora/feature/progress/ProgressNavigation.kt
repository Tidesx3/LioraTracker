package app.liora.feature.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.navigation.BodyRoute
import app.liora.core.navigation.ExerciseDetailRoute
import app.liora.core.navigation.ListDetail
import app.liora.core.navigation.Navigator
import app.liora.core.navigation.ProgressRoute
import app.liora.feature.progress.resources.Res
import app.liora.feature.progress.resources.progress_pick_body
import app.liora.feature.progress.resources.progress_pick_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun EntryProviderScope<NavKey>.progressEntries(navigator: Navigator) {
    // On wide screens an exercise opens beside the overview, with its chart, records and sessions.
    entry<ProgressRoute>(
        metadata =
            ListDetail.listPane {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = LioraIcons.Progress,
                        title = stringResource(Res.string.progress_pick_title),
                        body = stringResource(Res.string.progress_pick_body),
                    )
                }
            },
    ) {
        ProgressScreen(
            viewModel = koinViewModel(),
            navigation =
                ProgressNavigation(
                    onOpenExercise = { navigator.openFromList(ProgressRoute, ExerciseDetailRoute(it)) },
                    onOpenBody = { navigator.navigate(BodyRoute) },
                ),
            // Shown beside the overview on wide screens; this highlights the exercise open there.
            selectedExerciseId =
                navigator.backStack
                    .filterIsInstance<ExerciseDetailRoute>()
                    .lastOrNull()
                    ?.exerciseId,
        )
    }
}

val progressModule =
    module {
        viewModelOf(::ProgressViewModel)
    }
