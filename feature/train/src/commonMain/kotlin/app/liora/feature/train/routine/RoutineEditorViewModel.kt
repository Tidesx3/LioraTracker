package app.liora.feature.train.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.common.IdGenerator
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.domain.Supersets
import app.liora.core.model.Exercise
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.core.navigation.RoutineEditorRoute
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutineEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val routine: Routine = Routine(id = "", name = ""),
    val exercises: Map<String, Exercise> = emptyMap(),
    val hasChanges: Boolean = false,
    val saving: Boolean = false,
    /** Set once the save has landed; the screen closes in reaction to it. */
    val savedId: String? = null,
) {
    val canSave: Boolean get() = routine.isValid && !saving
}

/**
 * Edits a copy of the routine and writes it in one go on save, so leaving without saving changes
 * nothing. Exercises and sets are addressed by id, which stays stable while the list is reordered.
 */
@Suppress("TooManyFunctions") // one function per user intent
class RoutineEditorViewModel(
    route: RoutineEditorRoute,
    private val routines: RoutineRepository,
    exerciseRepository: ExerciseRepository,
    private val ids: IdGenerator,
) : ViewModel() {
    private val language = MutableStateFlow<String?>(null)
    private val original = MutableStateFlow<Routine?>(null)
    private val draft = MutableStateFlow<Routine?>(null)
    private val saving = MutableStateFlow(false)
    private val savedId = MutableStateFlow<String?>(null)
    private val isNew = route.routineId == null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val exercises =
        language
            .filterNotNull()
            .flatMapLatest { exerciseRepository.observeExercises(it) }
            .map { all -> all.associateBy { it.id } }
            .flowOn(Dispatchers.Default)
            .onStart { emit(emptyMap()) }

    val uiState: StateFlow<RoutineEditorUiState> =
        combine(draft, original, exercises, saving, savedId) { routine, stored, exercisesById, isSaving, saved ->
            if (routine == null) {
                RoutineEditorUiState()
            } else {
                RoutineEditorUiState(
                    loading = false,
                    isNew = isNew,
                    routine = routine,
                    exercises = exercisesById,
                    hasChanges = routine != stored,
                    saving = isSaving,
                    savedId = saved,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineEditorUiState())

    init {
        viewModelScope.launch {
            val routine =
                route.routineId?.let { routines.get(it) }
                    ?: Routine(id = ids.newId(), name = "", folderId = route.folderId)
            original.value = routine
            draft.value = routine
        }
    }

    fun setLanguage(value: String) {
        language.value = value
    }

    fun onNameChange(name: String) = edit { copy(name = name) }

    fun onNotesChange(notes: String) = edit { copy(notes = notes.ifEmpty { null }) }

    /** Adds picked exercises at the end, each with a starting set count that suits how it's tracked. */
    fun addExercises(exerciseIds: List<String>) {
        val known = uiState.value.exercises
        editExercises { current ->
            current +
                exerciseIds.map { exerciseId ->
                    val sets = if (known[exerciseId]?.trackingType?.usesReps == false) 1 else DEFAULT_SETS
                    RoutineExercise(
                        id = ids.newId(),
                        exerciseId = exerciseId,
                        sets = List(sets) { RoutineSet(id = ids.newId()) },
                    )
                }
        }
    }

    fun removeExercise(id: String) = editExercises { it.filterNot { exercise -> exercise.id == id } }

    fun move(
        fromId: String,
        toId: String,
    ) = editExercises { current ->
        val from = current.indexOfFirst { it.id == fromId }
        val to = current.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0) current else current.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun linkWithNext(id: String) =
        editExercises { current ->
            val index = current.indexOfFirst { it.id == id }
            if (index !in
                0 until current.lastIndex
            ) {
                current
            } else {
                current.withGroups(Supersets.linkWithNext(current.groups(), index))
            }
        }

    fun unlink(id: String) =
        editExercises { current ->
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) current else current.withGroups(Supersets.unlink(current.groups(), index))
        }

    /** Adds a set that repeats the last one, so a new working set needs no typing. */
    fun addSet(exerciseId: String) =
        updateExercise(exerciseId) {
            copy(sets = sets + (sets.lastOrNull() ?: RoutineSet(id = "")).copy(id = ids.newId()))
        }

    fun removeSet(
        exerciseId: String,
        setId: String,
    ) = updateExercise(exerciseId) { copy(sets = sets.filterNot { it.id == setId }) }

    fun updateSet(
        exerciseId: String,
        setId: String,
        change: RoutineSet.() -> RoutineSet,
    ) = updateExercise(exerciseId) { copy(sets = sets.map { if (it.id == setId) it.change() else it }) }

    fun save() {
        val routine = draft.value ?: return
        if (!routine.isValid || saving.value) return
        saving.value = true
        viewModelScope.launch {
            routines.save(routine)
            savedId.value = routine.id
        }
    }

    private fun edit(change: Routine.() -> Routine) {
        draft.update { it?.change() }
    }

    /** Every change to the exercise list ends with supersets repaired, whatever moved or went away. */
    private fun editExercises(change: (List<RoutineExercise>) -> List<RoutineExercise>) =
        edit {
            val changed = change(exercises)
            copy(exercises = changed.withGroups(Supersets.normalize(changed.groups())))
        }

    /** Changes one exercise's own settings, such as its rest time or note. */
    fun updateExercise(
        id: String,
        change: RoutineExercise.() -> RoutineExercise,
    ) = editExercises { current -> current.map { if (it.id == id) it.change() else it } }

    private companion object {
        const val DEFAULT_SETS = 3
    }
}

private fun List<RoutineExercise>.groups(): List<Int?> = map { it.supersetGroup }

private fun List<RoutineExercise>.withGroups(groups: List<Int?>): List<RoutineExercise> =
    zip(groups) { exercise, group -> exercise.copy(supersetGroup = group) }
