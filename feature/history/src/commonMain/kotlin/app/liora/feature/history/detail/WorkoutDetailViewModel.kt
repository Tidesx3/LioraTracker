package app.liora.feature.history.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.domain.HistoryRecords
import app.liora.core.domain.OneRepMaxFormula
import app.liora.core.domain.RecordHighlights
import app.liora.core.domain.RecordKey
import app.liora.core.domain.volumeOf
import app.liora.core.model.Exercise
import app.liora.core.model.FinishedWorkout
import app.liora.core.model.TrackingType
import app.liora.core.ui.RecordsCardEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WorkoutDialog {
    data object SaveAsRoutine : WorkoutDialog

    data object Delete : WorkoutDialog

    /** Repeating needs the running workout finished or discarded first. */
    data object WorkoutInProgress : WorkoutDialog
}

sealed interface WorkoutDetailUiState {
    data object Loading : WorkoutDetailUiState

    /** Deleted, here or on another device. */
    data object Gone : WorkoutDetailUiState

    data class Loaded(
        val workout: FinishedWorkout,
        val exercises: Map<String, Exercise>,
        /** The records each of this workout's sets broke, by set id. */
        val records: Map<String, Set<RecordKey>>,
        val hasActiveWorkout: Boolean,
        val dialog: WorkoutDialog?,
        /** How estimated one-rep maxes are worked out, for the records' values. */
        val formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    ) : WorkoutDetailUiState {
        val sets: Int = workout.exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } }

        val volumeKg: Double = workout.exercises.sumOf { volumeOf(trackingTypeOf(it.exerciseId), it.sets) }

        /** The records worth naming, by exercise in workout order, at the values they were set at. */
        val recordEntries: List<RecordsCardEntry> =
            workout.exercises.mapNotNull { exercise ->
                val trackingType = exercises[exercise.exerciseId]?.trackingType ?: TrackingType.WeightReps
                RecordHighlights
                    .of(exercise.sets, records, trackingType, formula)
                    .takeIf { it.isNotEmpty() }
                    ?.let { RecordsCardEntry(exercises[exercise.exerciseId]?.name.orEmpty(), trackingType, it) }
            }

        /** Counted like the finish sheet counts them, so the numbers match. */
        val recordCount: Int = recordEntries.sumOf { it.records.size }

        fun trackingTypeOf(exerciseId: String): TrackingType =
            exercises[exerciseId]?.trackingType ?: TrackingType.WeightReps
    }
}

class WorkoutDetailViewModel(
    private val workoutId: String,
    private val history: WorkoutHistoryRepository,
    exerciseRepository: ExerciseRepository,
    settings: SettingsRepository,
    private val activeWorkouts: ActiveWorkoutRepository,
) : ViewModel() {
    private val language = MutableStateFlow<String?>(null)
    private val dialog = MutableStateFlow<WorkoutDialog?>(null)
    private val savedRoutine = MutableStateFlow<String?>(null)

    /** A routine just saved from this workout, until the screen has opened it. */
    val routineToOpen: StateFlow<String?> = savedRoutine.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val exercises =
        language
            .filterNotNull()
            .flatMapLatest { exerciseRepository.observeExercises(it) }
            .map { all -> all.associateBy { it.id } }
            .onStart { emit(emptyMap()) }

    /** The workout with the records its sets broke, measured against everything before it. */
    private val workoutWithRecords =
        combine(history.workouts, exercises, settings.settings) { workouts, exercisesById, chosen ->
            workouts.firstOrNull { it.id == workoutId }?.let { workout ->
                val records =
                    HistoryRecords.of(workouts, chosen.oneRepMaxFormula) {
                        exercisesById[it]?.trackingType ?: TrackingType.WeightReps
                    }
                val setIds = workout.exercises.flatMap { exercise -> exercise.sets.map { it.id } }.toSet()
                Found(workout, exercisesById, records.filterKeys { it in setIds }, chosen.oneRepMaxFormula)
            }
        }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<WorkoutDetailUiState> =
        combine(workoutWithRecords, activeWorkouts.activeWorkout, dialog) { found, active, openDialog ->
            if (found == null) {
                WorkoutDetailUiState.Gone
            } else {
                WorkoutDetailUiState.Loaded(
                    workout = found.workout,
                    exercises = found.exercises,
                    records = found.records,
                    hasActiveWorkout = active != null,
                    dialog = openDialog,
                    formula = found.formula,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailUiState.Loading)

    fun setLanguage(value: String) {
        language.value = value
    }

    /** Starts the workout again and returns true, or asks to finish the running one first and returns false. */
    fun repeat(): Boolean {
        if ((uiState.value as? WorkoutDetailUiState.Loaded)?.hasActiveWorkout == true) {
            dialog.value = WorkoutDialog.WorkoutInProgress
            return false
        }
        viewModelScope.launch { activeWorkouts.repeat(workoutId) }
        return true
    }

    fun showDialog(value: WorkoutDialog) {
        dialog.value = value
    }

    fun dismissDialog() {
        dialog.value = null
    }

    /** The screen opens the routine once it's saved (see [routineToOpen]), never before. */
    fun saveAsRoutine(name: String) {
        dialog.value = null
        viewModelScope.launch { savedRoutine.value = history.saveAsRoutine(workoutId, name) }
    }

    fun onRoutineOpened() {
        savedRoutine.value = null
    }

    /** The screen closes once the workout is gone, never before the delete lands. */
    fun delete() {
        dialog.value = null
        viewModelScope.launch { history.delete(workoutId) }
    }
}

/** The workout, its exercises and the records its sets broke. */
private class Found(
    val workout: FinishedWorkout,
    val exercises: Map<String, Exercise>,
    val records: Map<String, Set<RecordKey>>,
    val formula: OneRepMaxFormula,
)
