package app.liora.feature.logger.done

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.progression.ProgressionRepository
import app.liora.core.data.progression.StepsEarned
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.WeekProgressRepository
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.domain.AgainstLastTime
import app.liora.core.domain.Earned
import app.liora.core.domain.ExerciseProgress
import app.liora.core.domain.HistoryRecords
import app.liora.core.domain.LastTimeComparison
import app.liora.core.domain.OneRepMaxFormula
import app.liora.core.domain.RecordHighlights
import app.liora.core.domain.RecordKey
import app.liora.core.domain.WeekProgress
import app.liora.core.domain.volumeOf
import app.liora.core.model.Exercise
import app.liora.core.model.FinishedWorkout
import app.liora.core.model.LoggedSet
import app.liora.core.model.TrackingType
import app.liora.core.model.WorkoutExercise
import app.liora.core.ui.RecordsCardEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DayOfWeek

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
        /** What its sets earned against its routine's rep ranges. */
        val earned: StepsEarned = StepsEarned(),
        /** The session before it of each of its exercises: its completed sets, by exercise id. */
        val previous: Map<String, List<LoggedSet>> = emptyMap(),
        /** This week against the weekly goal, with this workout; null until worked out. */
        val week: WeekProgress? = null,
    ) : WorkoutDoneUiState {
        val sets: Int = workout.exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } }

        /** The workout against last time, set by set as the logger raced it. */
        val race: AgainstLastTime = LastTimeComparison.of(workout.exercises, previous, ::trackingTypeOf, formula)

        private val exerciseIds: List<String> = workout.exercises.map { it.exerciseId }.distinct()

        /** The steps up earned, in workout order. */
        val steps: List<StepEntry> =
            exerciseIds.mapNotNull { id ->
                earned.steps[id]?.let { StepEntry(exercises[id]?.name.orEmpty(), it) }
            }

        /** The names of the exercises without progression that reached the top of every range, in workout order. */
        val readyForMore: List<String> =
            exerciseIds.filter { it in earned.readyForMore }.mapNotNull { exercises[it]?.name }

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

/** A step up the workout earned: the exercise's name, and the weight it goes to, or the top of the stack. */
data class StepEntry(
    val exerciseName: String,
    val earned: Earned,
)

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

/**
 * The summary of a workout just finished: what it came to, the steps up and records it earned, and each
 * exercise's best set.
 */
class WorkoutDoneViewModel(
    private val workoutId: String,
    history: WorkoutHistoryRepository,
    exerciseRepository: ExerciseRepository,
    settings: SettingsRepository,
    routines: RoutineRepository,
    progression: ProgressionRepository,
    weeks: WeekProgressRepository,
) : ViewModel() {
    private val language = MutableStateFlow<String?>(null)

    /** Where the locale starts its weeks. */
    private val firstDayOfWeek = MutableStateFlow<DayOfWeek?>(null)

    /** This week against the goal; null until the locale's week is known, so the summary doesn't wait. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val week: Flow<WeekProgress?> =
        firstDayOfWeek
            .filterNotNull()
            .flatMapLatest { weeks.progress(it) }
            .onStart<WeekProgress?> { emit(null) }

    // Waited for, unlike elsewhere: without them a record could show for a moment under the wrong measure,
    // and the burst would go off for it.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val exercises =
        language
            .filterNotNull()
            .flatMapLatest { exerciseRepository.observeExercises(it) }
            .map { all -> all.associateBy { it.id } }

    /** What the workout earned against its routine's rep ranges; nothing for one not started from a routine. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val earned =
        history.workouts
            .map { workouts -> workouts.firstOrNull { it.id == workoutId } }
            .distinctUntilChanged()
            .mapLatest { workout ->
                val routine = workout?.routineId?.let { routines.get(it) }
                if (workout == null ||
                    routine == null
                ) {
                    StepsEarned()
                } else {
                    progression.earned(routine, workout.exercises)
                }
            }

    /** Each exercise's session before this workout, as the logger's Previous column had it. */
    private val previous = history.edit(workoutId).previousSets

    val uiState: StateFlow<WorkoutDoneUiState> =
        combine(
            history.workouts,
            exercises,
            settings.settings,
            earned,
            previous,
        ) { workouts, exercisesById, chosen, steps, lastTime ->
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
                earned = steps,
                // Without racing last time (Settings › Workout), nothing to race against.
                previous = if (chosen.raceLastTime) lastTime else emptyMap(),
            )
        }.combine(week) { state, progress ->
            if (state is WorkoutDoneUiState.Loaded) state.copy(week = progress) else state
        }
            // Records are measured against the whole history; keep that off the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDoneUiState.Loading)

    fun setLanguage(value: String) {
        language.value = value
    }

    fun setFirstDayOfWeek(value: DayOfWeek) {
        firstDayOfWeek.value = value
    }
}
