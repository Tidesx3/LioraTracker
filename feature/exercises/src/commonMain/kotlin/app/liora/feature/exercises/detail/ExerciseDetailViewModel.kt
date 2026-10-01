package app.liora.feature.exercises.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseInUseException
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.domain.ExerciseProgress
import app.liora.core.domain.PersonalRecord
import app.liora.core.domain.PersonalRecords
import app.liora.core.domain.ProgressMetric
import app.liora.core.domain.ProgressPoint
import app.liora.core.domain.RecordType
import app.liora.core.domain.Stall
import app.liora.core.domain.Stalls
import app.liora.core.model.Exercise
import app.liora.core.model.ExerciseSession
import app.liora.core.model.TrackingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

sealed interface ExerciseDetailUiState {
    data object Loading : ExerciseDetailUiState

    /** The exercise no longer exists (e.g. just deleted); the screen closes itself. */
    data object Gone : ExerciseDetailUiState

    data class Loaded(
        val exercise: Exercise,
        /** The built-in a custom variation came from, for the "Variation of …" line. */
        val baseName: String?,
        /** Null until the exercise has been trained. */
        val progress: ExerciseProgressState?,
        val dialog: DetailDialog?,
    ) : ExerciseDetailUiState
}

/** What the exercise's sessions add up to: a chart, its records, whether it stalled, and the sessions. */
data class ExerciseProgressState(
    val trackingType: TrackingType,
    /** Newest first. */
    val sessions: List<ExerciseSession>,
    /** What can be charted, the main metric first. */
    val metrics: List<ProgressMetric>,
    val metric: ProgressMetric,
    /** [metric] per session, oldest first. */
    val points: List<ProgressPoint>,
    /** The best ever in each record but rep maxes, in a fixed order. */
    val records: List<PersonalRecord>,
    /** Rep maxes by rep count, for exercises lifted for reps. */
    val repMaxes: List<PersonalRecord>,
    val stall: Stall?,
    /** Whole weeks since the best still standing, while stalled. */
    val stallWeeks: Int?,
) {
    val best: ProgressPoint? =
        points.fold(null as ProgressPoint?) { best, point ->
            if (best == null || ExerciseProgress.improves(metric, point.value, best.value)) point else best
        }

    companion object {
        fun of(
            trackingType: TrackingType,
            sessions: List<ExerciseSession>,
            chosen: ProgressMetric?,
            clock: Clock,
        ): ExerciseProgressState? {
            if (sessions.isEmpty()) return null
            val metrics = ExerciseProgress.metricsFor(trackingType)
            val metric = chosen?.takeIf { it in metrics } ?: metrics.first()
            val all = PersonalRecords.compute(trackingType, sessions.flatMap { it.sets }).values
            val (repMaxes, records) = all.partition { it.key.type == RecordType.RepMax }
            val now = clock.now()
            val stall = Stalls.of(trackingType, sessions, now)
            return ExerciseProgressState(
                trackingType = trackingType,
                sessions = sessions.reversed(),
                metrics = metrics,
                metric = metric,
                points = ExerciseProgress.series(trackingType, sessions, metric),
                records = records.sortedBy { it.key.type.ordinal },
                repMaxes = telling(repMaxes),
                stall = stall,
                stallWeeks = stall?.let { ((now - it.best.at).inWholeDays / DAYS_PER_WEEK).toInt() },
            )
        }
    }
}

/**
 * The rep maxes that say something, by rep count: 87.5 kg for 5 reps already covers 1 to 4 reps at
 * that weight, so each weight shows only with the most reps it was lifted for.
 */
private fun telling(repMaxes: List<PersonalRecord>): List<PersonalRecord> {
    val byReps = repMaxes.associateBy { it.key.reps }
    return repMaxes
        .sortedBy { it.key.reps }
        .filter { record ->
            val next = byReps[(record.key.reps ?: 0) + 1]
            next == null || next.value < record.value
        }
}

private const val DAYS_PER_WEEK = 7

enum class DetailDialog { ConfirmDelete, InUse }

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModel(
    private val exerciseId: String,
    private val repository: ExerciseRepository,
    history: WorkoutHistoryRepository,
    clock: Clock,
) : ViewModel() {
    private val language = MutableStateFlow<String?>(null)
    private val dialog = MutableStateFlow<DetailDialog?>(null)
    private val metric = MutableStateFlow<ProgressMetric?>(null)

    private val exercise =
        language
            .filterNotNull()
            .flatMapLatest { lang ->
                repository.observeExercise(exerciseId, lang).flatMapLatest { exercise ->
                    val base = exercise?.variationOf?.let { repository.observeExercise(it, lang) } ?: flowOf(null)
                    base.map { exercise to it?.name }
                }
            }

    val uiState: StateFlow<ExerciseDetailUiState> =
        combine(
            exercise,
            history.sessionsOf(exerciseId),
            metric,
            dialog,
        ) { (exercise, baseName), sessions, chosen, open ->
            if (exercise == null) {
                ExerciseDetailUiState.Gone
            } else {
                val progress = ExerciseProgressState.of(exercise.trackingType, sessions, chosen, clock)
                ExerciseDetailUiState.Loaded(exercise, baseName, progress, open)
            }
        }
            // Records and charts go through every session of the exercise; keep that off the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState.Loading)

    fun setLanguage(value: String) {
        language.value = value
    }

    fun selectMetric(value: ProgressMetric) {
        metric.value = value
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
