package app.liora.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.navigation.ExercisePickerRoute
import app.liora.core.navigation.GymEditorRoute
import app.liora.core.navigation.GymsRoute
import app.liora.core.navigation.HealthConnectRoute
import app.liora.core.navigation.ImportRoute
import app.liora.core.navigation.NavigationResultEffect
import app.liora.core.navigation.Navigator
import app.liora.core.navigation.RoutineDetailRoute
import app.liora.core.navigation.RoutineImportRoute
import app.liora.core.navigation.SettingsRoute
import app.liora.core.navigation.SyncRoute
import app.liora.core.navigation.TrainRoute
import app.liora.core.ui.currentLanguage
import app.liora.feature.settings.data.DataViewModel
import app.liora.feature.settings.gym.GymEditorScreen
import app.liora.feature.settings.gym.GymEditorViewModel
import app.liora.feature.settings.gym.GymsScreen
import app.liora.feature.settings.gym.GymsViewModel
import app.liora.feature.settings.health.HealthScreen
import app.liora.feature.settings.health.HealthViewModel
import app.liora.feature.settings.importing.ImportScreen
import app.liora.feature.settings.importing.ImportViewModel
import app.liora.feature.settings.importing.RoutineImportScreen
import app.liora.feature.settings.importing.RoutineImportViewModel
import app.liora.feature.settings.sync.SyncScreen
import app.liora.feature.settings.sync.SyncViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

fun EntryProviderScope<NavKey>.settingsEntries(navigator: Navigator) {
    entry<SettingsRoute> {
        SettingsScreen(
            viewModel = koinViewModel(),
            dataViewModel = koinViewModel(),
            updatesViewModel = koinViewModel(),
            onBack = navigator::goBack,
            onOpenGyms = { navigator.navigate(GymsRoute) },
            onOpenSync = { navigator.navigate(SyncRoute) },
            onOpenHealth = { navigator.navigate(HealthConnectRoute) },
            onImport = { source -> navigator.navigate(ImportRoute(source)) },
            onImportRoutines = { navigator.navigate(RoutineImportRoute()) },
        )
    }
    routineImportEntry(navigator)
    entry<HealthConnectRoute> {
        HealthScreen(viewModel = koinViewModel(), onBack = navigator::goBack)
    }
    entry<SyncRoute> {
        SyncScreen(viewModel = koinViewModel(), onBack = navigator::goBack)
    }
    entry<GymsRoute> {
        GymsScreen(
            viewModel = koinViewModel(),
            onBack = navigator::goBack,
            onOpen = { gymId -> navigator.navigate(GymEditorRoute(gymId)) },
        )
    }
    entry<ImportRoute> { route ->
        val language = currentLanguage()
        val viewModel: ImportViewModel = koinViewModel(key = route.toString()) { parametersOf(route, language) }
        NavigationResultEffect<List<String>>(navigator.results, PICK_EXERCISE) { ids ->
            ids.firstOrNull()?.let(viewModel::onExercisePicked)
        }
        ImportScreen(
            viewModel = viewModel,
            onClose = navigator::goBack,
            onPickExercise = { navigator.navigate(ExercisePickerRoute(PICK_EXERCISE, multiple = false)) },
        )
    }
    entry<GymEditorRoute> { route ->
        GymEditorScreen(
            viewModel = koinViewModel(key = route.toString()) { parametersOf(route) },
            onClose = navigator::goBack,
        )
    }
}

/** Routines from a routine file, opened from Train, Settings, or shared to Liora. */
private fun EntryProviderScope<NavKey>.routineImportEntry(navigator: Navigator) {
    entry<RoutineImportRoute> { route ->
        val language = currentLanguage()
        val viewModel: RoutineImportViewModel =
            koinViewModel(key = route.toString()) { parametersOf(route, language) }
        NavigationResultEffect<List<String>>(navigator.results, PICK_ROUTINE_EXERCISE) { ids ->
            ids.firstOrNull()?.let(viewModel::onExercisePicked)
        }
        RoutineImportScreen(
            viewModel = viewModel,
            onClose = navigator::goBack,
            onPickExercise = { navigator.navigate(ExercisePickerRoute(PICK_ROUTINE_EXERCISE, multiple = false)) },
            onDone = { routineIds ->
                // To the routines, on the Train tab: the one routine opened, or the list for a program.
                navigator.close(route)
                navigator.showTopLevel(TrainRoute)
                routineIds.singleOrNull()?.let { navigator.openFromList(TrainRoute, RoutineDetailRoute(it)) }
            },
        )
    }
}

/** Where the exercise picker hands back the exercise chosen for a name in an import. */
private const val PICK_EXERCISE = "import.exercise"

/** Where the exercise picker hands back the exercise chosen for a name in a routine file. */
private const val PICK_ROUTINE_EXERCISE = "routine-import.exercise"

val settingsModule =
    module {
        viewModelOf(::SettingsViewModel)
        viewModelOf(::DataViewModel)
        viewModelOf(::UpdatesViewModel)
        viewModelOf(::GymsViewModel)
        viewModelOf(::SyncViewModel)
        viewModelOf(::HealthViewModel)
        viewModelOf(::GymEditorViewModel)
        viewModel { (route: ImportRoute, language: String) -> ImportViewModel(route, language, get(), get()) }
        viewModel { (route: RoutineImportRoute, language: String) ->
            RoutineImportViewModel(route, language, get(), get(), get())
        }
    }
