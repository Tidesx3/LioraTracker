package app.liora.core.model

import kotlin.time.Instant

/** The workout currently being logged. At most one exists at a time. */
data class ActiveWorkout(
    val id: String,
    /** User-given name; null until named, the UI then shows a default. */
    val name: String?,
    val startedAt: Instant,
    /** The routine it was started from, if any. */
    val routineId: String? = null,
    val exercises: List<WorkoutExercise> = emptyList(),
)

/** An exercise in a workout with its sets in order; sets not yet ticked off are the plan. */
data class WorkoutExercise(
    val id: String,
    val exerciseId: String,
    /** Adjacent exercises that share a group form a superset. */
    val supersetGroup: Int? = null,
    val restSeconds: Int? = null,
    val notes: String? = null,
    val sets: List<LoggedSet> = emptyList(),
)
