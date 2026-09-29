package app.liora.feature.train.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.model.Exercise
import app.liora.core.model.Routine
import app.liora.core.model.RoutineFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RoutineDetailUiState {
    data object Loading : RoutineDetailUiState

    /** Deleted, here or on another device. */
    data object Gone : RoutineDetailUiState

    data class Loaded(
        val routine: Routine,
        val exercises: Map<String, Exercise>,
        val folders: List<RoutineFolder>,
        val hasActiveWorkout: Boolean,
        val dialog: RoutineDialog?,
    ) : RoutineDetailUiState
}

class RoutineDetailViewModel(
    private val routineId: String,
    private val routines: RoutineRepository,
    exerciseRepository: ExerciseRepository,
    private val activeWorkouts: ActiveWorkoutRepository,
) : ViewModel() {
    private val language = MutableStateFlow<String?>(null)
    private val dialog = MutableStateFlow<RoutineDialog?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val exercises =
        language
            .filterNotNull()
            .flatMapLatest { exerciseRepository.observeExercises(it) }
            .map { all -> all.associateBy { it.id } }
            .flowOn(Dispatchers.Default)
            .onStart { emit(emptyMap()) }

    val uiState: StateFlow<RoutineDetailUiState> =
        combine(
            routines.observeRoutine(routineId),
            exercises,
            routines.folders,
            activeWorkouts.activeWorkout,
            dialog,
        ) { routine, exercisesById, folders, active, openDialog ->
            if (routine == null) {
                RoutineDetailUiState.Gone
            } else {
                RoutineDetailUiState.Loaded(routine, exercisesById, folders, active != null, openDialog)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineDetailUiState.Loading)

    fun setLanguage(value: String) {
        language.value = value
    }

    /** Starts the routine and returns true, or asks to finish the running workout first and returns false. */
    fun start(): Boolean {
        if ((uiState.value as? RoutineDetailUiState.Loaded)?.hasActiveWorkout == true) {
            dialog.value = RoutineDialog.WorkoutInProgress
            return false
        }
        viewModelScope.launch { activeWorkouts.startFromRoutine(routineId) }
        return true
    }

    fun showDialog(value: RoutineDialog) {
        dialog.value = value
    }

    fun dismissDialog() {
        dialog.value = null
    }

    fun duplicate(name: String) = write { routines.duplicate(routineId, name) }

    fun moveToFolder(folderId: String?) = write { routines.moveToFolder(routineId, folderId) }

    /** The screen closes once the routine is gone, never before the delete lands. */
    fun delete() = write { routines.delete(routineId) }

    private fun write(block: suspend () -> Unit) {
        dialog.value = null
        viewModelScope.launch { block() }
    }
}
