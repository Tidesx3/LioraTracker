package app.liora.core.model

import kotlin.time.Duration
import kotlin.time.Instant

/** A workout that was finished: what history lists, shows and can repeat. */
data class FinishedWorkout(
    val id: String,
    /** User-given name; null shows the default. */
    val name: String?,
    val startedAt: Instant,
    val endedAt: Instant,
    /** The routine it was started from, if any. */
    val routineId: String? = null,
    val notes: String? = null,
    val exercises: List<WorkoutExercise> = emptyList(),
) {
    val duration: Duration get() = endedAt - startedAt
}
