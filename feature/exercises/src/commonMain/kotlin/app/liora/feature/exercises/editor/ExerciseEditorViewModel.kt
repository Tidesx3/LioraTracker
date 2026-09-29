package app.liora.feature.exercises.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.exercise.TrackingTypeLockedException
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseDraft
import app.liora.core.model.Muscle
import app.liora.core.model.TrackingType
import app.liora.core.navigation.ExerciseEditorRoute
import app.liora.feature.exercises.resources.Res
import app.liora.feature.exercises.resources.editor_variation_name
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

data class ExerciseEditorUiState(
    val isNew: Boolean,
    val loading: Boolean = true,
    val draft: ExerciseDraft = ExerciseDraft(),
    /** Sets were logged with the current tracking type, so it can't change without losing data. */
    val trackingLocked: Boolean = false,
    val saving: Boolean = false,
    /** Set once saving finished; the screen closes in response. */
    val savedId: String? = null,
)

class ExerciseEditorViewModel(
    private val route: ExerciseEditorRoute,
    private val language: String,
    private val repository: ExerciseRepository,
) : ViewModel() {
    private val state = MutableStateFlow(ExerciseEditorUiState(isNew = route.exerciseId == null))
    val uiState: StateFlow<ExerciseEditorUiState> = state.asStateFlow()

    init {
        viewModelScope.launch { state.value = load() }
    }

    private suspend fun load(): ExerciseEditorUiState {
        route.exerciseId?.let { id ->
            val exercise = repository.observeExercise(id, language).first()
            return state.value.copy(
                loading = false,
                draft = exercise?.let(ExerciseDraft::from) ?: ExerciseDraft(),
                trackingLocked = repository.hasLoggedSets(id),
            )
        }
        route.variationOf?.let { baseId ->
            val base = repository.observeExercise(baseId, language).first()
            if (base != null) {
                val name = getString(Res.string.editor_variation_name, base.name)
                return state.value.copy(loading = false, draft = ExerciseDraft.variationOf(base, name))
            }
        }
        return state.value.copy(loading = false, draft = ExerciseDraft(name = route.initialName.orEmpty()))
    }

    fun onNameChange(name: String) = editDraft { copy(name = name) }

    fun onTrackingTypeChange(type: TrackingType) {
        if (!state.value.trackingLocked) editDraft { copy(trackingType = type) }
    }

    fun onEquipmentChange(equipment: Equipment) = editDraft { copy(equipment = equipment) }

    fun togglePrimary(muscle: Muscle) =
        editDraft { copy(primaryMuscles = primaryMuscles.toggle(muscle), secondaryMuscles = secondaryMuscles - muscle) }

    fun toggleSecondary(muscle: Muscle) {
        if (muscle !in
            state.value.draft.primaryMuscles
        ) {
            editDraft { copy(secondaryMuscles = secondaryMuscles.toggle(muscle)) }
        }
    }

    fun onNotesChange(notes: String) = editDraft { copy(notes = notes) }

    fun save() {
        val current = state.value
        if (!current.draft.isValid || current.saving) return
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val id =
                    route.exerciseId?.also { repository.updateCustom(it, current.draft) }
                        ?: repository.createCustom(current.draft)
                state.update { it.copy(saving = false, savedId = id) }
            } catch (_: TrackingTypeLockedException) {
                // Sets were logged for this exercise meanwhile: keep the original tracking type.
                state.update { it.copy(saving = false, trackingLocked = true) }
            }
        }
    }

    private fun editDraft(change: ExerciseDraft.() -> ExerciseDraft) {
        state.update { it.copy(draft = it.draft.change()) }
    }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
}
