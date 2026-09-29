package app.liora.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.ActiveWorkoutRepository
import app.liora.core.model.ActiveWorkout
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoggerViewModel(
    private val activeWorkouts: ActiveWorkoutRepository,
) : ViewModel() {
    val activeWorkout: StateFlow<ActiveWorkout?> = activeWorkouts.activeWorkout

    fun finish() {
        viewModelScope.launch { activeWorkouts.finish() }
    }

    fun discard() {
        viewModelScope.launch { activeWorkouts.discard() }
    }
}
