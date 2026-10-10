package app.liora.feature.logger.done

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.domain.ExerciseProgress
import app.liora.core.domain.HistoryRecords
import app.liora.core.domain.OneRepMaxFormula
import app.liora.core.domain.RecordHighlights
import app.liora.core.domain.RecordKey
import app.liora.core.domain.volumeOf
import app.liora.core.model.Exercise
import app.liora.core.model.FinishedWorkout
import app.liora.core.model.LoggedSet
import app.liora.core.model.TrackingType
import app.liora.core.model.WorkoutExercise
import app.liora.core.ui.RecordsCardEntry
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
import kotlinx.coroutines.flow.stateIn

sealed interface WorkoutDoneUiState {
    data object Loading : WorkoutDoneUiState

    /** Deleted meanwhile, on another device say. */
    data object Gone : WorkoutDoneUiState

    data class Loaded(
        val workout: FinishedWorkout,
        val exercises: Map<String, Exercise>,
        /** The records each of this workout's sets broke, by set id. */
        val records: Map<String, Set<RecordKey>>,
        /** How many workouts there are up to this one, this one included: 1 for the first. */
        val number: Int,
        /** How estimated one-rep maxes are worked out, for the records and the best sets. */
        val formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    ) : WorkoutDoneUiState {
        val sets: Int = workout.exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } }

        val volumeKg: Double = workout.exercises.sumOf { volumeOf(trackingTypeOf(it.exerciseId), it.sets) }

        /** The records worth naming, by exercise in workout order, at the values they were set at. */
        val recordEntries: List<RecordsCardEntry> =
            workout.exercises.mapNotNull { exercise ->
                val trackingType = trackingTypeOf(exercise.exerciseId)
                RecordHighlights
                    .of(exercise.sets, records, trackingType, formula)
                    .takeIf { it.isNotEmpty() }
                    ?.let { RecordsCardEntry(exercises[exercise.exerciseId]?.name.orEmpty(), trackingType, it) }
            }

        /** Counted like the finish sheet and workout detail count them, so the numbers match. */
        val recordCount: Int = recordEntries.sumOf { it.records.size }

        /** Each exercise done, in workout order, with its best set. */
        val highlights: List<ExerciseHighlight> =
            workout.exercises
                .filter { exercise -> exercise.sets.any { it.isCompleted } }
                .map { done ->
                    val trackingType = trackingTypeOf(done.exerciseId)
                    val best = ExerciseProgress.bestSet(trackingType, done.sets, formula)
                    ExerciseHighlight(
                        done = done,
                        exercise = exercises[done.exerciseId],
                        trackingType = trackingType,
                        sets = done.sets.count { it.isCompleted },
                        best = best,
                        bestIsRecord = best != null && !records[best.id].isNullOrEmpty(),
                    )
                }

        fun trackingTypeOf(exerciseId: String): TrackingType =
            exercises[exerciseId]?.trackingType ?: TrackingType.WeightReps
    }
}

/** One exercise of the workout as it went: how many sets, and the best of them. */
data class ExerciseHighlight(
    val done: WorkoutExercise,
    val exercise: Exercise?,
    val trackingType: TrackingType,
    val sets: Int,
    /** Null when it had only warm-ups. */
    val best: LoggedSet?,
    val bestIsRecord: Boolean,
)

/** The summary of a workout just finished: what it came to, its records and each exercise's best set. */
class WorkoutDoneViewModel(
    private val workoutId: String,
    history: WorkoutHistoryRepository,
    exerciseRepository: ExerciseRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val language = MutableStateFlow<String?>(null)

    // Waited for, unlike elsewhere: without them a record could show for a moment under the wrong measure,
    // and the burst would go off for it.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val exercises =
        language
            .filterNotNull()
            .flatMapLatest { exerciseRepository.observeExercises(it) }
            .map { all -> all.associateBy { it.id } }

    val uiState: StateFlow<WorkoutDoneUiState> =
        combine(history.workouts, exercises, settings.settings) { workouts, exercisesById, chosen ->
            val workout = workouts.firstOrNull { it.id == workoutId } ?: return@combine WorkoutDoneUiState.Gone
            val records =
                HistoryRecords.of(workouts, chosen.oneRepMaxFormula) {
                    exercisesById[it]?.trackingType ?: TrackingType.WeightReps
                }
            val setIds = workout.exercises.flatMap { exercise -> exercise.sets.map { it.id } }.toSet()
            WorkoutDoneUiState.Loaded(
                workout = workout,
                exercises = exercisesById,
                records = records.filterKeys { it in setIds },
                number = workouts.count { it.startedAt <= workout.startedAt },
                formula = chosen.oneRepMaxFormula,
            )
        }
            // Records are measured against the whole history; keep that off the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDoneUiState.Loading)

    fun setLanguage(value: String) {
        language.value = value
    }
}
