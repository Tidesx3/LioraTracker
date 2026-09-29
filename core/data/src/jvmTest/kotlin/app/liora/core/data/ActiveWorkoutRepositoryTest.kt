package app.liora.core.data

import app.cash.turbine.test
import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.workout.OfflineActiveWorkoutRepository
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.core.model.SetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ActiveWorkoutRepositoryTest {
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(1_790_000_000_000)
        }
    private val database = inMemoryLioraDatabase()
    private val ids = IdGenerator(clock)
    private val stamper = SyncStamper(HybridLogicalClock(clock) { "test-device" }, clock)
    private val routines =
        OfflineRoutineRepository(database.routineDao(), TransactionRunner(database), ids, stamper)
    private val repository =
        OfflineActiveWorkoutRepository(
            workoutDao = database.workoutDao(),
            routines = routines,
            transactions = TransactionRunner(database),
            ids = ids,
            stamper = stamper,
        )

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun startingTwiceKeepsTheSameWorkout() =
        runTest {
            val first = repository.startEmptyWorkout()
            val second = repository.startEmptyWorkout()
            assertEquals(first, second)
            assertEquals(clock.now(), first.startedAt)
        }

    @Test
    fun finishingEndsTheWorkoutAndMarksItForSync() =
        runTest {
            val started = repository.startEmptyWorkout()
            repository.finish()

            val stored = assertNotNull(database.workoutDao().get(started.id))
            assertEquals(clock.now().toEpochMilliseconds(), stored.endedAt)
            assertTrue(stored.sync.dirty)
            assertTrue(stored.sync.hlc.endsWith(":test-device"))
            assertNull(database.workoutDao().getActive())
        }

    @Test
    fun discardingLeavesATombstoneForSync() =
        runTest {
            repository.activeWorkout.test {
                assertNull(awaitItem())
                val started = repository.startEmptyWorkout()
                assertEquals(started, awaitItem())
                repository.discard()
                assertNull(awaitItem())

                val stored = assertNotNull(database.workoutDao().get(started.id))
                assertNotNull(stored.sync.deletedAt)
            }
        }

    @Test
    fun startingFromARoutinePlansItsExercisesAndSets() =
        runTest {
            val routine =
                Routine(
                    id = "push",
                    name = "Push",
                    exercises =
                        listOf(
                            RoutineExercise(
                                id = "re-bench",
                                exerciseId = "bench",
                                supersetGroup = 1,
                                restSeconds = 120,
                                sets =
                                    listOf(
                                        RoutineSet(
                                            "s1",
                                            type = SetType.Warmup,
                                            weight = Mass(40.0),
                                            reps = RepRange(10),
                                        ),
                                        RoutineSet("s2", weight = Mass(80.0), reps = RepRange(8, 12)),
                                    ),
                            ),
                            RoutineExercise(id = "re-fly", exerciseId = "fly", supersetGroup = 1),
                        ),
                )
            routines.save(routine)

            val workout = repository.startFromRoutine("push")

            assertEquals("Push", workout.name)
            assertEquals("push", workout.routineId)
            assertEquals(listOf("bench", "fly"), workout.exercises.map { it.exerciseId })
            assertEquals(listOf(1, 1), workout.exercises.map { it.supersetGroup })
            assertEquals(120, workout.exercises[0].restSeconds)
            val (warmup, working) = workout.exercises[0].sets
            // An exact target is prefilled; a range stays a hint. Nothing is ticked off yet.
            assertEquals(LoggedSet(warmup.id, SetType.Warmup, Mass(40.0), reps = 10, targetReps = RepRange(10)), warmup)
            assertEquals(LoggedSet(working.id, weight = Mass(80.0), reps = null, targetReps = RepRange(8, 12)), working)
            assertEquals(workout, repository.activeWorkout.first())

            // A second start while one is running keeps the running one.
            assertEquals(workout.id, repository.startFromRoutine("push").id)
        }
}
