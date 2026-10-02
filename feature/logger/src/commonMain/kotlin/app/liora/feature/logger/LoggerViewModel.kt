package app.liora.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.gym.GymProfileRepository
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.FinishedWorkoutSession
import app.liora.core.data.workout.RestTimerRepository
import app.liora.core.data.workout.SetCompletion
import app.liora.core.data.workout.SetLogger
import app.liora.core.data.workout.WorkoutEditor
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.data.workout.WorkoutSession
import app.liora.core.domain.GymProfiles
import app.liora.core.domain.LoadRounding
import app.liora.core.domain.OneRepMaxFormula
import app.liora.core.domain.PlateLoad
import app.liora.core.domain.RecordKey
import app.liora.core.domain.RestDefaults
import app.liora.core.domain.RoutineUpdate
import app.liora.core.domain.SessionRecords
import app.liora.core.domain.SetField
import app.liora.core.domain.SetPlaceholders
import app.liora.core.domain.SetRef
import app.liora.core.domain.Settings
import app.liora.core.domain.WarmupGenerator
import app.liora.core.domain.WarmupSet
import app.liora.core.domain.WorkoutOrder
import app.liora.core.domain.WorkoutTimes
import app.liora.core.domain.find
import app.liora.core.domain.setAt
import app.liora.core.domain.volumeOf
import app.liora.core.model.ActiveWorkout
import app.liora.core.model.Equipment
import app.liora.core.model.Exercise
import app.liora.core.model.FinishedWorkout
import app.liora.core.model.GymProfile
import app.liora.core.model.LoggedSet
import app.liora.core.model.RestTimer
import app.liora.core.model.Routine
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import app.liora.core.ui.headlineRecords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
        /** Every earlier completed set per exercise id, which today's records are measured against. */
        val history: Map<String, List<LoggedSet>>,
        /** The routine the workout was started from, if any. */
        val routine: Routine?,
        val restTimer: RestTimer?,
        val restDefaults: RestDefaults,
        /** The units values are typed in. */
        val units: Units = Units(),
        /** Whether sets get an RPE column. */
        val rpe: Boolean = false,
        /** The gym weights round to: ± steps, plates on the bar, warm-ups. */
        val gym: GymProfile = GymProfiles.standard(WeightUnit.Kilogram),
        /** Every gym set up, to switch between. */
        val gyms: List<GymProfile> = emptyList(),
        /** How estimated one-rep maxes are worked out, for today's records. */
        val formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
        val edit: CellEdit?,
        /** A field that kept a set from being ticked off; shown as an error until it gets a value. */
        val invalid: CellRef?,
        /** When the workout ended, if it's a finished one opened from history to correct it. */
        val endedAt: Instant? = null,
    ) : LoggerUiState {
        /** A finished workout being corrected: no clock, no rest, and Done instead of Finish. */
        val isFinished: Boolean get() = endedAt != null

        /** The set that's up next. */
        val current: SetRef? = WorkoutOrder.current(workout)

        val completedSets: Int = workout.exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } }

        /** Sets not ticked off; finishing, or closing a finished workout, drops them. */
        val openSets: Int = workout.exercises.sumOf { exercise -> exercise.sets.count { !it.isCompleted } }

        val volumeKg: Double =
            workout.exercises.sumOf { exercise -> volumeOf(trackingTypeOf(exercise.exerciseId), exercise.sets) }

        /** The personal records each of today's sets broke, by set id. */
        val newRecords: Map<String, Set<RecordKey>> =
            workout.exercises
                .groupBy { it.exerciseId }
                .flatMap { (exerciseId, instances) ->
                    SessionRecords
                        .of(
                            trackingTypeOf(exerciseId),
                            history[exerciseId].orEmpty(),
                            instances.flatMap { it.sets },
                            formula,
                        ).entries
                }.associate { it.key to it.value }

        /** What finishing now would keep, and what it would change. */
        fun finishSummary(): FinishSummary {
            val updated = routine?.let { RoutineUpdate.fromWorkout(it, workout) { "" } }
            return FinishSummary(
                completedSets = completedSets,
                openSets = openSets,
                volumeKg = volumeKg,
                records =
                    workout.exercises.mapNotNull { exercise ->
                        val keys = exercise.sets.flatMap { newRecords[it.id].orEmpty() }
                        headlineRecords(keys).takeIf { it.isNotEmpty() }?.let {
                            exercises[exercise.exerciseId]?.name.orEmpty() to
                                it
                        }
                    },
                routineToUpdate = routine?.name?.takeIf { updated != null && RoutineUpdate.changes(routine, updated) },
            )
        }

        fun trackingTypeOf(exerciseId: String): TrackingType =
            exercises[exerciseId]?.trackingType ?: TrackingType.WeightReps

        /** The columns a set of [trackingType] has in the logger, left to right. */
        fun fieldsOf(trackingType: TrackingType): List<SetField> = SetField.of(trackingType, withRpe = rpe)

        /** What an empty field of [ref] shows greyed out and logs when ticked without typing. */
        fun placeholderFor(ref: SetRef): LoggedSet {
            val exercise = workout.exercises[ref.exerciseIndex]
            val previousSet =
                SetPlaceholders.previousFor(exercise.sets, ref.setIndex, previous[exercise.exerciseId].orEmpty())
            return SetPlaceholders.fill(
                workout.setAt(ref).copy(weight = null, reps = null, duration = null, distanceMeters = null, rpe = null),
                previousSet,
            )
        }

        fun previousFor(ref: SetRef): LoggedSet? {
            val exercise = workout.exercises[ref.exerciseIndex]
            return SetPlaceholders.previousFor(exercise.sets, ref.setIndex, previous[exercise.exerciseId].orEmpty())
        }

        fun equipmentOf(exerciseId: String): Equipment = exercises[exerciseId]?.equipment ?: Equipment.Other

        /**
         * The plates per side for [ref]'s weight (as typed, else its placeholder), when its exercise is
         * loaded on a bar; null otherwise, or before there's a weight.
         */
        fun platesFor(ref: SetRef): PlateLoad? {
            val exerciseId = workout.exercises[ref.exerciseIndex].exerciseId
            if (!trackingTypeOf(exerciseId).usesWeight) return null
            val weight = workout.setAt(ref).weight ?: placeholderFor(ref).weight ?: return null
            return LoadRounding.plates(weight, equipmentOf(exerciseId), gym)
        }

        /** Whether [exerciseId]'s weights go on a bar with plates. */
        fun takesPlates(exerciseId: String): Boolean =
            trackingTypeOf(exerciseId).usesWeight && LoadRounding.barFor(equipmentOf(exerciseId), gym) != null

        /**
         * The warm-up sets "Add warm-up sets" would put before [exerciseIndex]'s sets: a ramp to its first
         * working set's weight (as typed, else its placeholder). Empty when there's nothing to ramp to, the
         * exercise isn't weight × reps, it has warm-ups already, or it's under way.
         */
        fun warmupsFor(exerciseIndex: Int): List<WarmupSet> {
            val exercise = workout.exercises[exerciseIndex]
            val sets = exercise.sets
            val firstWorking = sets.indexOfFirst { it.type != SetType.Warmup }
            val notYet =
                !isFinished &&
                    trackingTypeOf(exercise.exerciseId) == TrackingType.WeightReps &&
                    sets.none { it.type == SetType.Warmup || it.isCompleted }
            if (!notYet || firstWorking < 0) return emptyList()
            val ref = SetRef(exerciseIndex, firstWorking)
            val working = workout.setAt(ref).weight ?: placeholderFor(ref).weight ?: return emptyList()
            return WarmupGenerator.generate(working, equipmentOf(exercise.exerciseId), gym)
        }
    }
}

/** The finish sheet's summary. */
data class FinishSummary(
    val completedSets: Int,
    /** Planned sets that weren't done; finishing drops them. */
    val openSets: Int,
    val volumeKg: Double,
    /** Exercise names with the records they broke today, in workout order. */
    val records: List<Pair<String, List<RecordKey>>>,
    /** The routine's name, when updating it with today's values would change it. */
    val routineToUpdate: String?,
)

/**
 * Where the logger hears back from the exercise picker. A finished workout open for corrections has
 * keys of its own, so a pick never lands in the wrong workout.
 */
internal class PickerKeys(
    finishedWorkoutId: String?,
) {
    private val prefix = finishedWorkoutId?.let { "logger.$it" } ?: "logger"
    val add = "$prefix.add"
    val replace = "$prefix.replace"
}

/**
 * The logger, on the workout in progress or, with [finishedWorkoutId], on a finished workout opened
 * from history to correct it. Both log the same way; only the workout in progress has a clock, rest
 * and a finish.
 */
@Suppress("TooManyFunctions", "LongParameterList") // one function per user intent, drawing on every part of a workout
class LoggerViewModel(
    private val finishedWorkoutId: String?,
    private val activeWorkouts: ActiveWorkoutRepository,
    private val history: WorkoutHistoryRepository,
    private val restTimers: RestTimerRepository,
    settings: SettingsRepository,
    exerciseRepository: ExerciseRepository,
    routines: RoutineRepository,
    private val gymProfiles: GymProfileRepository,
) : ViewModel() {
    private val language = MutableStateFlow<String?>(null)
    private val edit = MutableStateFlow<CellEdit?>(null)
    private val invalid = MutableStateFlow<CellRef?>(null)
    private val writes = Mutex()
    private val doneCorrecting = MutableStateFlow(false)

    /** The finished workout being corrected, or null when logging the one in progress. */
    private val correcting: FinishedWorkoutSession? = finishedWorkoutId?.let(history::edit)
    private val session: WorkoutSession = correcting ?: activeWorkouts
    private val editor: WorkoutEditor get() = session.editor
    private val sets: SetLogger get() = session.sets

    /** Whether this logger corrects a finished workout rather than logging the one in progress. */
    val correctsHistory: Boolean = correcting != null

    /** True once corrections are tidied up and saved; the screen then closes. */
    val corrected: StateFlow<Boolean> = doneCorrecting.asStateFlow()

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

    /** The workout open, with its end if it's a finished one. */
    private val opened: Flow<OpenWorkout?> =
        correcting?.workout?.map { done -> done?.let { OpenWorkout(it.asLogged(), it.endedAt) } }
            ?: activeWorkouts.activeWorkout.map { active -> active?.let { OpenWorkout(it, endedAt = null) } }

    private val sessions =
        combine(
            opened,
            session.previousSets,
            session.exerciseHistory,
        ) { workout, previous, history ->
            Session(workout, previous, history)
        }

    // Updating the routine with today's values is part of finishing; a finished workout is past that.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val routine =
        if (correctsHistory) {
            flowOf(null)
        } else {
            activeWorkouts.activeWorkout
                .map { it?.routineId }
                .distinctUntilChanged()
                .flatMapLatest { id -> id?.let(routines::observeRoutine) ?: flowOf(null) }
        }

    private val restTimer = if (correctsHistory) flowOf(null) else restTimers.timer

    val uiState: StateFlow<LoggerUiState> =
        combine(
            sessions,
            exercises,
            restTimer,
            editing,
            combine(routine, settings.settings, gymProfiles.active, gymProfiles.profiles, ::Context),
        ) { session, byId, timer, (cell, error), context ->
            val workout = session.workout?.workout
            if (workout == null) {
                LoggerUiState.NoWorkout
            } else {
                LoggerUiState.Active(
                    workout = workout,
                    exercises = byId,
                    previous = session.previous,
                    history = session.history,
                    routine = context.routine,
                    restTimer = timer,
                    restDefaults = context.settings.rest,
                    formula = context.settings.oneRepMaxFormula,
                    units = context.settings.units,
                    rpe = context.settings.rpe,
                    gym = context.gym,
                    gyms = context.gyms,
                    // A cell whose set was removed meanwhile is no longer being edited.
                    edit = cell?.takeIf { workout.find(it.cell.setId) != null },
                    invalid = error?.takeIf { workout.find(it.setId) != null },
                    endedAt = session.workout.endedAt,
                )
            }
        }
            // Records are measured against the whole history; keep that off the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoggerUiState.Loading)

    /** A workout in the logger: its exercises and sets, and its end if it's finished. */
    private class OpenWorkout(
        val workout: ActiveWorkout,
        val endedAt: Instant?,
    )

    private class Session(
        val workout: OpenWorkout?,
        val previous: Map<String, List<LoggedSet>>,
        val history: Map<String, List<LoggedSet>>,
    )

    /** What the workout is logged against: its routine, the settings, and the gym. */
    private data class Context(
        val routine: Routine?,
        val settings: Settings,
        val gym: GymProfile,
        val gyms: List<GymProfile>,
    )

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
            PadKey.Backspace -> type(current.cell, current.text?.dropLast(1).orEmpty(), target)
            PadKey.Increase, PadKey.Decrease -> step(current, target, up = key == PadKey.Increase)
            PadKey.Next -> next(current, target)
            PadKey.Hide -> edit.value = null
        }
    }

    /**
     * The set being typed into, with how its exercise is tracked, the units it's typed in, and what its
     * weight is loaded with.
     */
    private class TypingTarget(
        val ref: SetRef,
        val trackingType: TrackingType,
        val fields: List<SetField>,
        val placeholder: LoggedSet,
        val units: Units,
        val equipment: Equipment,
        val gym: GymProfile,
    )

    private fun typingTarget(edit: CellEdit): TypingTarget? {
        val state = active ?: return null
        val ref = state.workout.find(edit.cell.setId) ?: return null
        val exerciseId = state.workout.exercises[ref.exerciseIndex].exerciseId
        val trackingType = state.trackingTypeOf(exerciseId)
        return TypingTarget(
            ref = ref,
            trackingType = trackingType,
            fields = state.fieldsOf(trackingType),
            placeholder = state.placeholderFor(ref),
            units = state.units,
            equipment = state.equipmentOf(exerciseId),
            gym = state.gym,
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
        type(current.cell, text, target)
    }

    private fun step(
        current: CellEdit,
        target: TypingTarget,
        up: Boolean,
    ) {
        edit.value = current.copy(text = null)
        clearInvalid(current.cell)
        write(current.cell.setId) {
            stepped(
                current.cell.field,
                up,
                target.trackingType,
                target.placeholder,
                target.units,
                target.equipment,
                target.gym,
            )
        }
    }

    /** Moves along the set; on its last field, ticks it off. */
    private fun next(
        current: CellEdit,
        target: TypingTarget,
    ) {
        val fields = target.fields
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
            launch { sets.reopenSet(setId) }
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

    /** Puts a ramp of warm-up sets before the exercise's sets (see [LoggerUiState.Active.warmupsFor]). */
    fun addWarmups(workoutExerciseId: String) {
        val state = active ?: return
        val index =
            state.workout.exercises
                .indexOfFirst { it.id == workoutExerciseId }
                .takeIf { it >= 0 } ?: return
        val warmups = state.warmupsFor(index)
        launch { sets.addWarmups(workoutExerciseId, warmups) }
    }

    /** Rounds weights to [gymId]'s equipment on this device from now on. */
    fun useGym(gymId: String) = launch { gymProfiles.use(gymId) }

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

    fun rename(name: String) = launch { session.rename(name) }

    fun adjustRest(bySeconds: Int) = launch { restTimers.adjust(bySeconds.seconds) }

    fun skipRest() = launch { restTimers.stop() }

    fun finish(updateRoutine: Boolean) = launch { activeWorkouts.finish(updateRoutine) }

    fun discard() = launch { activeWorkouts.discard() }

    // Correcting a finished workout

    /** Moves the workout to another day, at the same times. */
    fun moveTo(date: LocalDate) = retime { it.onDate(date, TimeZone.currentSystemDefault()) }

    /** Starts the workout at another time; it lasts as long as before. */
    fun startAt(time: LocalTime) = retime { it.startingAt(time, TimeZone.currentSystemDefault()) }

    /** Ends the workout at another time, which changes how long it lasted. */
    fun endAt(time: LocalTime) = retime { it.endingAt(time, TimeZone.currentSystemDefault()) }

    /** Done correcting: sets left unticked are dropped, then [corrected] lets the screen close. */
    fun finishCorrections() {
        val finished = correcting ?: return
        edit.value = null
        launch {
            finished.tidyUp()
            doneCorrecting.value = true
        }
    }

    /** Removes the finished workout from history; the screen closes once it's gone. */
    fun deleteWorkout() {
        val id = finishedWorkoutId ?: return
        launch { history.delete(id) }
    }

    private fun retime(change: (WorkoutTimes) -> WorkoutTimes) {
        val finished = correcting ?: return
        val state = active ?: return
        val endedAt = state.endedAt ?: return
        val times = change(WorkoutTimes(state.workout.startedAt, endedAt))
        launch { finished.setTimes(times.startedAt, times.endedAt) }
    }

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
        target: TypingTarget,
    ) {
        edit.value = CellEdit(cell, text)
        clearInvalid(cell)
        write(cell.setId) { withTyped(cell.field, text, target.trackingType, target.units) }
    }

    private fun clearInvalid(cell: CellRef) {
        invalid.update { if (it == cell) null else it }
    }

    private fun focusFirstField(
        state: LoggerUiState.Active,
        ref: SetRef,
    ) {
        val exercise = state.workout.exercises[ref.exerciseIndex]
        val field = state.fieldsOf(state.trackingTypeOf(exercise.exerciseId)).firstOrNull() ?: return
        edit.value = CellEdit(CellRef(exercise.sets[ref.setIndex].id, field))
    }

    private fun write(
        setId: String,
        change: LoggedSet.() -> LoggedSet,
    ) = launch { sets.updateSet(setId, change) }

    /**
     * Runs a write after the ones already on their way. Keystrokes arrive faster than writes finish, and
     * a tap on Done or Finish must not overtake the set added just before it.
     */
    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { writes.withLock { block() } }
    }
}

/** A finished workout's exercises and sets, in the shape the logger works on. */
private fun FinishedWorkout.asLogged() =
    ActiveWorkout(id = id, name = name, startedAt = startedAt, routineId = routineId, exercises = exercises)

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
