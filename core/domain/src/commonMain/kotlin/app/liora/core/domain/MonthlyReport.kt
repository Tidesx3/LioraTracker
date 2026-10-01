package app.liora.core.domain

import app.liora.core.model.FinishedWorkout
import app.liora.core.model.Muscle
import app.liora.core.model.TrackingType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.yearMonth
import kotlin.time.Duration

/** A month's training in numbers. */
data class MonthTotals(
    val workouts: Int,
    val trainingDays: Int,
    val duration: Duration,
    val volumeKg: Double,
    /** Working sets. */
    val sets: Int,
)

/** An exercise's share of a month: its working sets and the load they moved. */
data class ExerciseShare(
    val exerciseId: String,
    val sets: Int,
    val volumeKg: Double,
)

/** A month looked back on, next to the month before. */
data class MonthlyReport(
    val month: YearMonth,
    val totals: MonthTotals,
    val previous: MonthTotals,
    /** The days with a workout. */
    val trainingDays: Set<LocalDate>,
    /** The records set during the month by exercise, in the order they were first set. */
    val records: Map<String, Set<RecordKey>>,
    /** The exercises with the most working sets, most first. */
    val topExercises: List<ExerciseShare>,
    /** Hard sets per muscle over the month, see [setsPerMuscle]. */
    val setsPerMuscle: Map<Muscle, Double>,
)

/** Monthly reports: what each month added up to, its records, favourite exercises and muscles. */
object MonthlyReports {
    /**
     * The report for [month] in [zone], where workouts belong to the day they started on. Records are
     * measured against everything before them, as history shows them.
     */
    fun of(
        workouts: List<FinishedWorkout>,
        month: YearMonth,
        zone: TimeZone,
        trackingTypeOf: (exerciseId: String) -> TrackingType,
        targetsOf: (exerciseId: String) -> MuscleTargets?,
        topCount: Int = TOP_EXERCISES,
        formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    ): MonthlyReport {
        val monthOf = { workout: FinishedWorkout -> TrainingCalendar.dayOf(workout.startedAt, zone).yearMonth }
        val inMonth = workouts.filter { monthOf(it) == month }.sortedBy { it.startedAt }
        val recordsBySet = HistoryRecords.of(workouts, formula, trackingTypeOf)
        val records = linkedMapOf<String, MutableSet<RecordKey>>()
        for (workout in inMonth) {
            for (exercise in workout.exercises) {
                val keys = exercise.sets.flatMap { recordsBySet[it.id].orEmpty() }
                if (keys.isNotEmpty()) records.getOrPut(exercise.exerciseId) { linkedSetOf() } += keys
            }
        }
        val shares =
            inMonth
                .flatMap { it.exercises }
                .groupBy { it.exerciseId }
                .map { (exerciseId, instances) ->
                    val sets = instances.flatMap { it.sets }
                    ExerciseShare(
                        exerciseId,
                        sets.count { it.isWorkingSet },
                        volumeOf(trackingTypeOf(exerciseId), sets),
                    )
                }.filter { it.sets > 0 }
                .sortedWith(compareByDescending<ExerciseShare> { it.sets }.thenByDescending { it.volumeKg })
        return MonthlyReport(
            month = month,
            totals = totals(inMonth, zone, trackingTypeOf),
            previous = totals(workouts.filter { monthOf(it) == month.minusMonth() }, zone, trackingTypeOf),
            trainingDays = inMonth.map { TrainingCalendar.dayOf(it.startedAt, zone) }.toSet(),
            records = records,
            topExercises = shares.take(topCount),
            setsPerMuscle = setsPerMuscle(inMonth, targetsOf),
        )
    }

    private fun totals(
        workouts: List<FinishedWorkout>,
        zone: TimeZone,
        trackingTypeOf: (exerciseId: String) -> TrackingType,
    ) = MonthTotals(
        workouts = workouts.size,
        trainingDays = workouts.map { TrainingCalendar.dayOf(it.startedAt, zone) }.toSet().size,
        duration = workouts.fold(Duration.ZERO) { total, workout -> total + workout.duration },
        volumeKg =
            workouts.sumOf { workout ->
                workout.exercises.sumOf { volumeOf(trackingTypeOf(it.exerciseId), it.sets) }
            },
        sets = workouts.sumOf { workout -> workout.exercises.sumOf { it.sets.count { set -> set.isWorkingSet } } },
    )

    private const val TOP_EXERCISES = 5
}
