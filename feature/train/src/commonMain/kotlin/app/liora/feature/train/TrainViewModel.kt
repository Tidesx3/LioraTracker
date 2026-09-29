package app.liora.feature.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.model.Routine
import app.liora.core.model.RoutineFolder
import app.liora.feature.train.routine.RoutineDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

/** A routine as a card: its exercises reduced to names. */
data class RoutineCardState(
    val routine: Routine,
    val exerciseNames: List<String>,
)

/** Routines under a folder; [folder] is null for routines outside any folder. */
data class RoutineSection(
    val folder: RoutineFolder?,
    val routines: List<RoutineCardState>,
)

data class TrainUiState(
    val hasActiveWorkout: Boolean = false,
    val sections: List<RoutineSection> = emptyList(),
    val folders: List<RoutineFolder> = emptyList(),
    val loading: Boolean = true,
    val dialog: RoutineDialog? = null,
) {
    val isEmpty: Boolean get() = folders.isEmpty() && sections.all { it.routines.isEmpty() }
}

@Suppress("TooManyFunctions") // one function per user intent
class TrainViewModel(
    private val activeWorkouts: ActiveWorkoutRepository,
    private val routines: RoutineRepository,
    exercises: ExerciseRepository,
) : ViewModel() {
    private val language = MutableStateFlow<String?>(null)
    private val dialog = MutableStateFlow<RoutineDialog?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val exerciseNames: Flow<Map<String, String>> =
        language
            .filterNotNull()
            .flatMapLatest { exercises.observeExercises(it) }
            .map { all -> all.associate { it.id to it.name } }
            .flowOn(Dispatchers.Default)
            .onStart { emit(emptyMap()) }

    val uiState: StateFlow<TrainUiState> =
        combine(
            activeWorkouts.activeWorkout,
            routines.routines,
            routines.folders,
            exerciseNames,
            dialog,
        ) { active, all, folders, names, openDialog ->
            TrainUiState(
                hasActiveWorkout = active != null,
                sections = sections(all, folders, names),
                folders = folders,
                loading = false,
                dialog = openDialog,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainUiState())

    fun setLanguage(value: String) {
        language.value = value
    }

    fun startEmptyWorkout() {
        viewModelScope.launch { activeWorkouts.startEmptyWorkout() }
    }

    /** Starts [routineId] and returns true, or asks to finish the running workout first and returns false. */
    fun startRoutine(routineId: String): Boolean {
        if (uiState.value.hasActiveWorkout) {
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

    fun createFolder(name: String) = write { routines.createFolder(name) }

    fun renameFolder(
        id: String,
        name: String,
    ) = write { routines.renameFolder(id, name) }

    fun deleteFolder(id: String) = write { routines.deleteFolder(id) }

    fun moveRoutine(
        routineId: String,
        folderId: String?,
    ) = write { routines.moveToFolder(routineId, folderId) }

    fun duplicateRoutine(
        routineId: String,
        name: String,
    ) = write { routines.duplicate(routineId, name) }

    fun deleteRoutine(routineId: String) = write { routines.delete(routineId) }

    private fun write(block: suspend () -> Unit) {
        dialog.value = null
        viewModelScope.launch { block() }
    }
}

private fun sections(
    routines: List<Routine>,
    folders: List<RoutineFolder>,
    names: Map<String, String>,
): List<RoutineSection> {
    val folderIds = folders.map { it.id }.toSet()
    // A routine whose folder is gone (deleted on another device) shows outside any folder.
    val byFolder = routines.groupBy { it.folderId?.takeIf { id -> id in folderIds } }

    fun cards(folderId: String?) =
        byFolder[folderId].orEmpty().map { routine ->
            RoutineCardState(routine, routine.exercises.mapNotNull { names[it.exerciseId] })
        }
    return listOf(RoutineSection(null, cards(null))) + folders.map { RoutineSection(it, cards(it.id)) }
}
