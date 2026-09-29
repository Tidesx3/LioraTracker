package app.liora.feature.exercises.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.domain.ExerciseSearch
import app.liora.core.model.Equipment
import app.liora.core.model.Exercise
import app.liora.core.model.Muscle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class LibraryFilters(
    val muscles: Set<Muscle> = emptySet(),
    val equipment: Set<Equipment> = emptySet(),
    val customOnly: Boolean = false,
    val showHidden: Boolean = false,
) {
    val isActive: Boolean get() = muscles.isNotEmpty() || equipment.isNotEmpty() || customOnly

    fun matches(exercise: Exercise): Boolean =
        (muscles.isEmpty() || exercise.primaryMuscles.any { it in muscles }) &&
            (equipment.isEmpty() || exercise.equipment in equipment) &&
            (!customOnly || exercise.isCustom)
}

sealed interface FilterEvent {
    data class ToggleMuscle(
        val muscle: Muscle,
    ) : FilterEvent

    data object ClearMuscles : FilterEvent

    data class ToggleEquipment(
        val equipment: Equipment,
    ) : FilterEvent

    data object ClearEquipment : FilterEvent

    data object ToggleCustomOnly : FilterEvent

    data object ToggleShowHidden : FilterEvent
}

data class ExerciseLibraryUiState(
    val filters: LibraryFilters = LibraryFilters(),
    val results: List<Exercise> = emptyList(),
    val loading: Boolean = true,
)

class ExerciseLibraryViewModel(
    repository: ExerciseRepository,
) : ViewModel() {
    /** Held as Compose state so the text field updates synchronously while typing. */
    var query by mutableStateOf("")
        private set

    private val language = MutableStateFlow<String?>(null)
    private val filters = MutableStateFlow(LibraryFilters())

    private val searchIndex: StateFlow<ExerciseSearch?> = repository.searchIndex(language, viewModelScope)

    val uiState: StateFlow<ExerciseLibraryUiState> =
        combine(searchIndex, snapshotFlow { query }, filters) { index, text, activeFilters ->
            if (index == null) {
                ExerciseLibraryUiState(filters = activeFilters, loading = true)
            } else {
                val results =
                    index
                        .search(text, includeArchived = activeFilters.showHidden)
                        .filter(activeFilters::matches)
                ExerciseLibraryUiState(filters = activeFilters, results = results, loading = false)
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseLibraryUiState())

    fun setLanguage(value: String) {
        language.value = value
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun onFilterEvent(event: FilterEvent) {
        filters.value =
            with(filters.value) {
                when (event) {
                    is FilterEvent.ToggleMuscle -> copy(muscles = muscles.toggle(event.muscle))
                    FilterEvent.ClearMuscles -> copy(muscles = emptySet())
                    is FilterEvent.ToggleEquipment -> copy(equipment = equipment.toggle(event.equipment))
                    FilterEvent.ClearEquipment -> copy(equipment = emptySet())
                    FilterEvent.ToggleCustomOnly -> copy(customOnly = !customOnly)
                    FilterEvent.ToggleShowHidden -> copy(showHidden = !showHidden)
                }
            }
    }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
}
