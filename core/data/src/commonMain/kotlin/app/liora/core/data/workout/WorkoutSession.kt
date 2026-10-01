package app.liora.core.data.workout

import app.liora.core.database.dao.ExerciseSetRow
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.model.LoggedSet
import app.liora.core.model.WorkoutExercise
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * A workout open in the logger, with the tools that change it: the one in progress
 * ([ActiveWorkoutRepository]) or a finished one being corrected ([WorkoutHistoryRepository.edit]).
 * Both are logged the same way, and both look back at the sessions before them (see [EarlierSessions]).
 */
interface WorkoutSession {
    /** Last session's completed sets for each exercise of the workout, by exercise id. */
    val previousSets: Flow<Map<String, List<LoggedSet>>>

    /** Every completed set of the workout's exercises from earlier sessions, by exercise id. */
    val exerciseHistory: Flow<Map<String, List<LoggedSet>>>

    val editor: WorkoutEditor

    val sets: SetLogger

    /** Names the workout; blank goes back to the default name. */
    suspend fun rename(name: String)
}

/** Which workout a [WorkoutEditor] or [SetLogger] changes. */
internal sealed interface WorkoutTarget {
    suspend fun rows(workoutDao: WorkoutDao): WorkoutRows?

    /** The workout in progress. */
    data object Active : WorkoutTarget {
        override suspend fun rows(workoutDao: WorkoutDao): WorkoutRows? = workoutDao.activeRows()
    }

    /** A finished workout being corrected. */
    data class Finished(
        val workoutId: String,
    ) : WorkoutTarget {
        override suspend fun rows(workoutDao: WorkoutDao): WorkoutRows? = workoutDao.finishedRows(workoutId)
    }
}

/**
 * What the sessions before a workout depend on: which exercises it has, and where "before" ends
 * ([before], an exclusive start time).
 */
internal data class EarlierSessions(
    val exerciseIds: List<String>,
    val before: Long,
) {
    companion object {
        /** Before the workout in progress comes every finished workout, whatever the clock says. */
        fun ofActive(exercises: List<WorkoutExercise>) = EarlierSessions(idsOf(exercises), Long.MAX_VALUE)

        /** Before a finished workout come the ones that started earlier. */
        fun ofFinished(
            startedAt: Instant,
            exercises: List<WorkoutExercise>,
        ) = EarlierSessions(idsOf(exercises), startedAt.toEpochMilliseconds())

        private fun idsOf(exercises: List<WorkoutExercise>) = exercises.map { it.exerciseId }.distinct().sorted()
    }
}

/** Where the sessions before these rows' workout end; see [EarlierSessions]. */
internal val WorkoutRows.earlierThan: Long
    get() = if (workout.endedAt == null) Long.MAX_VALUE else workout.startedAt

/** Sets from [query] by exercise id, re-queried as the workout's exercises come and go or it moves in time. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<EarlierSessions?>.sets(
    query: (exerciseIds: List<String>, before: Long) -> Flow<List<ExerciseSetRow>>,
): Flow<Map<String, List<LoggedSet>>> =
    distinctUntilChanged()
        .flatMapLatest { earlier ->
            if (earlier == null || earlier.exerciseIds.isEmpty()) {
                flowOf(emptyMap())
            } else {
                query(earlier.exerciseIds, earlier.before).map { rows ->
                    rows.groupBy({ it.exerciseId }, { it.set.toLoggedSet() })
                }
            }
        }.distinctUntilChanged()
