package app.liora.core.data.routine

import app.liora.core.database.dao.WorkoutDao
import app.liora.core.domain.PastWorkout
import app.liora.core.domain.RotationTurn
import app.liora.core.domain.Rotations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Instant

/** What's next in each rotation: a folder whose routines take turns in their order. */
interface RotationRepository {
    /** One turn per rotation with routines, the one trained most recently first. */
    val turns: Flow<List<RotationTurn>>
}

internal class OfflineRotationRepository(
    routines: RoutineRepository,
    workoutDao: WorkoutDao,
) : RotationRepository {
    override val turns: Flow<List<RotationTurn>> =
        combine(routines.folders, routines.routines, workoutDao.observeFinished()) { folders, all, finished ->
            // Newest first, as the history lists them.
            val workouts =
                finished.map { PastWorkout(it.routineId, it.name, Instant.fromEpochMilliseconds(it.startedAt)) }
            val byFolder = all.groupBy { it.folderId }
            Rotations.order(
                folders
                    .filter { it.rotation }
                    .mapNotNull { folder -> Rotations.turn(folder.id, byFolder[folder.id].orEmpty(), workouts) },
            )
        }.distinctUntilChanged()
}
