package app.liora.core.domain

import app.liora.core.model.ExerciseSession
import app.liora.core.model.FinishedWorkout
import app.liora.core.model.TrackingType
import kotlin.time.Instant

/** Each exercise's sessions across [workouts], oldest first, by exercise id; only completed sets. */
fun sessionsByExercise(workouts: List<FinishedWorkout>): Map<String, List<ExerciseSession>> =
    workouts
        .sortedBy { it.startedAt }
        .flatMap { workout ->
            workout.exercises.groupBy { it.exerciseId }.map { (exerciseId, instances) ->
                exerciseId to
                    ExerciseSession(
                        workoutId = workout.id,
                        workoutName = workout.name,
                        startedAt = workout.startedAt,
                        sets = instances.flatMap { it.sets }.filter { it.isCompleted },
                    )
            }
        }.groupBy({ it.first }, { it.second })

/** One exercise on the records board: its best in its main metric, and whether that has stalled. */
data class BoardEntry(
    val exerciseId: String,
    val metric: ProgressMetric,
    /** The session that set the best still standing. */
    val best: ProgressPoint,
    val stall: Stall?,
)

/** The records board: every trained exercise's standing best in its main metric, newest record first. */
object RecordsBoard {
    fun of(
        workouts: List<FinishedWorkout>,
        trackingTypeOf: (exerciseId: String) -> TrackingType?,
        now: Instant,
        formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    ): List<BoardEntry> =
        sessionsByExercise(workouts)
            .mapNotNull { (exerciseId, sessions) ->
                val trackingType = trackingTypeOf(exerciseId) ?: return@mapNotNull null
                val metric = ExerciseProgress.metricsFor(trackingType).first()
                val points = ExerciseProgress.series(trackingType, sessions, metric, formula)
                // The earliest session to reach the best keeps it, as with records.
                val best =
                    points.reduceOrNull { best, point ->
                        if (ExerciseProgress.improves(metric, point.value, best.value)) point else best
                    } ?: return@mapNotNull null
                BoardEntry(exerciseId, metric, best, Stalls.of(trackingType, sessions, now, formula = formula))
            }.sortedByDescending { it.best.at }
}
