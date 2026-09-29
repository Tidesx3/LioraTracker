package app.liora.core.data

import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.exercise.ExerciseInUseException
import app.liora.core.data.exercise.OfflineExerciseRepository
import app.liora.core.data.exercise.TrackingTypeLockedException
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.database.model.SyncMetadata
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.database.model.WorkoutExerciseEntity
import app.liora.core.database.model.WorkoutSetEntity
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseDraft
import app.liora.core.model.Muscle
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class CustomExerciseTest {
    private val database = inMemoryLioraDatabase()
    private val repository =
        OfflineExerciseRepository(
            exerciseDao = database.exerciseDao(),
            transactions = TransactionRunner(database),
            ids = IdGenerator(Clock.System),
            stamper = SyncStamper(HybridLogicalClock { "test-device" }, Clock.System),
        )
    private val legPressInternat =
        ExerciseDraft(
            name = "  Beinpresse Internat ",
            trackingType = TrackingType.WeightReps,
            equipment = Equipment.Machine,
            primaryMuscles = setOf(Muscle.Quadriceps),
            secondaryMuscles = setOf(Muscle.Glutes, Muscle.Quadriceps),
            notes = "Sitz auf Stufe 4",
            variationOf = "fedb.Leg_Press",
        )

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun createdExerciseShowsUpInEveryLanguageAndIsMarkedForSync() =
        runTest {
            val id = repository.createCustom(legPressInternat)

            for (language in listOf("de", "en")) {
                val exercise = assertNotNull(repository.observeExercise(id, language).first())
                assertEquals("Beinpresse Internat", exercise.name)
                assertTrue(exercise.isCustom)
                assertEquals("fedb.Leg_Press", exercise.variationOf)
                // A muscle can't be both primary and secondary.
                assertEquals(setOf(Muscle.Glutes), exercise.secondaryMuscles)
            }
            assertTrue(assertNotNull(database.exerciseDao().get(id)).sync.dirty)
        }

    @Test
    fun trackingTypeLocksOnceSetsAreLogged() =
        runTest {
            val id = repository.createCustom(legPressInternat)
            repository.updateCustom(id, legPressInternat.copy(trackingType = TrackingType.WeightedBodyweight))
            repository.updateCustom(id, legPressInternat.copy(name = "Beinpresse Internat unten"))

            logASet(exerciseId = id)

            assertTrue(repository.hasLoggedSets(id))
            assertFailsWith<TrackingTypeLockedException> {
                repository.updateCustom(id, legPressInternat.copy(trackingType = TrackingType.Duration))
            }
            // Everything except the tracking type stays editable.
            repository.updateCustom(id, legPressInternat.copy(name = "Beinpresse (Internat)"))
            assertEquals("Beinpresse (Internat)", repository.observeExercise(id, "de").first()?.name)
        }

    @Test
    fun unusedExercisesCanBeDeletedButUsedOnesOnlyArchived() =
        runTest {
            val unused = repository.createCustom(legPressInternat.copy(name = "Wegwerfübung"))
            assertFalse(repository.isInUse(unused))
            repository.deleteCustom(unused)
            assertNull(repository.observeExercise(unused, "de").first())
            assertNotNull(assertNotNull(database.exerciseDao().get(unused)).sync.deletedAt, "tombstone for sync")

            val used = repository.createCustom(legPressInternat)
            logASet(exerciseId = used)
            assertFailsWith<ExerciseInUseException> { repository.deleteCustom(used) }

            repository.setArchived(used, archived = true)
            assertTrue(assertNotNull(repository.observeExercise(used, "de").first()).settings.archived)
            repository.setArchived(used, archived = false)
            assertFalse(assertNotNull(repository.observeExercise(used, "de").first()).settings.archived)
        }

    private suspend fun logASet(exerciseId: String) {
        val sync = SyncMetadata(createdAt = 1, hlc = "0000000000001:0000:test")
        val workouts = database.workoutDao()
        workouts.upsert(WorkoutEntity("w", null, 1, 2, null, null, null, sync))
        workouts.upsertExercise(WorkoutExerciseEntity("we-$exerciseId", "w", exerciseId, 0, null, null, null, sync))
        workouts.upsertSet(
            WorkoutSetEntity(
                id = "s-$exerciseId",
                workoutExerciseId = "we-$exerciseId",
                position = 0,
                setType = SetType.Normal,
                weightKg = 100.0,
                reps = 10,
                durationSeconds = null,
                distanceMeters = null,
                rpe = null,
                completedAt = 2,
                sync = sync,
            ),
        )
    }
}
