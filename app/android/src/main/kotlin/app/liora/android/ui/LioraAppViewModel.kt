package app.liora.android.ui

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.model.ActiveWorkout
import app.liora.core.navigation.Navigator
import app.liora.core.navigation.TrainRoute
import app.liora.core.navigation.restore
import app.liora.core.navigation.save
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Activity-scoped state for the app shell; survives configuration changes. */
class LioraAppViewModel(
    activeWorkouts: ActiveWorkoutRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val navigator =
        Navigator(startRoute = TrainRoute, topLevelRoutes = TopLevelDestination.routes).apply {
            savedState.get<Bundle>(NAVIGATION)?.getString(NAVIGATION)?.let(::restore)
        }

    init {
        // Written down whenever the activity saves its state, so after Android ends the process (say, while
        // the camera app takes a progress photo) Liora opens where it was, and the screen gets its result.
        savedState.setSavedStateProvider(NAVIGATION) { bundleOf(NAVIGATION to navigator.save()) }
    }

    val activeWorkout: StateFlow<ActiveWorkout?> =
        activeWorkouts.activeWorkout.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private const val NAVIGATION = "navigation"
