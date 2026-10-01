package app.liora.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.domain.BoardEntry
import app.liora.core.domain.MuscleTargets
import app.liora.core.domain.RecordsBoard
import app.liora.core.domain.TrainingCalendar
import app.liora.core.domain.setsPerMuscle
import app.liora.core.model.Exercise
import app.liora.core.model.FinishedWorkout
import app.liora.core.model.Muscle
import app.liora.core.model.TrackingType
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.time.Clock

sealed interface ProgressUiState {
    data object Loading : ProgressUiState

    /** Nothing finished yet. */
    data object Empty : ProgressUiState

    data class Loaded(
        val today: LocalDate,
        /** Weeks in a row with training. */
        val streakWeeks: Int,
        val workoutsThisWeek: Int,
        val setsThisWeek: Int,
        /** Every day with a finished workout, for the consistency grid. */
        val trainingDays: Set<LocalDate>,
        /** The week the muscle section shows, by its first day. */
        val muscleWeek: LocalDate,
        val isCurrentWeek: Boolean,
        /** Whether there was training before [muscleWeek], to page back to. */
        val hasEarlierWeeks: Boolean,
        val setsPerMuscle: Map<Muscle, Double>,
        /** Each trained exercise's standing best, newest record first. */
        val board: List<BoardRow>,
    ) : ProgressUiState
}

/** A row of the records board: the exercise and its best. */
data class BoardRow(
    val name: String,
    val trackingType: TrackingType,
    val entry: BoardEntry,
    /** Whole weeks since the best, while stalled. */
    val stallWeeks: Int?,
)

/** The Progress tab: streaks and consistency, sets per muscle week by week, and the records board. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    history: WorkoutHistoryRepository,
    exerciseRepository: ExerciseRepository,
    private val clock: Clock,
) : ViewModel() {
    private val language = MutableStateFlow<String?>(null)
    private val firstDayOfWeek = MutableStateFlow(DayOfWeek.MONDAY)

    /** 0 for this week, -1 for last week, and so on. */
    private val weekOffset = MutableStateFlow(0)

    private val exercises =
        language
            .filterNotNull()
            .flatMapLatest { exerciseRepository.observeExercises(it) }
            .map { all -> all.associateBy { it.id } }
            .onStart { emit(emptyMap()) }

    val uiState: StateFlow<ProgressUiState> =
        combine(history.workouts, exercises, firstDayOfWeek, weekOffset) { workouts, byId, firstDay, offset ->
            if (workouts.isEmpty()) ProgressUiState.Empty else loaded(workouts, byId, firstDay, offset)
        }
            // The board goes through every set ever logged; keep that off the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState.Loading)

    /** The app's language, for exercise names, and its locale's first day of the week. */
    fun setLocale(
        language: String,
        firstDayOfWeek: DayOfWeek,
    ) {
        this.language.value = language
        this.firstDayOfWeek.value = firstDayOfWeek
    }

    fun previousWeek() {
        weekOffset.update { it - 1 }
    }

    fun nextWeek() {
        weekOffset.update { (it + 1).coerceAtMost(0) }
    }

    private fun loaded(
        workouts: List<FinishedWorkout>,
        exercisesById: Map<String, Exercise>,
        firstDay: DayOfWeek,
        offset: Int,
    ): ProgressUiState.Loaded {
        val zone = TimeZone.currentSystemDefault()
        val now = clock.now()
        val today = TrainingCalendar.dayOf(now, zone)
        val dayOf = { workout: FinishedWorkout -> TrainingCalendar.dayOf(workout.startedAt, zone) }
        val thisWeek = TrainingCalendar.weekStart(today, firstDay)
        val muscleWeek = thisWeek.plus(offset * DAYS_PER_WEEK, DateTimeUnit.DAY)
        val inWeek = { start: LocalDate ->
            val end = start.plus(DAYS_PER_WEEK, DateTimeUnit.DAY)
            workouts.filter { dayOf(it) >= start && dayOf(it) < end }
        }
        val targetsOf = { id: String ->
            exercisesById[id]?.let { MuscleTargets(it.primaryMuscles, it.secondaryMuscles) }
        }
        val current = inWeek(thisWeek)
        val trainingDays = workouts.map(dayOf).toSet()
        return ProgressUiState.Loaded(
            today = today,
            streakWeeks = TrainingCalendar.weekStreak(trainingDays, today, firstDay),
            workoutsThisWeek = current.size,
            setsThisWeek =
                current.sumOf { workout ->
                    workout.exercises.sumOf { it.sets.count { set -> set.isWorkingSet } }
                },
            trainingDays = trainingDays,
            muscleWeek = muscleWeek,
            isCurrentWeek = offset == 0,
            hasEarlierWeeks = trainingDays.any { it < muscleWeek },
            setsPerMuscle = setsPerMuscle(inWeek(muscleWeek), targetsOf),
            board =
                RecordsBoard.of(workouts, { exercisesById[it]?.trackingType }, now).mapNotNull { entry ->
                    exercisesById[entry.exerciseId]?.let { exercise ->
                        val stallWeeks = entry.stall?.let { ((now - it.best.at).inWholeDays / DAYS_PER_WEEK).toInt() }
                        BoardRow(exercise.name, exercise.trackingType, entry, stallWeeks)
                    }
                },
        )
    }

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}
