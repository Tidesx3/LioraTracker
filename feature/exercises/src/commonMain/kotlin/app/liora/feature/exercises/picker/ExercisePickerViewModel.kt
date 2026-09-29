package app.liora.feature.exercises.picker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.model.Exercise
import app.liora.feature.exercises.library.searchIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ExercisePickerUiState(
    val results: List<Exercise> = emptyList(),
    /** Picked exercise ids in the order they were tapped, which is the order they get added. */
    val selected: List<String> = emptyList(),
    val loading: Boolean = true,
)

class ExercisePickerViewModel(
    repository: ExerciseRepository,
) : ViewModel() {
    var query by mutableStateOf("")
        private set

    private val language = MutableStateFlow<String?>(null)
    private val selected = MutableStateFlow<List<String>>(emptyList())
    private val searchIndex = repository.searchIndex(language, viewModelScope)

    val uiState: StateFlow<ExercisePickerUiState> =
        combine(searchIndex, snapshotFlow { query }, selected) { index, text, picked ->
            ExercisePickerUiState(
                // Hidden exercises stay out of pickers; history keeps them.
                results = index?.search(text, includeArchived = false).orEmpty(),
                selected = picked,
                loading = index == null,
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisePickerUiState())

    fun setLanguage(value: String) {
        language.value = value
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun toggle(exerciseId: String) {
        selected.update { if (exerciseId in it) it - exerciseId else it + exerciseId }
    }

    /** A custom exercise made from the picker counts as picked; it shows up for the search that made it. */
    fun onExerciseCreated(exerciseId: String) {
        selected.update { if (exerciseId in it) it else it + exerciseId }
    }
}
