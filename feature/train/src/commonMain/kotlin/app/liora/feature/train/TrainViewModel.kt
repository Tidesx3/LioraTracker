package app.liora.feature.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.workout.ActiveWorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrainUiState(
    val hasActiveWorkout: Boolean = false,
)

class TrainViewModel(
    private val activeWorkouts: ActiveWorkoutRepository,
) : ViewModel() {
    val uiState: StateFlow<TrainUiState> =
        activeWorkouts.activeWorkout
            .map { TrainUiState(hasActiveWorkout = it != null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainUiState())

    fun startEmptyWorkout() {
        viewModelScope.launch { activeWorkouts.startEmptyWorkout() }
    }
}
