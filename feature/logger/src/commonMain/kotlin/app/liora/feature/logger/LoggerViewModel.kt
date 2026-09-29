package app.liora.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.model.ActiveWorkout
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LoggerUiState {
    /** Waiting for the database; renders nothing so there is no flash of "no workout". */
    data object Loading : LoggerUiState

    data object NoWorkout : LoggerUiState

    data class Active(
        val workout: ActiveWorkout,
    ) : LoggerUiState
}

class LoggerViewModel(
    private val activeWorkouts: ActiveWorkoutRepository,
) : ViewModel() {
    val uiState: StateFlow<LoggerUiState> =
        activeWorkouts.activeWorkout
            .map { workout -> workout?.let(LoggerUiState::Active) ?: LoggerUiState.NoWorkout }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoggerUiState.Loading)

    fun finish() {
        viewModelScope.launch { activeWorkouts.finish() }
    }

    fun discard() {
        viewModelScope.launch { activeWorkouts.discard() }
    }
}
