package app.liora.core.data

import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.exercise.LegacyNotes
import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.database.model.ExerciseEntity
import app.liora.core.database.model.SyncMetadata
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseCategory
import app.liora.core.model.Muscle
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.core.model.TrackingType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

/** Notes that custom exercises and routines kept move onto the exercise's own note, once. */
class LegacyNotesTest {
    private val database = inMemoryLioraDatabase()
    private val stamper = SyncStamper(HybridLogicalClock { "test-device" }, Clock.System)
    private val transactions = TransactionRunner(database)
    private val exercises =
        OfflineExerciseRepository(database.exerciseDao(), transactions, IdGenerator(Clock.System), stamper)
    private val routines =
        OfflineRoutineRepository(database.routineDao(), transactions, IdGenerator(Clock.System), stamper)
    private val legacyNotes = LegacyNotes(database.exerciseDao(), database.routineDao(), transactions, stamper)

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun notesMoveToTheExerciseOnceAndNothingIsLost() =
        runTest {
            val dao = database.exerciseDao()
            dao.upsertExercises(listOf(exercise("custom.press", isCustom = true, notes = " Sitz auf 4 ")))
            dao.upsertExercises(listOf(exercise("fedb.Bench", isCustom = false, notes = null)))
            exercises.setNote("fedb.Bench", "Griff eng")
            routines.save(
                Routine(
                    id = "push",
                    name = "Push",
                    exercises =
                        listOf(
                            planned("push-press", "custom.press", notes = "3x 12-15"),
                            planned("push-bench", "fedb.Bench", notes = "Griff eng"),
                        ),
                ),
            )

            legacyNotes.move()

            assertEquals("Sitz auf 4\n3x 12-15", note("custom.press"))
            // The same note twice stays once.
            assertEquals("Griff eng", note("fedb.Bench"))
            assertNull(assertNotNull(dao.get("custom.press")).notes)
            assertEquals(listOf(null, null), assertNotNull(routines.get("push")).exercises.map { it.notes })

            // Nothing left to move: a second run writes nothing.
            val settings = dao.getSettings("custom.press")
            legacyNotes.move()
            assertEquals(settings, dao.getSettings("custom.press"))
        }

    private suspend fun note(exerciseId: String) =
        exercises
            .observeExercise(exerciseId, "en")
            .first()
            ?.settings
            ?.stickyNote

    private fun planned(
        id: String,
        exerciseId: String,
        notes: String,
    ) = RoutineExercise(id = id, exerciseId = exerciseId, notes = notes, sets = listOf(RoutineSet("$id-1")))

    private fun exercise(
        id: String,
        isCustom: Boolean,
        notes: String?,
    ) = ExerciseEntity(
        id = id,
        name = id,
        trackingType = TrackingType.WeightReps,
        equipment = Equipment.Machine,
        category = ExerciseCategory.Strength,
        primaryMuscles = setOf(Muscle.Chest),
        secondaryMuscles = emptySet(),
        instructions = emptyList(),
        imagePaths = emptyList(),
        isCustom = isCustom,
        variationOf = null,
        notes = notes,
        rank = null,
        sync = SyncMetadata(createdAt = 1, hlc = "0000000000001:0000:test"),
    )
}
