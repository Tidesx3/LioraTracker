package app.liora.core.data

import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.gym.OfflineGymProfileRepository
import app.liora.core.data.progression.OfflineProgressionRepository
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.settings.OfflineSettingsRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.workout.LocalRestTimerRepository
import app.liora.core.data.workout.OfflineActiveWorkoutRepository
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.domain.BigJump
import app.liora.core.domain.ProgressionReason
import app.liora.core.model.ActiveWorkout
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseDraft
import app.liora.core.model.Mass
import app.liora.core.model.ProgressionRule
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Double progression end to end: workouts from a routine, then its next start with the targets filled in. */
class ProgressionRepositoryTest {
    private val clock = TestClock()
    private val database = inMemoryLioraDatabase()
    private val transactions = TransactionRunner(database)
    private val ids = IdGenerator(clock)
    private val stamper = SyncStamper(HybridLogicalClock(clock) { "test-device" }, clock)
    private val exercises = OfflineExerciseRepository(database.exerciseDao(), transactions, ids, stamper)
    private val routines = OfflineRoutineRepository(database.routineDao(), transactions, ids, stamper)
    private val settings = OfflineSettingsRepository(database.preferenceDao(), transactions, stamper)
    private val gyms =
        OfflineGymProfileRepository(
            database.gymProfileDao(),
            database.localMetaDao(),
            settings,
            transactions,
            ids,
            stamper,
        )
    private val progression =
        OfflineProgressionRepository(database.workoutDao(), database.exerciseDao(), routines, gyms, settings, clock)
    private val workouts =
        OfflineActiveWorkoutRepository(
            workoutDao = database.workoutDao(),
            exerciseDao = database.exerciseDao(),
            routines = routines,
            progression = progression,
            restTimer = LocalRestTimerRepository(database.localMetaDao(), clock),
            transactions = transactions,
            ids = ids,
            stamper = stamper,
        )

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun everySetAtTheTopStartsTheNextWorkoutAStepHeavier() =
        runTest {
            val press = machine("Chest Press")
            val push = routine("push", press)
            train(push, kilograms = 60.0, 12, 12, 12)

            val next = workouts.startFromRoutine(push.id)
            val sets = next.workingSets()
            // The standard stack moves in 5 kg; the reps aimed for start again at the bottom.
            assertEquals(List(3) { Mass(65.0) }, sets.map { it.weight })
            assertEquals(List(3) { 8 }, sets.map { it.aimReps })
            assertEquals(List(3) { null }, sets.map { it.reps })
            // The range stays the hint.
            assertEquals(List(3) { RepRange(8, 12) }, sets.map { it.targetReps })
            val reason =
                progression
                    .targets(push, clock.now())
                    .values
                    .single()
                    .target.reason
            assertEquals(ProgressionReason.TopReached(3, 12, Mass(60.0), Mass(65.0)), reason)
        }

    @Test
    fun theSameExerciseInAnotherRoutineDoesntCount() =
        runTest {
            val press = machine("Chest Press")
            val push = routine("push", press)
            val pump = routine("pump", press)
            train(push, kilograms = 60.0, 10, 10, 9)
            // A heavier day in another routine.
            train(pump, kilograms = 70.0, 12, 12, 12)

            val target =
                progression
                    .targets(push, clock.now())
                    .values
                    .single()
                    .target
            assertEquals(ProgressionReason.KeepGoing(Mass(60.0), listOf(10, 10, 9)), target.reason)
            assertEquals(listOf(11, 11, 10), target.sets.filterNotNull().map { it.reps })
        }

    @Test
    fun withoutSessionsFromTheRoutineItsLatestSessionAnywhereCounts() =
        runTest {
            val press = machine("Chest Press")
            val pump = routine("pump", press)
            train(pump, kilograms = 70.0, 12, 12, 12)
            // A new routine: nothing from it yet, so the press's latest session anywhere.
            val push = routine("push", press)

            val target = progression.targets(push, clock.now()).values.single()
            assertEquals(Mass(75.0), target.target.heaviest)
        }

    @Test
    fun aMachineWithItsOwnStepMovesByIt() =
        runTest {
            val press = machine("Chest Press")
            exercises.setLoadStep(press, Mass(2.5))
            val push = routine("push", press)
            train(push, kilograms = 60.0, 12, 12, 12)

            assertEquals(
                Mass(62.5),
                progression
                    .targets(push, clock.now())
                    .values
                    .single()
                    .target.heaviest,
            )
            assertEquals(
                Mass(2.5),
                exercises
                    .observeExercise(press, "en")
                    .first()
                    ?.settings
                    ?.loadStep,
            )
        }

    @Test
    fun aMistypedStepIsFlaggedAsABigJump() =
        runTest {
            val press = machine("Chest Press")
            val push = routine("push", press)
            train(push, kilograms = 60.0, 12, 12, 12)
            assertNull(bigJump(push))

            exercises.setLoadStep(press, Mass(25.0))
            assertEquals(BigJump(target = Mass(85.0), heaviestLately = Mass(60.0)), bigJump(push))
            // A looser limit lets it through.
            settings.update { it.copy(jumpLimitPercent = 50) }
            assertNull(bigJump(push))
        }

    private suspend fun bigJump(routine: Routine) =
        progression
            .targets(routine, clock.now())
            .values
            .single()
            .bigJump

    @Test
    fun exercisesWithoutARuleStartAsTheyAlwaysHave() =
        runTest {
            val press = machine("Chest Press")
            val push = routine("push", press, rule = null)
            train(push, kilograms = 60.0, 12, 12, 12)

            assertEquals(emptyMap(), progression.targets(push, clock.now()))
            val sets = workouts.startFromRoutine(push.id).workingSets()
            assertEquals(List(3) { Mass(40.0) }, sets.map { it.weight })
            assertTrue(sets.all { it.aimReps == null })
        }

    @Test
    fun updatingTheRoutineAtTheEndLeavesProgressionsPlanAlone() =
        runTest {
            val press = machine("Chest Press")
            val push = routine("push", press)
            train(push, kilograms = 60.0, 12, 12, 12, updateRoutine = true)

            val saved = routines.get(push.id)!!.exercises.single()
            assertEquals(push.exercises.single().sets, saved.sets)
            assertEquals(ProgressionRule.DoubleProgression, saved.progression)
        }

    private suspend fun machine(name: String) =
        exercises.createCustom(
            ExerciseDraft(name = name, trackingType = TrackingType.WeightReps, equipment = Equipment.Machine),
        )

    /** A routine of [exerciseId] in 3 × 8–12 at 40 kg, with double progression unless [rule] says otherwise. */
    private suspend fun routine(
        id: String,
        exerciseId: String,
        rule: ProgressionRule? = ProgressionRule.DoubleProgression,
    ): Routine {
        val warmup = RoutineSet("$id-w", type = SetType.Warmup, weight = Mass(20.0), reps = RepRange(10))
        val routine =
            Routine(
                id = id,
                name = id,
                exercises =
                    listOf(
                        RoutineExercise(
                            id = "$id-e",
                            exerciseId = exerciseId,
                            sets =
                                listOf(warmup) +
                                    List(3) { RoutineSet("$id-s$it", weight = Mass(40.0), reps = RepRange(8, 12)) },
                            progression = rule,
                        ),
                    ),
            )
        routines.save(routine)
        return routines.get(id)!!
    }

    /** A workout from [routine], its working sets logged at [kilograms] for [reps], a day after the last. */
    private suspend fun train(
        routine: Routine,
        kilograms: Double,
        vararg reps: Int,
        updateRoutine: Boolean = false,
    ) {
        clock.advance(1.days)
        val workout = workouts.startFromRoutine(routine.id)
        val working = workout.workingSets()
        working.forEachIndexed { index, set ->
            workouts.sets.updateSet(set.id) { copy(weight = Mass(kilograms), reps = reps[index]) }
            clock.advance(1.seconds)
            workouts.sets.completeSet(set.id)
        }
        workouts.finish(updateRoutine = updateRoutine)
    }

    private fun ActiveWorkout.workingSets() = exercises.single().sets.filter { it.type != SetType.Warmup }

    private class TestClock(
        var now: Instant = Instant.fromEpochMilliseconds(1_790_000_000_000),
    ) : Clock {
        override fun now() = now

        fun advance(by: Duration) {
            now += by
        }
    }
}
