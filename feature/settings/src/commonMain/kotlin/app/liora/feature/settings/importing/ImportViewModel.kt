package app.liora.feature.settings.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.backup.ExportFileException
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.importing.ImportPreview
import app.liora.core.data.importing.ImportRepository
import app.liora.core.data.importing.ImportResult
import app.liora.core.data.importing.ImportedExerciseName
import app.liora.core.data.importing.UnknownImportException
import app.liora.core.domain.importing.ExerciseChoice
import app.liora.core.domain.importing.ImportSource
import app.liora.core.model.Exercise
import app.liora.core.model.WeightUnit
import app.liora.core.navigation.ImportRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Why a file can't be imported. */
enum class ImportProblem { NotAnExport, Unreadable }

sealed interface ImportUiState {
    data object Reading : ImportUiState

    data class Failed(
        val problem: ImportProblem,
    ) : ImportUiState

    /** What the file holds that isn't here yet, and where each exercise's sets would go. */
    data class Review(
        val source: ImportSource,
        val workouts: Int,
        val sets: Int,
        val measurements: Int,
        /** The first and last day of what's new. */
        val days: ClosedRange<LocalDate>?,
        val workoutsHere: Int,
        val measurementsHere: Int,
        /** The unit the file's weights are read in when it doesn't say; null when it does. */
        val assumedWeightUnit: WeightUnit?,
        /** Names that become new exercises unless the user says otherwise. */
        val newExercises: List<ImportExerciseRow>,
        /** Names that match an exercise here. */
        val matchedExercises: List<ImportExerciseRow>,
        val importing: Boolean = false,
    ) : ImportUiState {
        val isEmpty: Boolean get() = workouts == 0 && measurements == 0
    }

    data class Done(
        val result: ImportResult,
    ) : ImportUiState
}

/** An exercise as the file names it, and where its sets go. */
data class ImportExerciseRow(
    val name: String,
    val sets: Int,
    val choice: ExerciseChoice,
    /** The exercise here that [choice] uses, or that a new exercise is based on. */
    val target: Exercise?,
    /** The exercise here most like the name, offered as a choice. */
    val closest: Exercise?,
    /** Whether the exercise used has a column for everything logged; a new one always does. */
    val fits: Boolean,
)

/**
 * Reads a file from Hevy or Strong, lets the user change where each exercise goes, and imports it.
 * The import finishes even if the screen closes (see [ImportRepository]).
 */
class ImportViewModel(
    private val route: ImportRoute,
    language: String,
    private val imports: ImportRepository,
    exercises: ExerciseRepository,
) : ViewModel() {
    private val zone = TimeZone.currentSystemDefault()
    private val phase = MutableStateFlow<Phase>(Phase.Reading)
    private val choices = MutableStateFlow<Map<String, ExerciseChoice>>(emptyMap())

    /** The name whose exercise is being picked from the library. */
    private var picking: String? = null

    val uiState: StateFlow<ImportUiState> =
        combine(phase, choices, exercises.observeExercises(language)) { phase, choices, all ->
            when (phase) {
                Phase.Reading -> ImportUiState.Reading
                is Phase.Failed -> ImportUiState.Failed(phase.problem)
                is Phase.Done -> ImportUiState.Done(phase.result)
                is Phase.Review -> review(phase, choices, all.associateBy { it.id })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ImportUiState.Reading)

    init {
        viewModelScope.launch {
            phase.value =
                try {
                    Phase.Review(imports.read(route.source, zone))
                } catch (_: UnknownImportException) {
                    Phase.Failed(ImportProblem.NotAnExport)
                } catch (_: ExportFileException) {
                    Phase.Failed(ImportProblem.Unreadable)
                }
        }
    }

    fun choose(
        name: String,
        choice: ExerciseChoice,
    ) {
        choices.update { it + (name to choice) }
    }

    /** The exercise for [name] is being picked from the library; [onExercisePicked] hears back. */
    fun startPicking(name: String) {
        picking = name
    }

    fun onExercisePicked(exerciseId: String) {
        val name = picking ?: return
        picking = null
        choose(name, ExerciseChoice.Existing(exerciseId))
    }

    /** Reads the file again with its weights in [unit], for files that don't say theirs. */
    fun setWeightUnit(unit: WeightUnit) {
        val review = phase.value as? Phase.Review ?: return
        if (review.importing || review.preview.assumedWeightUnit == unit) return
        viewModelScope.launch {
            phase.value = Phase.Review(imports.withWeightUnit(review.preview, unit))
        }
    }

    fun import() {
        val review = phase.value as? Phase.Review ?: return
        if (review.importing || review.preview.isEmpty) return
        phase.value = review.copy(importing = true)
        viewModelScope.launch {
            phase.value = Phase.Done(imports.import(review.preview, choices.value))
        }
    }

    private fun review(
        phase: Phase.Review,
        choices: Map<String, ExerciseChoice>,
        exercises: Map<String, Exercise>,
    ): ImportUiState.Review {
        val preview = phase.preview
        val rows = preview.exercises.map { it.toRow(choices[it.name] ?: it.suggestion.choice, exercises) }
        val days =
            preview.workouts.map { it.startedAt.toLocalDateTime(zone).date } + preview.measurements.map { it.day }
        return ImportUiState.Review(
            source = preview.source,
            workouts = preview.workouts.size,
            sets = preview.sets,
            measurements = preview.measurements.size,
            days = days.minOrNull()?.let { first -> first..days.max() },
            workoutsHere = preview.workoutsHere,
            measurementsHere = preview.measurementsHere,
            assumedWeightUnit = preview.assumedWeightUnit,
            newExercises = rows.filterIndexed { index, _ -> preview.exercises[index].isNew },
            matchedExercises = rows.filterIndexed { index, _ -> !preview.exercises[index].isNew },
            importing = phase.importing,
        )
    }

    private sealed interface Phase {
        data object Reading : Phase

        data class Failed(
            val problem: ImportProblem,
        ) : Phase

        data class Review(
            val preview: ImportPreview,
            val importing: Boolean = false,
        ) : Phase

        data class Done(
            val result: ImportResult,
        ) : Phase
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Suggested as a new exercise: what the review lists first. */
private val ImportedExerciseName.isNew: Boolean get() = suggestion.choice is ExerciseChoice.New

private fun ImportedExerciseName.toRow(
    choice: ExerciseChoice,
    exercises: Map<String, Exercise>,
): ImportExerciseRow {
    val target =
        when (choice) {
            is ExerciseChoice.Existing -> exercises[choice.exerciseId]
            is ExerciseChoice.New -> choice.basedOn?.let(exercises::get)
        }
    return ImportExerciseRow(
        name = name,
        sets = sets,
        choice = choice,
        target = target,
        closest = suggestion.closest?.let(exercises::get),
        fits = choice !is ExerciseChoice.Existing || target == null || fields.fit(target.trackingType),
    )
}
