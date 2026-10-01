package app.liora.core.model

import kotlin.time.Instant

/**
 * One exercise in one finished workout: the sets done that day, however often the exercise came up in
 * the workout. What its charts, records and history are made of.
 */
data class ExerciseSession(
    val workoutId: String,
    /** The workout's user-given name; null shows the default. */
    val workoutName: String?,
    val startedAt: Instant,
    val sets: List<LoggedSet>,
)
