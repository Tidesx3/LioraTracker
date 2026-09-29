package app.liora.android.ui

import androidx.lifecycle.ViewModel
import app.liora.core.data.ActiveWorkoutRepository
import app.liora.core.model.ActiveWorkout
import app.liora.core.navigation.Navigator
import app.liora.core.navigation.TrainRoute
import kotlinx.coroutines.flow.StateFlow

/** Activity-scoped state for the app shell; survives configuration changes. */
class LioraAppViewModel(
    activeWorkouts: ActiveWorkoutRepository,
) : ViewModel() {
    val navigator = Navigator(startRoute = TrainRoute, topLevelRoutes = TopLevelDestination.routes)

    val activeWorkout: StateFlow<ActiveWorkout?> = activeWorkouts.activeWorkout
}
