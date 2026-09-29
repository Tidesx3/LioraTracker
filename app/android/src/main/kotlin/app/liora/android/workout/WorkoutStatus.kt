package app.liora.android.workout

import app.liora.core.domain.SetPlaceholders
import app.liora.core.domain.WorkoutOrder
import app.liora.core.domain.setAt
import app.liora.core.model.ActiveWorkout
import app.liora.core.model.Exercise
import app.liora.core.model.LoggedSet
import app.liora.core.model.RestTimer
import app.liora.core.model.TrackingType
import kotlin.time.Instant

/** What the workout notification shows. */
internal data class WorkoutStatus(
    val name: String?,
    val startedAt: Instant,
    /** The set that's up next, or null once every set is done. */
    val next: NextSet?,
    /** The rest being counted down, or null. */
    val rest: RestTimer?,
)

internal data class NextSet(
    val exerciseName: String,
    val setNumber: Int,
    val setCount: Int,
    /** Its values, with empty fields taken from last session or the routine's target. */
    val values: LoggedSet,
    val trackingType: TrackingType,
    /** Whether it can be ticked off from the notification, i.e. without typing anything. */
    val canLog: Boolean,
)

/** The notification's view of the workout in progress; null when there is none. */
internal fun workoutStatus(
    workout: ActiveWorkout?,
    previous: Map<String, List<LoggedSet>>,
    exercises: Map<String, Exercise>,
    rest: RestTimer?,
    now: Instant,
): WorkoutStatus? {
    workout ?: return null
    val next =
        WorkoutOrder.current(workout)?.let { ref ->
            val exercise = workout.exercises[ref.exerciseIndex]
            val trackingType = exercises[exercise.exerciseId]?.trackingType ?: TrackingType.WeightReps
            val lastTime =
                SetPlaceholders.previousFor(
                    exercise.sets,
                    ref.setIndex,
                    previous[exercise.exerciseId].orEmpty(),
                )
            val values = SetPlaceholders.fill(workout.setAt(ref), lastTime)
            NextSet(
                exerciseName = exercises[exercise.exerciseId]?.name.orEmpty(),
                setNumber = ref.setIndex + 1,
                setCount = exercise.sets.size,
                values = values,
                trackingType = trackingType,
                canLog = SetPlaceholders.missingFields(trackingType, values).isEmpty(),
            )
        }
    return WorkoutStatus(workout.name, workout.startedAt, next, rest?.takeUnless { it.isOver(now) })
}
