package app.liora.feature.progress.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.domain.MonthlyReport
import app.liora.core.domain.MonthlyReports
import app.liora.core.domain.MuscleTargets
import app.liora.core.domain.TrainingCalendar
import app.liora.core.model.Exercise
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minusMonth
import kotlinx.datetime.yearMonth
import kotlin.time.Clock

sealed interface MonthlyReportUiState {
    data object Loading : MonthlyReportUiState

    data class Loaded(
        val report: MonthlyReport,
        val exercises: Map<String, Exercise>,
        /** Whether there are workouts before this month, to page back to. */
        val hasEarlier: Boolean,
        /** Whether this is the current month, the last one to page to. */
        val isCurrent: Boolean,
    ) : MonthlyReportUiState {
        fun nameOf(exerciseId: String): String = exercises[exerciseId]?.name.orEmpty()

        fun trackingTypeOf(exerciseId: String): TrackingType =
            exercises[exerciseId]?.trackingType ?: TrackingType.WeightReps
    }
}

/** A month looked back on, starting with the current one, paging back to the first with training. */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyReportViewModel(
    history: WorkoutHistoryRepository,
    exerciseRepository: ExerciseRepository,
    private val clock: Clock,
) : ViewModel() {
    private val language = MutableStateFlow<String?>(null)

    /** Months back from the current one: 0 now, -1 the month before. */
    private val offset = MutableStateFlow(0)

    private val exercises =
        language
            .filterNotNull()
            .flatMapLatest { exerciseRepository.observeExercises(it) }
            .map { all -> all.associateBy { it.id } }
            .onStart { emit(emptyMap()) }

    val uiState: StateFlow<MonthlyReportUiState> =
        combine(history.workouts, exercises, offset) { workouts, byId, back ->
            val zone = TimeZone.currentSystemDefault()
            val current = TrainingCalendar.dayOf(clock.now(), zone).yearMonth
            var month = current
            repeat(-back) { month = month.minusMonth() }
            val report =
                MonthlyReports.of(
                    workouts = workouts,
                    month = month,
                    zone = zone,
                    trackingTypeOf = { byId[it]?.trackingType ?: TrackingType.WeightReps },
                    targetsOf = { id -> byId[id]?.let { MuscleTargets(it.primaryMuscles, it.secondaryMuscles) } },
                )
            MonthlyReportUiState.Loaded(
                report = report,
                exercises = byId,
                hasEarlier = workouts.any { TrainingCalendar.dayOf(it.startedAt, zone).yearMonth < month },
                isCurrent = month >= current,
            )
        }
            // Records are measured against all of history; keep that off the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthlyReportUiState.Loading)

    fun setLanguage(value: String) {
        language.value = value
    }

    fun previousMonth() {
        offset.update { it - 1 }
    }

    fun nextMonth() {
        offset.update { (it + 1).coerceAtMost(0) }
    }
}
