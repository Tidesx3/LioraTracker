package app.liora.core.model

import kotlin.time.Instant

/** The workout currently being logged. At most one exists at a time. */
data class ActiveWorkout(
    val id: String,
    /** User-given name; null until named, the UI then shows a default. */
    val name: String?,
    val startedAt: Instant,
)
