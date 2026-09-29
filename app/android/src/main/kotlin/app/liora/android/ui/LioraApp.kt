package app.liora.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.liora.core.designsystem.component.ActiveWorkoutBar
import app.liora.core.designsystem.util.workoutDisplayName
import app.liora.core.model.ActiveWorkout
import app.liora.core.navigation.LoggerRoute
import app.liora.core.navigation.Navigator
import app.liora.feature.body.bodyEntries
import app.liora.feature.exercises.exercisesEntries
import app.liora.feature.history.historyEntries
import app.liora.feature.logger.loggerEntries
import app.liora.feature.progress.progressEntries
import app.liora.feature.settings.settingsEntries
import app.liora.feature.train.trainEntries
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * App shell: the Navigation 3 host plus the bottom chrome (active-workout mini bar and tab bar),
 * which is shown on top-level screens and hidden on full-screen ones like the logger.
 */
@Composable
fun LioraApp(
    modifier: Modifier = Modifier,
    viewModel: LioraAppViewModel = koinViewModel(),
) {
    val navigator = viewModel.navigator
    val activeWorkout by viewModel.activeWorkout.collectAsStateWithLifecycle()
    val showBottomChrome = navigator.currentRoute in TopLevelDestination.routes

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomChrome) {
                BottomChrome(
                    navigator = navigator,
                    activeWorkout = activeWorkout,
                )
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = navigator.backStack,
            onBack = navigator::goBack,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    trainEntries(navigator)
                    historyEntries()
                    progressEntries(navigator)
                    exercisesEntries(navigator)
                    loggerEntries(navigator)
                    bodyEntries(navigator)
                    settingsEntries(navigator)
                },
        )
    }
}

@Composable
private fun BottomChrome(
    navigator: Navigator,
    activeWorkout: ActiveWorkout?,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            if (activeWorkout != null) {
                ActiveWorkoutBar(
                    title = workoutDisplayName(activeWorkout.name),
                    startedAt = activeWorkout.startedAt,
                    onClick = { navigator.navigate(LoggerRoute) },
                )
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = navigator.topLevelRoute == destination.route,
                        onClick = { navigator.selectTopLevel(destination.route) },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        }
    }
}
