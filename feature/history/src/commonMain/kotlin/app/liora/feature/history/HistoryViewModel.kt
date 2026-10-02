package app.liora.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.domain.HistoryRecords
import app.liora.core.domain.OneRepMaxFormula
import app.liora.core.domain.TrainingCalendar
import app.liora.core.domain.volumeOf
import app.liora.core.model.Exercise
import app.liora.core.model.FinishedWorkout
import app.liora.core.model.TrackingType
import app.liora.core.ui.headlineRecords
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.Duration

/** One finished workout as history lists it. */
data class WorkoutItem(
    val id: String,
    val name: String?,
    /** When it started, where the user is. */
    val date: LocalDate,
    val time: LocalTime,
    val duration: Duration,
    val volumeKg: Double,
    val sets: Int,
    val records: Int,
    /** Its exercises in order, each with the number of sets done. */
    val exercises: List<ExerciseLine>,
)

data class ExerciseLine(
    val name: String,
    val sets: Int,
)

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data object Empty : HistoryUiState

    data class Loaded(
        /** Newest first. */
        val workouts: List<WorkoutItem>,
        val today: LocalDate,
    ) : HistoryUiState {
        /** The months with workouts, newest first. */
        val months: List<YearMonth> = workouts.map { it.date.yearMonth }.distinct()

        val trainingDays: Set<LocalDate> = workouts.map { it.date }.toSet()

        fun inMonth(month: YearMonth): List<WorkoutItem> = workouts.filter { it.date.yearMonth == month }
    }
}

class HistoryViewModel(
    history: WorkoutHistoryRepository,
    exerciseRepository: ExerciseRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val language = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val exercises =
        language
            .filterNotNull()
            .flatMapLatest { exerciseRepository.observeExercises(it) }
            .map { all -> all.associateBy { it.id } }
            .onStart { emit(emptyMap()) }

    val uiState: StateFlow<HistoryUiState> =
        combine(history.workouts, exercises, settings.settings) { workouts, exercisesById, chosen ->
            if (workouts.isEmpty()) {
                HistoryUiState.Empty
            } else {
                val zone = TimeZone.currentSystemDefault()
                HistoryUiState.Loaded(
                    workouts = items(workouts, exercisesById, zone, chosen.oneRepMaxFormula),
                    today = TrainingCalendar.dayOf(clock.now(), zone),
                )
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    fun setLanguage(value: String) {
        language.value = value
    }
}

/** What each workout added up to, including the records it set measured against everything before it. */
internal fun items(
    workouts: List<FinishedWorkout>,
    exercises: Map<String, Exercise>,
    zone: TimeZone,
    formula: OneRepMaxFormula,
): List<WorkoutItem> {
    val trackingTypeOf = { id: String -> exercises[id]?.trackingType ?: TrackingType.WeightReps }
    val records = HistoryRecords.of(workouts, formula, trackingTypeOf)
    return workouts.map { workout ->
        val start = workout.startedAt.toLocalDateTime(zone)
        WorkoutItem(
            id = workout.id,
            name = workout.name,
            date = start.date,
            time = start.time,
            duration = workout.duration,
            volumeKg = workout.exercises.sumOf { volumeOf(trackingTypeOf(it.exerciseId), it.sets) },
            sets = workout.exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } },
            // Counted the way the finish sheet counts them, so the numbers match.
            records =
                workout.exercises.sumOf { exercise ->
                    headlineRecords(exercise.sets.flatMap { records[it.id].orEmpty() }).size
                },
            exercises =
                workout.exercises.map { exercise ->
                    ExerciseLine(
                        name = exercises[exercise.exerciseId]?.name.orEmpty(),
                        sets = exercise.sets.count { it.isCompleted },
                    )
                },
        )
    }
}
