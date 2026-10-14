package app.liora.core.data

import app.liora.core.data.progression.ExerciseProgression
import app.liora.core.data.progression.ProgressionRepository
import app.liora.core.data.progression.StepsEarned
import app.liora.core.model.Routine
import app.liora.core.model.WorkoutExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

/** For tests that don't use progression: no exercise has targets. */
object NoProgression : ProgressionRepository {
    override suspend fun targets(
        routine: Routine,
        at: Instant,
    ): Map<String, ExerciseProgression> = emptyMap()

    override fun observeTargets(routineId: String): Flow<Map<String, ExerciseProgression>> = flowOf(emptyMap())

    override suspend fun earned(
        routine: Routine,
        exercises: List<WorkoutExercise>,
    ): StepsEarned = StepsEarned()
}
