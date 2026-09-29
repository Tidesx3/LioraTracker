package app.liora.core.data

import app.liora.core.model.ActiveWorkout
import kotlinx.coroutines.flow.StateFlow

/** Owns the single in-progress workout. UI reads [activeWorkout]; every change is written through immediately. */
interface ActiveWorkoutRepository {
    val activeWorkout: StateFlow<ActiveWorkout?>

    /** Starts an empty workout, or returns the one already in progress. */
    suspend fun startEmptyWorkout(): ActiveWorkout

    suspend fun finish()

    suspend fun discard()
}
