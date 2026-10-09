package app.liora.core.data

import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.routine.OfflineRotationRepository
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.workout.LocalRestTimerRepository
import app.liora.core.data.workout.OfflineActiveWorkoutRepository
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.domain.RotationTurn
import app.liora.core.model.Routine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** A folder made a rotation: its routines take turns, and finishing one moves it on. */
class RotationRepositoryTest {
    private var now = Instant.fromEpochMilliseconds(1_790_000_000_000)
    private val clock =
        object : Clock {
            override fun now() = now
        }
    private val database = inMemoryLioraDatabase()
    private val transactions = TransactionRunner(database)
    private val ids = IdGenerator(clock)
    private val stamper = SyncStamper(HybridLogicalClock(clock) { "test-device" }, clock)
    private val routines = OfflineRoutineRepository(database.routineDao(), transactions, ids, stamper)
    private val rotations = OfflineRotationRepository(routines, database.workoutDao())
    private val workouts =
        OfflineActiveWorkoutRepository(
            workoutDao = database.workoutDao(),
            exerciseDao = database.exerciseDao(),
            routines = routines,
            progression = NoProgression,
            restTimer = LocalRestTimerRepository(database.localMetaDao(), clock),
            transactions = transactions,
            ids = ids,
            stamper = stamper,
        )

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun aRotationMovesOnWithEachWorkoutFromIt() =
        runTest {
            val ppl = routines.createFolder("Push Pull Legs")
            listOf(
                "push",
                "pull",
                "legs",
            ).forEach { routines.save(Routine(it, it.replaceFirstChar(Char::uppercase), ppl)) }
            // A plain folder isn't a rotation.
            assertTrue(rotations.turns.first().isEmpty())

            routines.setRotation(ppl, true)
            assertEquals(listOf(RotationTurn(ppl, "push", null)), rotations.turns.first())
            assertTrue(
                routines.folders
                    .first()
                    .single()
                    .rotation,
            )

            val started = now
            workouts.startFromRoutine("push")
            now += 1.hours
            workouts.finish()
            assertEquals(listOf(RotationTurn(ppl, "pull", started)), rotations.turns.first())

            routines.setRotation(ppl, false)
            assertTrue(rotations.turns.first().isEmpty())
        }
}
