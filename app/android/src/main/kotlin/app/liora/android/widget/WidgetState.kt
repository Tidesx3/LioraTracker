package app.liora.android.widget

import app.liora.core.data.routine.RotationRepository
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.RestTimerRepository
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.domain.TrainingCalendar
import app.liora.core.model.RestTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinDayOfWeek
import kotlinx.datetime.toLocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

/** What the home-screen widget shows. */
sealed interface WidgetState {
    /** A workout running: its name (null for the default), when it started, the sets done, and any rest left. */
    data class Training(
        val name: String?,
        val startedAt: Instant,
        val setsDone: Int,
        val restEndsAt: Instant?,
    ) : WidgetState

    /** The routine up next in the rotation trained last. */
    data class NextUp(
        val folderName: String,
        val routineId: String,
        val routineName: String,
        val streakWeeks: Int,
    ) : WidgetState

    /** No rotation: an empty workout to start. */
    data class QuickStart(
        val streakWeeks: Int,
    ) : WidgetState
}

/** The widget's state from the repositories: the workout running, else the rotations, and the streak. */
class WidgetStates(
    private val workouts: ActiveWorkoutRepository,
    private val restTimer: RestTimerRepository,
    private val rotations: RotationRepository,
    private val routines: RoutineRepository,
    private val history: WorkoutHistoryRepository,
    private val clock: Clock,
) {
    /** The rest running, and null the moment it runs out, which changes nothing stored. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val restRunning: Flow<RestTimer?> =
        restTimer.timer.flatMapLatest { rest ->
            flow {
                if (rest == null || rest.isOver(clock.now())) {
                    emit(null)
                } else {
                    emit(rest)
                    delay(rest.endsAt - clock.now())
                    emit(null)
                }
            }
        }

    /** The state as it changes, a rest running out included. */
    val changes: Flow<WidgetState> =
        combine(
            workouts.activeWorkout,
            restRunning,
            combine(rotations.turns, routines.folders, routines.routines, ::Triple),
            history.trainingStarts,
        ) { active, rest, (turns, folders, all), starts ->
            val now = clock.now()
            if (active != null) {
                return@combine WidgetState.Training(
                    name = active.name,
                    startedAt = active.startedAt,
                    setsDone = active.exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } },
                    restEndsAt = rest?.endsAt,
                )
            }
            val streak = streakWeeks(starts, now)
            val turn = turns.firstOrNull()
            val folder = turn?.let { folders.firstOrNull { it.id == turn.folderId } }
            val routine = turn?.let { all.firstOrNull { it.id == turn.nextRoutineId } }
            if (folder != null && routine != null) {
                WidgetState.NextUp(folder.name, routine.id, routine.name, streak)
            } else {
                WidgetState.QuickStart(streak)
            }
        }.distinctUntilChanged()

    suspend fun current(): WidgetState = changes.first()

    /** Weeks in a row with a workout, as the Progress tab counts them. */
    private fun streakWeeks(
        starts: List<Instant>,
        now: Instant,
    ): Int {
        val zone = TimeZone.currentSystemDefault()
        val days = starts.map { it.toLocalDateTime(zone).date }.toSet()
        return TrainingCalendar.weekStreak(days, now.toLocalDateTime(zone).date, firstDayOfWeek())
    }

    private fun firstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek.toKotlinDayOfWeek()
}
