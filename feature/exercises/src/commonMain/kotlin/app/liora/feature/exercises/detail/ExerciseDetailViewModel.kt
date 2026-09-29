package app.liora.feature.exercises.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseInUseException
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.model.Exercise
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ExerciseDetailUiState {
    data object Loading : ExerciseDetailUiState

    /** The exercise no longer exists (e.g. just deleted); the screen closes itself. */
    data object Gone : ExerciseDetailUiState

    data class Loaded(
        val exercise: Exercise,
        /** The built-in a custom variation came from, for the "Variation of …" line. */
        val baseName: String?,
        val dialog: DetailDialog?,
    ) : ExerciseDetailUiState
}

enum class DetailDialog { ConfirmDelete, InUse }

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModel(
    private val exerciseId: String,
    private val repository: ExerciseRepository,
) : ViewModel() {
    private val language = MutableStateFlow<String?>(null)
    private val dialog = MutableStateFlow<DetailDialog?>(null)

    val uiState: StateFlow<ExerciseDetailUiState> =
        language
            .filterNotNull()
            .flatMapLatest { lang ->
                repository.observeExercise(exerciseId, lang).flatMapLatest { exercise ->
                    val base = exercise?.variationOf?.let { repository.observeExercise(it, lang) } ?: flowOf(null)
                    base.map { exercise to it?.name }
                }
            }.combine(dialog) { (exercise, baseName), openDialog ->
                if (exercise == null) {
                    ExerciseDetailUiState.Gone
                } else {
                    ExerciseDetailUiState.Loaded(exercise, baseName, openDialog)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState.Loading)

    fun setLanguage(value: String) {
        language.value = value
    }

    fun setHidden(hidden: Boolean) {
        viewModelScope.launch { repository.setArchived(exerciseId, hidden) }
    }

    /** Asks for confirmation, or explains why deleting isn't possible. */
    fun requestDelete() {
        viewModelScope.launch {
            dialog.value = if (repository.isInUse(exerciseId)) DetailDialog.InUse else DetailDialog.ConfirmDelete
        }
    }

    fun dismissDialog() {
        dialog.value = null
    }

    fun confirmDelete() {
        dialog.value = null
        viewModelScope.launch {
            try {
                repository.deleteCustom(exerciseId)
            } catch (_: ExerciseInUseException) {
                // Used in the meantime (e.g. in a running workout): explain instead of deleting.
                dialog.value = DetailDialog.InUse
            }
        }
    }

    fun hideInsteadOfDelete() {
        dialog.value = null
        setHidden(true)
    }
}
