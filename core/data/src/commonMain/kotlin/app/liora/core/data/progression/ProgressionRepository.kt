package app.liora.core.data.progression

import app.liora.core.data.gym.GymProfileRepository
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.toLoggedSet
import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.domain.BigJump
import app.liora.core.domain.LastTime
import app.liora.core.domain.LoadRounding
import app.liora.core.domain.Progression
import app.liora.core.domain.ProgressionTarget
import app.liora.core.domain.Settings
import app.liora.core.model.GymProfile
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.TrackingType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A routine exercise's next targets, and whether they jump too far above what was lifted lately. */
data class ExerciseProgression(
    val target: ProgressionTarget,
    /**
     * Set when the heaviest target is more than Settings' limit, and more than one gym step, above the
     * heaviest working weight of the last six weeks.
     */
    val bigJump: BigJump? = null,
)

/** The next targets of a routine's exercises that have progression, worked out from their history. */
interface ProgressionRepository {
    /**
     * The targets of [routine]'s exercises with a rule, by routine exercise id, for a session starting at
     * [at]: only workouts that started before it count.
     */
    suspend fun targets(
        routine: Routine,
        at: Instant,
    ): Map<String, ExerciseProgression>

    /** [targets] for the routine with [routineId] starting now, again as it or the history changes. */
    fun observeTargets(routineId: String): Flow<Map<String, ExerciseProgression>>
}

internal class OfflineProgressionRepository(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val routines: RoutineRepository,
    private val gyms: GymProfileRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ProgressionRepository {
    override suspend fun targets(
        routine: Routine,
        at: Instant,
    ): Map<String, ExerciseProgression> {
        val progressing = routine.exercises.filter { it.progression != null && it.sets.any { set -> set.reps != null } }
        if (progressing.isEmpty()) return emptyMap()
        val gym = gyms.active.first()
        val preferences = settings.current()
        return progressing
            .mapNotNull { exercise -> targetFor(exercise, routine.id, at, gym, preferences)?.let { exercise.id to it } }
            .toMap()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTargets(routineId: String): Flow<Map<String, ExerciseProgression>> =
        combine(routines.observeRoutine(routineId), workoutDao.observeFinished()) { routine, _ -> routine }
            .mapLatest { routine -> routine?.let { targets(it, clock.now()) }.orEmpty() }

    private suspend fun targetFor(
        exercise: RoutineExercise,
        routineId: String,
        at: Instant,
        gym: GymProfile,
        preferences: Settings,
    ): ExerciseProgression? {
        val entity = exerciseDao.get(exercise.exerciseId) ?: return null
        if (entity.trackingType !in PROGRESSING) return null
        val ownStep = exerciseDao.getSettings(exercise.exerciseId)?.takeIf { it.sync.deletedAt == null }?.loadStepKg
        val step = { from: Mass, up: Boolean ->
            LoadRounding.step(from, up, entity.equipment, gym, ownStep?.let(::Mass))
        }
        // "Last time" from the same routine first, unless Settings say the latest session anywhere.
        val scope = routineId.takeIf { preferences.progression.lastTime == LastTime.SameRoutine }
        val sessions = sessions(exercise.exerciseId, scope, at)
        val target = Progression.next(exercise.sets, sessions, step, preferences.progression)
        val heaviest =
            workoutDao
                .heaviestKg(exercise.exerciseId, (at - RECENT).toEpochMilliseconds(), at.toEpochMilliseconds())
                ?.let(::Mass)
        val aimed = target.heaviest
        val bigJump =
            aimed != null && heaviest != null &&
                Progression.isBigJump(
                    target = aimed,
                    heaviest = heaviest,
                    // The gym's own step, so a mistyped step of the exercise's own still shows.
                    oneStepUp = LoadRounding.step(heaviest, up = true, entity.equipment, gym),
                    limitPercent = preferences.jumpLimitPercent,
                )
        return ExerciseProgression(target, if (bigJump) BigJump(aimed, heaviest) else null)
    }

    /**
     * The exercise's latest two sessions in workouts started from [routineId], newest first; without
     * any yet (a new rule, or history imported from another app), or without a routine, its latest two anywhere.
     */
    private suspend fun sessions(
        exerciseId: String,
        routineId: String?,
        at: Instant,
    ): List<List<LoggedSet>> {
        val before = at.toEpochMilliseconds()
        val rows =
            routineId?.let { workoutDao.recentSessionSets(exerciseId, it, before, SESSIONS) }.orEmpty().ifEmpty {
                workoutDao.recentSessionSets(exerciseId, null, before, SESSIONS)
            }
        return rows.groupBy { it.workoutId }.values.map { session -> session.map { it.set.toLoggedSet() } }
    }

    private companion object {
        /** Two: the last session decides, and the one before tells a bad day from a trend. */
        const val SESSIONS = 2
        val RECENT = 42.days
        val PROGRESSING = setOf(TrackingType.WeightReps, TrackingType.WeightedBodyweight)
    }
}
