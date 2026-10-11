package app.liora.feature.settings.importing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.backup.ExportFileException
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.importing.FolderPlan
import app.liora.core.data.importing.RoutineFileProblem
import app.liora.core.data.importing.RoutineImportExercise
import app.liora.core.data.importing.RoutineImportPreview
import app.liora.core.data.importing.RoutineImportReading
import app.liora.core.data.importing.RoutineImportRepository
import app.liora.core.data.importing.RoutineImportResult
import app.liora.core.domain.importing.ExerciseChoice
import app.liora.core.model.Exercise
import app.liora.core.navigation.RoutineImportRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RoutineImportUiState {
    /** Nothing to read yet: the way to open a file or paste one. */
    data object Start : RoutineImportUiState

    data object Reading : RoutineImportUiState

    /** The file couldn't be opened. */
    data object Unreadable : RoutineImportUiState

    /** The file can't be imported: what's wrong with it, errors first. */
    data class Invalid(
        val problems: List<RoutineFileProblem>,
    ) : RoutineImportUiState

    /** What the file holds, and where each exercise it names goes. */
    data class Review(
        val folder: FolderPlan?,
        val routines: List<RoutineSummary>,
        /** Names that become new exercises unless the user says otherwise, the file's own among them. */
        val newExercises: List<ImportExerciseRow>,
        /** Names that match an exercise here. */
        val matchedExercises: List<ImportExerciseRow>,
        /** What's left out or changed, such as fields Liora doesn't know. */
        val warnings: List<RoutineFileProblem>,
        val importing: Boolean = false,
    ) : RoutineImportUiState

    data class Done(
        val result: RoutineImportResult,
    ) : RoutineImportUiState
}

/** A routine in a file, in brief. */
data class RoutineSummary(
    val name: String,
    val exercises: Int,
    val sets: Int,
)

/**
 * Reads a routine file (opened, shared to Liora or pasted), lets the user change where each exercise goes,
 * and imports it. What was pasted is kept with the screen, so it survives Android ending the process; the
 * import finishes even if the screen closes (see [RoutineImportRepository]).
 */
class RoutineImportViewModel(
    route: RoutineImportRoute,
    language: String,
    private val imports: RoutineImportRepository,
    exercises: ExerciseRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val phase = MutableStateFlow<Phase>(Phase.Start)
    private val choices = MutableStateFlow<Map<String, ExerciseChoice>>(emptyMap())

    /** The name whose exercise is being picked from the library. */
    private var picking: String? = null

    val uiState: StateFlow<RoutineImportUiState> =
        combine(phase, choices, exercises.observeExercises(language)) { phase, choices, all ->
            when (phase) {
                Phase.Start -> RoutineImportUiState.Start
                Phase.Reading -> RoutineImportUiState.Reading
                Phase.Unreadable -> RoutineImportUiState.Unreadable
                is Phase.Invalid -> RoutineImportUiState.Invalid(phase.problems)
                is Phase.Done -> RoutineImportUiState.Done(phase.result)
                is Phase.Review -> review(phase, choices, all.associateBy { it.id })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RoutineImportUiState.Start)

    init {
        val pasted = savedState.get<String>(PASTED)
        val source = savedState.get<String>(SOURCE) ?: route.source
        when {
            pasted != null -> read { imports.read(pasted) }
            source != null -> read { imports.readFile(source) }
        }
    }

    /** Reads the file the user picked. */
    fun open(source: String) {
        savedState[SOURCE] = source
        savedState.remove<String>(PASTED)
        read { imports.readFile(source) }
    }

    /** Reads what the user pasted; nothing pasted reads as no routine at all. */
    fun paste(text: String?) {
        savedState[PASTED] = text.orEmpty()
        savedState.remove<String>(SOURCE)
        read { imports.read(text.orEmpty()) }
    }

    /** Back to the start, to open or paste another. */
    fun startOver() {
        savedState.remove<String>(PASTED)
        savedState.remove<String>(SOURCE)
        choices.value = emptyMap()
        phase.value = Phase.Start
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

    fun import() {
        val review = phase.value as? Phase.Review ?: return
        if (review.importing) return
        phase.value = review.copy(importing = true)
        viewModelScope.launch {
            phase.value = Phase.Done(imports.import(review.preview, choices.value))
        }
    }

    private fun read(reading: suspend () -> RoutineImportReading) {
        phase.value = Phase.Reading
        choices.value = emptyMap()
        viewModelScope.launch {
            phase.value =
                try {
                    when (val result = reading()) {
                        is RoutineImportReading.Ready -> Phase.Review(result.preview)
                        is RoutineImportReading.Invalid -> Phase.Invalid(result.problems)
                    }
                } catch (_: ExportFileException) {
                    Phase.Unreadable
                }
        }
    }

    private fun review(
        phase: Phase.Review,
        choices: Map<String, ExerciseChoice>,
        exercises: Map<String, Exercise>,
    ): RoutineImportUiState.Review {
        val preview = phase.preview
        val rows = preview.exercises.map { it to it.toRow(choices[it.name] ?: it.suggestion.choice, exercises) }
        return RoutineImportUiState.Review(
            folder = preview.folder,
            routines =
                preview.routines.map { routine ->
                    RoutineSummary(routine.name, routine.exercises.size, routine.exercises.sumOf { it.sets.size })
                },
            newExercises = rows.filter { (exercise, _) -> exercise.isNew }.map { it.second },
            matchedExercises = rows.filterNot { (exercise, _) -> exercise.isNew }.map { it.second },
            warnings = preview.warnings,
            importing = phase.importing,
        )
    }

    private sealed interface Phase {
        data object Start : Phase

        data object Reading : Phase

        data object Unreadable : Phase

        data class Invalid(
            val problems: List<RoutineFileProblem>,
        ) : Phase

        data class Review(
            val preview: RoutineImportPreview,
            val importing: Boolean = false,
        ) : Phase

        data class Done(
            val result: RoutineImportResult,
        ) : Phase
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PASTED = "pasted"
        const val SOURCE = "source"
    }
}

/** Suggested as a new exercise: what the review lists first. */
private val RoutineImportExercise.isNew: Boolean get() = suggestion.choice is ExerciseChoice.New

private fun RoutineImportExercise.toRow(
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
        title = if (fixed) target?.name ?: name else custom?.name ?: name,
        fixed = fixed,
    )
}
