package app.liora.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.RestTimerRepository
import app.liora.core.data.workout.SetCompletion
import app.liora.core.data.workout.SetLogger
import app.liora.core.data.workout.WorkoutEditor
import app.liora.core.domain.RestDefaults
import app.liora.core.domain.SetField
import app.liora.core.domain.SetPlaceholders
import app.liora.core.domain.SetRef
import app.liora.core.domain.WorkoutOrder
import app.liora.core.domain.find
import app.liora.core.domain.setAt
import app.liora.core.domain.volumeOf
import app.liora.core.model.ActiveWorkout
import app.liora.core.model.Exercise
import app.liora.core.model.LoggedSet
import app.liora.core.model.RestTimer
import app.liora.core.model.SetType
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

/** A value in a set row: which set, which column. */
data class CellRef(
    val setId: String,
    val field: SetField,
)

/** The cell being typed into. [text] stays null until the first key, which then replaces the value. */
data class CellEdit(
    val cell: CellRef,
    val text: String? = null,
)

sealed interface LoggerUiState {
    /** Waiting for the database; renders nothing so there is no flash of "no workout". */
    data object Loading : LoggerUiState

    data object NoWorkout : LoggerUiState

    data class Active(
        val workout: ActiveWorkout,
        val exercises: Map<String, Exercise>,
        /** Last session's completed sets per exercise id. */
        val previous: Map<String, List<LoggedSet>>,
        val restTimer: RestTimer?,
        val restDefaults: RestDefaults,
        val edit: CellEdit?,
        /** A field that kept a set from being ticked off; shown as an error until it gets a value. */
        val invalid: CellRef?,
    ) : LoggerUiState {
        /** The set that's up next. */
        val current: SetRef? = WorkoutOrder.current(workout)

        val completedSets: Int = workout.exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } }

        val volumeKg: Double =
            workout.exercises.sumOf { exercise -> volumeOf(trackingTypeOf(exercise.exerciseId), exercise.sets) }

        fun trackingTypeOf(exerciseId: String): TrackingType =
            exercises[exerciseId]?.trackingType ?: TrackingType.WeightReps

        /** What an empty field of [ref] shows greyed out and logs when ticked without typing. */
        fun placeholderFor(ref: SetRef): LoggedSet {
            val exercise = workout.exercises[ref.exerciseIndex]
            val previousSet =
                SetPlaceholders.previousFor(exercise.sets, ref.setIndex, previous[exercise.exerciseId].orEmpty())
            return SetPlaceholders.fill(
                workout.setAt(ref).copy(weight = null, reps = null, duration = null, distanceMeters = null),
                previousSet,
            )
        }

        fun previousFor(ref: SetRef): LoggedSet? {
            val exercise = workout.exercises[ref.exerciseIndex]
            return SetPlaceholders.previousFor(exercise.sets, ref.setIndex, previous[exercise.exerciseId].orEmpty())
        }
    }
}

/** Where the logger hears back from the exercise picker. */
internal object PickerKeys {
    const val ADD = "logger.add"
    const val REPLACE = "logger.replace"
}

@Suppress("TooManyFunctions") // one function per user intent
class LoggerViewModel(
    private val activeWorkouts: ActiveWorkoutRepository,
    private val editor: WorkoutEditor,
    private val sets: SetLogger,
    private val restTimers: RestTimerRepository,
    private val restDefaults: RestDefaults,
    exerciseRepository: ExerciseRepository,
) : ViewModel() {
    private val language = MutableStateFlow<String?>(null)
    private val edit = MutableStateFlow<CellEdit?>(null)
    private val invalid = MutableStateFlow<CellRef?>(null)
    private val writes = Mutex()

    /** The exercise being replaced while the picker is open. */
    private var replacing: String? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val exercises =
        language
            .filterNotNull()
            .flatMapLatest { exerciseRepository.observeExercises(it) }
            .map { all -> all.associateBy { it.id } }
            .flowOn(Dispatchers.Default)
            .onStart { emit(emptyMap()) }

    private val editing = combine(edit, invalid) { cell, error -> cell to error }

    val uiState: StateFlow<LoggerUiState> =
        combine(
            activeWorkouts.activeWorkout,
            activeWorkouts.previousSets,
            exercises,
            restTimers.timer,
            editing,
        ) { workout, previous, byId, timer, (cell, error) ->
            if (workout == null) {
                LoggerUiState.NoWorkout
            } else {
                LoggerUiState.Active(
                    workout = workout,
                    exercises = byId,
                    previous = previous,
                    restTimer = timer,
                    restDefaults = restDefaults,
                    // A cell whose set was removed meanwhile is no longer being edited.
                    edit = cell?.takeIf { workout.find(it.cell.setId) != null },
                    invalid = error?.takeIf { workout.find(it.setId) != null },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoggerUiState.Loading)

    private val active: LoggerUiState.Active? get() = uiState.value as? LoggerUiState.Active

    fun setLanguage(value: String) {
        language.value = value
    }

    // Typing

    fun focus(cell: CellRef) {
        if (edit.value?.cell != cell) edit.value = CellEdit(cell)
    }

    /** Opens the pad on the set that's up next, e.g. when a key is pressed with nothing selected. */
    fun focusCurrent() {
        val state = active ?: return
        state.current?.let { focusFirstField(state, it) }
    }

    fun onKey(key: PadKey) {
        val current = edit.value
        if (current == null) {
            // Typing with nothing selected starts on the set that's up next.
            focusCurrent()
            if (key is PadKey.Digit || key == PadKey.Decimal) edit.value?.let { onKey(key) }
            return
        }
        val target = typingTarget(current) ?: return
        when (key) {
            is PadKey.Digit, PadKey.Decimal -> typeKey(current, target, key)
            PadKey.Backspace -> type(current.cell, current.text?.dropLast(1).orEmpty(), target.trackingType)
            PadKey.Increase, PadKey.Decrease -> step(current, target, up = key == PadKey.Increase)
            PadKey.Next -> next(current, target.trackingType)
            PadKey.Hide -> edit.value = null
        }
    }

    /** The set being typed into, with how its exercise is tracked. */
    private class TypingTarget(
        val ref: SetRef,
        val trackingType: TrackingType,
        val placeholder: LoggedSet,
    )

    private fun typingTarget(edit: CellEdit): TypingTarget? {
        val state = active ?: return null
        val ref = state.workout.find(edit.cell.setId) ?: return null
        return TypingTarget(
            ref,
            state.trackingTypeOf(state.workout.exercises[ref.exerciseIndex].exerciseId),
            state.placeholderFor(ref),
        )
    }

    private fun typeKey(
        current: CellEdit,
        target: TypingTarget,
        key: PadKey,
    ) {
        val text =
            current.cell.field
                .inputKind(target.trackingType)
                .append(current.text, key) ?: return
        type(current.cell, text, target.trackingType)
    }

    private fun step(
        current: CellEdit,
        target: TypingTarget,
        up: Boolean,
    ) {
        edit.value = current.copy(text = null)
        clearInvalid(current.cell)
        write(current.cell.setId) { stepped(current.cell.field, up, target.trackingType, target.placeholder) }
    }

    /** Moves along the set; on its last field, ticks it off. */
    private fun next(
        current: CellEdit,
        trackingType: TrackingType,
    ) {
        val fields = SetField.of(trackingType)
        val following = fields.getOrNull(fields.indexOf(current.cell.field) + 1)
        if (following !=
            null
        ) {
            edit.value = CellEdit(current.cell.copy(field = following))
        } else {
            complete(current.cell.setId)
        }
    }

    fun closePad() {
        edit.value = null
    }

    // Sets

    /** Ticks a set off, or reopens it if it was done. */
    fun toggleComplete(setId: String) {
        val state = active ?: return
        val ref = state.workout.find(setId) ?: return
        if (state.workout.setAt(ref).isCompleted) {
            viewModelScope.launch { sets.reopenSet(setId) }
        } else {
            complete(setId)
        }
    }

    /** Ticks off the set that's up next. */
    fun completeCurrent() {
        val state = active ?: return
        state.current?.let { complete(state.workout.setAt(it).id) }
    }

    /** Copies last session's values into a set, without ticking it off. */
    fun copyPrevious(setId: String) {
        val state = active ?: return
        val ref = state.workout.find(setId) ?: return
        val previous = state.previousFor(ref) ?: return
        write(setId) {
            copy(
                weight = previous.weight,
                reps = previous.reps,
                duration = previous.duration,
                distanceMeters = previous.distanceMeters,
            )
        }
    }

    fun setType(
        setId: String,
        type: SetType,
    ) = write(setId) { copy(type = type) }

    fun addSet(workoutExerciseId: String) = launch { sets.addSet(workoutExerciseId) }

    fun removeSet(setId: String) = launch { sets.removeSet(setId) }

    // Exercises

    fun addExercises(exerciseIds: List<String>) = launch { editor.addExercises(exerciseIds) }

    fun startReplacing(workoutExerciseId: String) {
        replacing = workoutExerciseId
    }

    fun onReplacementPicked(exerciseIds: List<String>) {
        val target = replacing ?: return
        replacing = null
        exerciseIds.firstOrNull()?.let { launch { editor.replaceExercise(target, it) } }
    }

    fun removeExercise(workoutExerciseId: String) = launch { editor.removeExercise(workoutExerciseId) }

    fun reorder(workoutExerciseIds: List<String>) = launch { editor.reorder(workoutExerciseIds) }

    fun linkWithNext(workoutExerciseId: String) = launch { editor.linkWithNext(workoutExerciseId) }

    fun unlink(workoutExerciseId: String) = launch { editor.unlink(workoutExerciseId) }

    fun setRest(
        workoutExerciseId: String,
        seconds: Int?,
    ) = launch { editor.setRest(workoutExerciseId, seconds) }

    fun setNotes(
        workoutExerciseId: String,
        notes: String?,
    ) = launch { editor.setNotes(workoutExerciseId, notes) }

    // Workout

    fun rename(name: String) = launch { activeWorkouts.rename(name) }

    fun adjustRest(bySeconds: Int) = launch { restTimers.adjust(bySeconds.seconds) }

    fun skipRest() = launch { restTimers.stop() }

    fun finish() = launch { activeWorkouts.finish() }

    fun discard() = launch { activeWorkouts.discard() }

    private fun complete(setId: String) {
        val before = active ?: return
        viewModelScope.launch {
            when (val result = writes.withLock { sets.completeSet(setId) }) {
                is SetCompletion.Logged -> {
                    invalid.value = null
                    // Typing carries on with the next set, so a whole workout can be logged on the pad.
                    if (edit.value != null) {
                        val next = nextOpenAfter(before.workout, setId)
                        if (next != null) focusFirstField(before, next) else edit.value = null
                    }
                }

                is SetCompletion.Missing -> {
                    val field = SetField.entries.firstOrNull { it in result.fields } ?: return@launch
                    val cell = CellRef(setId, field)
                    invalid.value = cell
                    edit.value = CellEdit(cell)
                }
            }
        }
    }

    private fun type(
        cell: CellRef,
        text: String,
        trackingType: TrackingType,
    ) {
        edit.value = CellEdit(cell, text)
        clearInvalid(cell)
        write(cell.setId) { withTyped(cell.field, text, trackingType) }
    }

    private fun clearInvalid(cell: CellRef) {
        invalid.update { if (it == cell) null else it }
    }

    private fun focusFirstField(
        state: LoggerUiState.Active,
        ref: SetRef,
    ) {
        val exercise = state.workout.exercises[ref.exerciseIndex]
        val field = SetField.of(state.trackingTypeOf(exercise.exerciseId)).firstOrNull() ?: return
        edit.value = CellEdit(CellRef(exercise.sets[ref.setIndex].id, field))
    }

    /** Keystrokes arrive faster than writes finish; the lock keeps them landing in order. */
    private fun write(
        setId: String,
        change: LoggedSet.() -> LoggedSet,
    ) = launch { writes.withLock { sets.updateSet(setId, change) } }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** The open set that follows [setId] once it is done, in the order the workout is worked through. */
private fun nextOpenAfter(
    workout: ActiveWorkout,
    setId: String,
): SetRef? {
    val order = WorkoutOrder.of(workout)
    val done = workout.find(setId) ?: return null
    val isOpen = { ref: SetRef -> ref != done && !workout.setAt(ref).isCompleted }
    return order.drop(order.indexOf(done) + 1).firstOrNull(isOpen) ?: order.firstOrNull(isOpen)
}
