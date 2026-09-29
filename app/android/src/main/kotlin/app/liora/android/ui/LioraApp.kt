package app.liora.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.liora.core.designsystem.component.ActiveWorkoutBar
import app.liora.core.designsystem.component.ActiveWorkoutRailButton
import app.liora.core.designsystem.layout.currentWindowLayout
import app.liora.core.designsystem.util.workoutDisplayName
import app.liora.core.model.ActiveWorkout
import app.liora.core.navigation.ListDetailSceneStrategy
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
 * App shell: the Navigation 3 host plus the navigation chrome, which follows the window size.
 * - Compact (phones, the Fold's cover screen): a bottom bar with the active-workout mini bar above it,
 *   shown on the tabs themselves and hidden on full-screen destinations.
 * - Medium and up (the Fold's inner screen, tablets): a navigation rail headed by the active workout,
 *   shown everywhere except the logger, and list-detail destinations side by side.
 *
 * NavDisplay keeps its place in the composition whichever chrome is showing, so folding or unfolding
 * keeps every screen's state.
 */
@Composable
fun LioraApp(
    modifier: Modifier = Modifier,
    viewModel: LioraAppViewModel = koinViewModel(),
) {
    val navigator = viewModel.navigator
    val activeWorkout by viewModel.activeWorkout.collectAsStateWithLifecycle()
    val layout = currentWindowLayout()
    val showRail = layout.navigationRail && navigator.currentRoute != LoggerRoute
    val showBottomChrome = !layout.navigationRail && navigator.currentRoute in TopLevelDestination.routes
    val listDetail = remember(layout.twoPane) { ListDetailSceneStrategy<NavKey>(layout.twoPane) }

    Row(modifier.fillMaxSize()) {
        if (showRail) {
            NavigationSideRail(navigator = navigator, activeWorkout = activeWorkout)
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (showBottomChrome) {
                    BottomChrome(navigator = navigator, activeWorkout = activeWorkout)
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
                        .consumeWindowInsets(padding)
                        // The rail already keeps clear of the cutout and system bars on its side.
                        .consumeWindowInsets(
                            if (showRail) RailInsets.only(WindowInsetsSides.Start) else WindowInsets(0),
                        ),
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                sceneStrategies = listOf(listDetail),
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
            NavigationBar(
                modifier = Modifier.testTag(ShellTags.NAVIGATION_BAR),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
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

@Composable
private fun NavigationSideRail(
    navigator: Navigator,
    activeWorkout: ActiveWorkout?,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier.testTag(ShellTags.NAVIGATION_RAIL),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        header =
            activeWorkout?.let { workout ->
                {
                    ActiveWorkoutRailButton(
                        startedAt = workout.startedAt,
                        onClick = { navigator.navigate(LoggerRoute) },
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                }
            },
        windowInsets = RailInsets,
    ) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = navigator.topLevelRoute == destination.route,
                onClick = { navigator.selectTopLevel(destination.route) },
                icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                label = { Text(stringResource(destination.label)) },
            )
        }
    }
}

/** Lets tests tell which navigation chrome the window size produced. */
internal object ShellTags {
    const val NAVIGATION_BAR = "navigation_bar"
    const val NAVIGATION_RAIL = "navigation_rail"
}

private val RailInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
