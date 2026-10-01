package app.liora.core.data

import androidx.room.useReaderConnection
import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.workout.LocalRestTimerRepository
import app.liora.core.data.workout.OfflineActiveWorkoutRepository
import app.liora.core.data.workout.OfflineWorkoutHistoryRepository
import app.liora.core.data.workout.SetCompletion
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.model.ActiveWorkout
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseDraft
import app.liora.core.model.FinishedWorkout
import app.liora.core.model.Mass
import app.liora.core.model.TrackingType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Correcting finished workouts with the logger's tools, without disturbing the workout in progress. */
class WorkoutEditingTest {
    private val clock = TestClock()
    private val database = inMemoryLioraDatabase()
    private val transactions = TransactionRunner(database)
    private val ids = IdGenerator(clock)
    private val stamper = SyncStamper(HybridLogicalClock(clock) { "test-device" }, clock)
    private val exercises = OfflineExerciseRepository(database.exerciseDao(), transactions, ids, stamper)
    private val routines = OfflineRoutineRepository(database.routineDao(), transactions, ids, stamper)
    private val restTimer = LocalRestTimerRepository(database.localMetaDao(), clock)
    private val workouts =
        OfflineActiveWorkoutRepository(
            database.workoutDao(),
            database.exerciseDao(),
            routines,
            restTimer,
            transactions,
            ids,
            stamper,
        )
    private val history =
        OfflineWorkoutHistoryRepository(
            database.workoutDao(),
            database.exerciseDao(),
            routines,
            restTimer,
            transactions,
            ids,
            stamper,
        )

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun correctingAFinishedWorkoutLeavesTheOneInProgressAlone() =
        runTest {
            val bench = exercise("Bench")
            val monday = session(bench, weight = 80.0)
            clock.advance(1.days)
            // Today's workout is resting after its first set.
            workouts.startEmptyWorkout()
            workouts.editor.addExercises(listOf(bench))
            val first =
                current()
                    .exercises
                    .single()
                    .sets
                    .first()
            workouts.sets.updateSet(first.id) { copy(weight = Mass(85.0), reps = 8) }
            workouts.sets.completeSet(first.id)
            val today = current()
            val rest = restTimer.timer.first()

            val editing = history.edit(monday)
            val done = finished(editing.workout.first())
            val exercise = done.exercises.single()
            editing.sets.updateSet(exercise.sets.first().id) { copy(weight = Mass(82.5)) }
            editing.sets.addSet(exercise.id)
            val added =
                finished(editing.workout.first())
                    .exercises
                    .single()
                    .sets
                    .last()
            clock.advance(1.minutes)
            assertEquals(SetCompletion.Logged(rest = null), editing.sets.completeSet(added.id))

            val corrected = history.workouts.first().single { it.id == monday }
            val sets = corrected.exercises.single().sets
            assertEquals(listOf(82.5, 80.0, 80.0, 80.0), sets.map { it.weight?.kilograms })
            // Done during that workout, as far as anyone can tell: by its end.
            assertEquals(done.endedAt, sets.last().completedAt)
            assertEquals(done.endedAt, corrected.endedAt)
            // Today's workout and its rest timer didn't notice.
            assertEquals(today, current())
            assertEquals(rest, restTimer.timer.first())
        }

    @Test
    fun aFinishedWorkoutLooksBackAtTheSessionsBeforeIt() =
        runTest {
            val bench = exercise("Bench")
            session(bench, weight = 60.0)
            clock.advance(1.days)
            val middle = session(bench, weight = 70.0)
            clock.advance(1.days)
            session(bench, weight = 80.0)

            val editing = history.edit(middle)
            assertEquals(List(3) { 60.0 }, editing.previousSets.first()[bench]?.map { it.weight?.kilograms })
            assertEquals(List(3) { 60.0 }, editing.exerciseHistory.first()[bench]?.map { it.weight?.kilograms })
            // An exercise added to it starts like that session, and an empty set ticked off takes its values.
            editing.editor.addExercises(listOf(bench))
            val added = finished(editing.workout.first()).exercises.last()
            assertEquals(3, added.sets.size)
            editing.sets.completeSet(added.sets.first().id)
            val logged =
                finished(editing.workout.first())
                    .exercises
                    .last()
                    .sets
                    .first()
            assertEquals(Mass(60.0), logged.weight)
            assertEquals(8, logged.reps)

            // The workout in progress looks back at the latest.
            clock.advance(1.days)
            workouts.startEmptyWorkout()
            workouts.editor.addExercises(listOf(bench))
            assertEquals(List(3) { 80.0 }, workouts.previousSets.first()[bench]?.map { it.weight?.kilograms })
        }

    @Test
    fun movingAWorkoutMovesItsSets() =
        runTest {
            val id = session(exercise("Bench"), weight = 80.0)
            val editing = history.edit(id)
            val before = finished(editing.workout.first())
            val hlc = hlcOf("workout", id)

            val dayEarlier = before.startedAt - 1.days
            editing.setTimes(dayEarlier, dayEarlier + 45.minutes)
            val moved = finished(editing.workout.first())
            assertEquals(dayEarlier, moved.startedAt)
            assertEquals(45.minutes, moved.duration)
            // The records its sets set keep their day.
            assertEquals(
                before.exercises
                    .single()
                    .sets
                    .map { it.completedAt?.minus(1.days) },
                moved.exercises
                    .single()
                    .sets
                    .map { it.completedAt },
            )
            assertNotEquals(hlc, hlcOf("workout", id))

            // A new end leaves the sets where they are.
            editing.setTimes(dayEarlier, dayEarlier + 1.hours)
            assertEquals(moved.exercises, finished(editing.workout.first()).exercises)
            assertEquals(1.hours, finished(editing.workout.first()).duration)
            // An end before the start ends it as it starts.
            editing.setTimes(dayEarlier, dayEarlier - 1.hours)
            assertEquals(Duration.ZERO, finished(editing.workout.first()).duration)
        }

    @Test
    fun tidyingUpDropsWhatWasntTickedOff() =
        runTest {
            val bench = exercise("Bench")
            val row = exercise("Row")
            val id = session(bench, weight = 80.0)
            val editing = history.edit(id)
            val exercise = finished(editing.workout.first()).exercises.single()
            editing.sets.addSet(exercise.id)
            editing.sets.reopenSet(exercise.sets.first().id)
            editing.editor.addExercises(listOf(row))

            editing.tidyUp()
            val tidy = finished(editing.workout.first())
            assertEquals(listOf(bench), tidy.exercises.map { it.exerciseId })
            assertEquals(
                exercise.sets.drop(1).map { it.id },
                tidy.exercises
                    .single()
                    .sets
                    .map { it.id },
            )
        }

    @Test
    fun onlyFinishedWorkoutsThatStillExistCanBeChanged() =
        runTest {
            val bench = exercise("Bench")
            val id = session(bench, weight = 80.0)
            val editing = history.edit(id)
            editing.rename("  Monday ")
            assertEquals("Monday", editing.workout.first()?.name)
            val exercise = finished(editing.workout.first()).exercises.single()

            history.delete(id)
            assertNull(editing.workout.first())
            editing.sets.addSet(exercise.id)
            editing.rename("Tuesday")
            assertEquals(3, rowCount("SELECT COUNT(*) FROM workout_set WHERE workout_exercise_id = ?", exercise.id))

            // The workout in progress isn't history yet.
            val active = workouts.startEmptyWorkout()
            assertNull(history.edit(active.id).workout.first())
            history.edit(active.id).rename("Not yet")
            assertNull(current().name)
        }

    /** A finished session of [exerciseId]: three sets of [weight] × 8, a minute apart. Returns its id. */
    private suspend fun session(
        exerciseId: String,
        weight: Double,
    ): String {
        val workout = workouts.startEmptyWorkout()
        workouts.editor.addExercises(listOf(exerciseId))
        for (set in current().exercises.single().sets) {
            clock.advance(1.minutes)
            workouts.sets.updateSet(set.id) { copy(weight = Mass(weight), reps = 8) }
            workouts.sets.completeSet(set.id)
        }
        clock.advance(1.minutes)
        workouts.finish()
        return workout.id
    }

    private suspend fun exercise(name: String) =
        exercises.createCustom(ExerciseDraft(name, TrackingType.WeightReps, Equipment.Barbell))

    private suspend fun current(): ActiveWorkout = workouts.activeWorkout.first()!!

    private fun finished(workout: FinishedWorkout?): FinishedWorkout = requireNotNull(workout)

    private suspend fun hlcOf(
        table: String,
        id: String,
    ): String =
        database.useReaderConnection { connection ->
            connection.usePrepared("SELECT hlc FROM $table WHERE id = ?") { row ->
                row.bindText(1, id)
                check(row.step())
                row.getText(0)
            }
        }

    private suspend fun rowCount(
        sql: String,
        id: String,
    ): Int =
        database.useReaderConnection { connection ->
            connection.usePrepared(sql) { row ->
                row.bindText(1, id)
                check(row.step())
                row.getInt(0)
            }
        }

    private class TestClock(
        var now: Instant = Instant.fromEpochMilliseconds(1_790_000_000_000),
    ) : Clock {
        override fun now() = now

        fun advance(by: Duration) {
            now += by
        }
    }
}
