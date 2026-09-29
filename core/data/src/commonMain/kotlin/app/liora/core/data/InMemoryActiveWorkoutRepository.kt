package app.liora.core.data

import app.liora.core.common.IdGenerator
import app.liora.core.model.ActiveWorkout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * Temporary implementation until the Room-backed repository lands in Phase 1. State lives in memory,
 * so an in-progress workout does not survive process death yet.
 */
internal class InMemoryActiveWorkoutRepository(
    private val ids: IdGenerator,
    private val clock: Clock,
) : ActiveWorkoutRepository {
    private val state = MutableStateFlow<ActiveWorkout?>(null)

    override val activeWorkout: StateFlow<ActiveWorkout?> = state.asStateFlow()

    override suspend fun startEmptyWorkout(): ActiveWorkout {
        state.value?.let { return it }
        return ActiveWorkout(id = ids.newId(), name = null, startedAt = clock.now())
            .also { state.value = it }
    }

    override suspend fun finish() {
        state.value = null
    }

    override suspend fun discard() {
        state.value = null
    }
}
