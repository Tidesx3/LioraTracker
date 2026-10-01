package app.liora.core.data

import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.workout.LocalRestTimerRepository
import app.liora.core.data.workout.OfflineActiveWorkoutRepository
import app.liora.core.data.workout.SetCompletion
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.domain.SetField
import app.liora.core.model.ActiveWorkout
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseDraft
import app.liora.core.model.LoggedSet
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class WorkoutLoggingTest {
    private val clock = TestClock()
    private val database = inMemoryLioraDatabase()
    private val transactions = TransactionRunner(database)
    private val ids = IdGenerator(clock)
    private val stamper = SyncStamper(HybridLogicalClock(clock) { "test-device" }, clock)
    private val exercises = OfflineExerciseRepository(database.exerciseDao(), transactions, ids, stamper)
    private val restTimer = LocalRestTimerRepository(database.localMetaDao(), clock)
    private val routines = OfflineRoutineRepository(database.routineDao(), transactions, ids, stamper)
    private val workouts =
        OfflineActiveWorkoutRepository(
            workoutDao = database.workoutDao(),
            exerciseDao = database.exerciseDao(),
            routines = routines,
            restTimer = restTimer,
            transactions = transactions,
            ids = ids,
            stamper = stamper,
        )
    private val editor = workouts.editor
    private val logger = workouts.sets

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun newExercisesStartWithLastSessionsSets() =
        runTest {
            val bench = exercise("Bench", TrackingType.WeightReps)
            val plank = exercise("Plank", TrackingType.Duration)
            workouts.startEmptyWorkout()
            editor.addExercises(listOf(bench, plank))
            // Never done before: three sets for a lift, one for a timed hold.
            assertEquals(listOf(3, 1), current().exercises.map { it.sets.size })

            logEverything(current(), weight = 80.0, reps = 8)
            workouts.finish()

            workouts.startEmptyWorkout()
            editor.addExercises(listOf(bench))
            // Like last time: same number of sets, still to be done.
            val sets = current().exercises.single().sets
            assertEquals(3, sets.size)
            assertTrue(sets.none { it.isCompleted || it.weight != null })
            assertEquals(
                List(3) { Mass(80.0) to 8 },
                workouts.previousSets.first()[bench]?.map { it.weight to it.reps },
            )
        }

    @Test
    fun tickingAnEmptySetRepeatsLastSessionAndStartsRest() =
        runTest {
            val bench = exercise("Bench", TrackingType.WeightReps)
            workouts.startEmptyWorkout()
            editor.addExercises(listOf(bench))

            // First time ever: nothing to fall back on.
            val first =
                current()
                    .exercises
                    .single()
                    .sets
                    .first()
            assertEquals(SetCompletion.Missing(setOf(SetField.Weight, SetField.Reps)), logger.completeSet(first.id))

            logger.updateSet(first.id) { copy(weight = Mass(80.0), reps = 8) }
            assertEquals(SetCompletion.Logged(rest = 2.minutes), logger.completeSet(first.id))
            val timer = restTimer.timer.first()!!
            assertEquals(2.minutes, timer.total)
            workouts.finish()
            // Finishing ends the rest timer too.
            assertNull(restTimer.current())

            workouts.startEmptyWorkout()
            editor.addExercises(listOf(bench))
            clock.advance(1.minutes)
            val next =
                current()
                    .exercises
                    .single()
                    .sets
                    .first()
            assertEquals(SetCompletion.Logged(rest = 2.minutes), logger.completeSet(next.id))
            val logged =
                current()
                    .exercises
                    .single()
                    .sets
                    .first()
            assertEquals(Mass(80.0), logged.weight)
            assertEquals(8, logged.reps)
            assertEquals(clock.now, logged.completedAt)
        }

    @Test
    fun supersetsRestAfterTheRound() =
        runTest {
            val bench = exercise("Bench", TrackingType.BodyweightReps)
            val row = exercise("Row", TrackingType.BodyweightReps)
            workouts.startEmptyWorkout()
            editor.addExercises(listOf(bench, row))
            editor.linkWithNext(current().exercises[0].id)
            current().exercises.flatMap { it.sets }.forEach { logger.updateSet(it.id) { copy(reps = 10) } }

            // Bench, then row without a break, then rest.
            restTimer.start(1.minutes)
            assertEquals(SetCompletion.Logged(rest = null), logger.completeCurrentSet())
            assertNull(restTimer.current(), "a rest left running mid-round would be wrong")
            clock.advance(1.seconds)
            assertEquals(SetCompletion.Logged(rest = 2.minutes), logger.completeCurrentSet())

            val (benchSets, rowSets) = current().exercises.map { it.sets }
            assertTrue(benchSets[0].isCompleted && rowSets[0].isCompleted)
            assertTrue(!benchSets[1].isCompleted)
        }

    @Test
    fun warmupsRestShorterAndAddedSetsRepeatTheLastOne() =
        runTest {
            val squat = exercise("Squat", TrackingType.WeightReps)
            workouts.startEmptyWorkout()
            editor.addExercises(listOf(squat))
            val first =
                current()
                    .exercises
                    .single()
                    .sets
                    .first()
            logger.updateSet(first.id) { copy(type = SetType.Warmup, weight = Mass(60.0), reps = 5) }
            assertEquals(SetCompletion.Logged(rest = 1.minutes), logger.completeSet(first.id))

            current()
                .exercises
                .single()
                .sets
                .drop(1)
                .forEach { logger.removeSet(it.id) }
            assertEquals(
                1,
                current()
                    .exercises
                    .single()
                    .sets.size,
            )

            logger.addSet(current().exercises.single().id)
            val added =
                current()
                    .exercises
                    .single()
                    .sets
                    .last()
            // After a warm-up comes a working set, with the same numbers to start from.
            assertEquals(LoggedSet(added.id, weight = Mass(60.0), reps = 5), added)
            assertEquals(
                2,
                current()
                    .exercises
                    .single()
                    .sets.size,
            )
        }

    @Test
    fun editingExercisesKeepsSupersetsConsistent() =
        runTest {
            val bench = exercise("Bench", TrackingType.WeightReps)
            val fly = exercise("Fly", TrackingType.WeightReps)
            val plank = exercise("Plank", TrackingType.Duration)
            workouts.startEmptyWorkout()
            editor.addExercises(listOf(bench, fly, plank))
            val (benchId, flyId, plankId) = current().exercises.map { it.id }
            editor.linkWithNext(benchId)
            assertEquals(listOf(1, 1, null), current().exercises.map { it.supersetGroup })

            // Dragging the plank between them breaks the pair up.
            editor.reorder(listOf(benchId, plankId, flyId))
            assertEquals(listOf(bench, plank, fly), current().exercises.map { it.exerciseId })
            assertEquals(listOf(null, null, null), current().exercises.map { it.supersetGroup })

            editor.setRest(benchId, 90)
            editor.setNotes(benchId, "  Pause on the chest ")
            val benchNow = current().exercises.first()
            assertEquals(90, benchNow.restSeconds)
            assertEquals("Pause on the chest", benchNow.notes)

            editor.removeExercise(plankId)
            assertEquals(listOf(bench, fly), current().exercises.map { it.exerciseId })
        }

    @Test
    fun replacingAnExerciseKeepsTheSetsItCanShow() =
        runTest {
            val bench = exercise("Bench", TrackingType.WeightReps)
            val pushUp = exercise("Push-up", TrackingType.BodyweightReps)
            workouts.startEmptyWorkout()
            editor.addExercises(listOf(bench))
            val set =
                current()
                    .exercises
                    .single()
                    .sets
                    .first()
            logger.updateSet(set.id) { copy(weight = Mass(80.0), reps = 8) }

            editor.replaceExercise(current().exercises.single().id, pushUp)
            val replaced = current().exercises.single()
            assertEquals(pushUp, replaced.exerciseId)
            assertEquals(LoggedSet(set.id, reps = 8), replaced.sets.first())
        }

    @Test
    fun restCanBeExtendedShortenedAndSkipped() =
        runTest {
            restTimer.start(90.seconds)
            restTimer.adjust(30.seconds)
            assertEquals(2.minutes, restTimer.current()!!.total)
            clock.advance(1.minutes)
            // Taking away more than is left ends it.
            restTimer.adjust((-90).seconds)
            assertNull(restTimer.current())
            restTimer.start(1.minutes)
            restTimer.stop()
            assertNull(restTimer.timer.first())
        }

    @Test
    fun finishingKeepsWhatWasDoneAndCanUpdateTheRoutine() =
        runTest {
            val bench = exercise("Bench", TrackingType.WeightReps)
            val fly = exercise("Fly", TrackingType.WeightReps)
            routines.save(
                Routine(
                    id = "push",
                    name = "Push",
                    exercises =
                        listOf(
                            RoutineExercise(
                                "rb",
                                bench,
                                sets = List(3) { RoutineSet("rb$it", weight = Mass(80.0), reps = RepRange(8, 12)) },
                            ),
                            RoutineExercise("rf", fly, sets = listOf(RoutineSet("rf0", reps = RepRange(12)))),
                        ),
                ),
            )
            val workout = workouts.startFromRoutine("push")
            val (first, second) = workout.exercises.first().sets
            logger.updateSet(first.id) { copy(weight = Mass(85.0), reps = 10) }
            logger.completeSet(first.id)
            clock.advance(1.minutes)
            logger.updateSet(second.id) { copy(weight = Mass(85.0), reps = 9) }
            logger.completeSet(second.id)

            workouts.finish(updateRoutine = true)

            // History keeps what happened: two bench sets; the third set and the flyes were never done.
            val dao = database.workoutDao()
            assertEquals(listOf(bench), dao.exercisesOf(workout.id).map { it.exerciseId })
            assertEquals(listOf(10, 9), dao.setsOf(workout.id).map { it.reps })
            assertNull(restTimer.current())
            // Next time starts from today: 85 kg, still aiming for 8–12.
            val routine = routines.get("push")!!
            assertEquals(listOf(bench), routine.exercises.map { it.exerciseId })
            assertEquals(
                List(2) { Mass(85.0) to RepRange(8, 12) },
                routine.exercises.single().sets.map {
                    it.weight to
                        it.reps
                },
            )
        }

    private suspend fun current(): ActiveWorkout = workouts.activeWorkout.first()!!

    private suspend fun exercise(
        name: String,
        trackingType: TrackingType,
    ) = exercises.createCustom(ExerciseDraft(name = name, trackingType = trackingType, equipment = Equipment.Other))

    private suspend fun logEverything(
        workout: ActiveWorkout,
        weight: Double,
        reps: Int,
    ) {
        for (set in workout.exercises.flatMap { it.sets }) {
            logger.updateSet(set.id) { copy(weight = Mass(weight), reps = reps, duration = 1.minutes) }
            clock.advance(1.seconds)
            logger.completeSet(set.id)
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
