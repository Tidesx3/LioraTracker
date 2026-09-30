package app.liora.core.data

import androidx.room.useReaderConnection
import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.workout.LocalRestTimerRepository
import app.liora.core.data.workout.OfflineActiveWorkoutRepository
import app.liora.core.data.workout.OfflineSetLogger
import app.liora.core.data.workout.OfflineWorkoutHistoryRepository
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseDraft
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** History: finished workouts listed, deleted, saved as a routine and started again. */
class WorkoutHistoryTest {
    private val clock = TestClock()
    private val database = inMemoryLioraDatabase()
    private val transactions = TransactionRunner(database)
    private val ids = IdGenerator(clock)
    private val stamper = SyncStamper(HybridLogicalClock(clock) { "test-device" }, clock)
    private val exercises = OfflineExerciseRepository(database.exerciseDao(), transactions, ids, stamper)
    private val routines = OfflineRoutineRepository(database.routineDao(), transactions, ids, stamper)
    private val restTimer = LocalRestTimerRepository(database.localMetaDao(), clock)
    private val workouts =
        OfflineActiveWorkoutRepository(database.workoutDao(), routines, restTimer, transactions, ids, stamper)
    private val logger =
        OfflineSetLogger(database.workoutDao(), database.exerciseDao(), restTimer, transactions, ids, stamper)
    private val history = OfflineWorkoutHistoryRepository(database.workoutDao(), routines, transactions, ids, stamper)

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun historyListsFinishedWorkoutsNewestFirst() =
        runTest {
            val push = pushDay()
            clock.advance(1.days)
            workouts.startEmptyWorkout()
            workouts.rename("Quick one")
            workouts.finish()
            clock.advance(1.days)
            // Still going: not history yet.
            workouts.startEmptyWorkout()

            val listed = history.workouts.first()
            assertEquals(listOf("Quick one", "Push"), listed.map { it.name })
            val pushed = listed.last()
            assertEquals(push, pushed.id)
            assertEquals(30.minutes, pushed.duration)
            val bench = pushed.exercises.single()
            assertEquals(listOf(SetType.Warmup, SetType.Normal, SetType.Normal), bench.sets.map { it.type })
            assertEquals(listOf(40.0, 80.0, 80.0), bench.sets.map { it.weight?.kilograms })
            assertEquals(RepRange(8, 12), bench.sets.last().targetReps)
        }

    @Test
    fun deletingTombstonesTheWorkoutWithItsExercisesAndSets() =
        runTest {
            val push = pushDay()
            val rows = history.workouts.first().single()
            history.delete(push)

            assertEquals(emptyList(), history.workouts.first())
            assertNotNull(deletedAt("workout", push))
            rows.exercises.forEach { exercise ->
                assertNotNull(deletedAt("workout_exercise", exercise.id))
                exercise.sets.forEach { assertNotNull(deletedAt("workout_set", it.id)) }
            }
        }

    @Test
    fun aWorkoutSavedAsARoutineKeepsWhatWasDone() =
        runTest {
            val push = pushDay()
            val routineId = assertNotNull(history.saveAsRoutine(push, "  Push B "))
            val routine = assertNotNull(routines.get(routineId))

            assertEquals("Push B", routine.name)
            assertEquals(90, routine.exercises.single().restSeconds)
            assertEquals(
                listOf(RepRange(10), RepRange(8, 12), RepRange(8, 12)),
                routine.exercises
                    .single()
                    .sets
                    .map { it.reps },
            )
            // A gone workout makes no routine.
            history.delete(push)
            assertNull(history.saveAsRoutine(push, "Push C"))
        }

    @Test
    fun repeatingStartsAWorkoutWithThatDaysValues() =
        runTest {
            val push = pushDay()
            clock.advance(1.days)
            val repeated = workouts.repeat(push)

            assertEquals("Push", repeated.name)
            // Its own workout, not tied to the routine the original came from.
            assertNull(repeated.routineId)
            val sets = repeated.exercises.single().sets
            assertEquals(List(3) { null }, sets.map { it.completedAt })
            assertEquals(listOf(40.0, 80.0, 80.0), sets.map { it.weight?.kilograms })
            // An exact target is prefilled; a range stays a hint.
            assertEquals(listOf(10, null, null), sets.map { it.reps })
            assertEquals(RepRange(8, 12), sets.last().targetReps)

            // One workout at a time: repeating again returns the one in progress.
            assertEquals(repeated.id, workouts.repeat(push).id)
        }

    /** A finished Push session from its routine: a warm-up and two working sets, half an hour long. */
    private suspend fun pushDay(): String {
        val bench = exercises.createCustom(ExerciseDraft("Bench", TrackingType.WeightReps, Equipment.Barbell))
        routines.save(
            Routine(
                id = "push",
                name = "Push",
                exercises =
                    listOf(
                        RoutineExercise(
                            id = "push-bench",
                            exerciseId = bench,
                            restSeconds = 90,
                            sets =
                                listOf(
                                    RoutineSet("w", SetType.Warmup, Mass(40.0), RepRange(10)),
                                    RoutineSet("s1", weight = Mass(80.0), reps = RepRange(8, 12)),
                                    RoutineSet("s2", weight = Mass(80.0), reps = RepRange(8, 12)),
                                ),
                        ),
                    ),
            ),
        )
        val workout = workouts.startFromRoutine("push")
        for (set in workout.exercises.single().sets) {
            clock.advance(10.minutes)
            logger.updateSet(set.id) { copy(reps = reps ?: 9) }
            logger.completeSet(set.id)
        }
        workouts.finish()
        return workout.id
    }

    /** A row's tombstone time; fails if the row was hard-deleted. */
    private suspend fun deletedAt(
        table: String,
        id: String,
    ): Long? =
        database.useReaderConnection { connection ->
            connection.usePrepared("SELECT deleted_at FROM $table WHERE id = ?") { row ->
                row.bindText(1, id)
                check(row.step()) { "$id was hard-deleted" }
                if (row.isNull(0)) null else row.getLong(0)
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
