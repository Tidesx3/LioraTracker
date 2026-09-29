package app.liora.core.database

import androidx.room.Room
import app.cash.turbine.test
import app.liora.core.database.model.ExerciseEntity
import app.liora.core.database.model.SyncMetadata
import app.liora.core.database.model.WorkoutEntity
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseCategory
import app.liora.core.model.Muscle
import app.liora.core.model.TrackingType
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LioraDatabaseTest {
    private val database = Room.inMemoryDatabaseBuilder<LioraDatabase>().buildDatabase()

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun exerciseRoundTripsThroughConverters() =
        runTest {
            val bench =
                ExerciseEntity(
                    id = "fedb.Barbell_Bench_Press",
                    name = "Barbell Bench Press",
                    trackingType = TrackingType.WeightReps,
                    equipment = Equipment.Barbell,
                    category = ExerciseCategory.Strength,
                    primaryMuscles = setOf(Muscle.Chest),
                    secondaryMuscles = setOf(Muscle.Shoulders, Muscle.Triceps),
                    instructions = listOf("Lie on the bench, grip the bar.", "Lower to the chest, press up."),
                    imagePaths = listOf("Barbell_Bench_Press/0.jpg"),
                    isCustom = false,
                    variationOf = null,
                    notes = null,
                    rank = 1,
                    sync = SyncMetadata(createdAt = 0, hlc = "", dirty = false),
                )
            database.exerciseDao().upsertExercises(listOf(bench))
            assertEquals(bench, database.exerciseDao().get(bench.id))
        }

    @Test
    fun activeWorkoutIsTheUnfinishedOne() =
        runTest {
            val dao = database.workoutDao()
            dao.observeActive().test {
                assertNull(awaitItem())

                val workout = workout(id = "w1", endedAt = null)
                dao.upsert(workout)
                assertEquals(workout, awaitItem())

                dao.upsert(workout.copy(endedAt = 2_000))
                assertNull(awaitItem())
            }
        }

    @Test
    fun tombstonedWorkoutIsNotActive() =
        runTest {
            val dao = database.workoutDao()
            dao.upsert(workout(id = "w1", endedAt = null).let { it.copy(sync = it.sync.copy(deletedAt = 5)) })
            assertNull(dao.getActive())
        }

    private fun workout(
        id: String,
        endedAt: Long?,
    ) = WorkoutEntity(
        id = id,
        name = null,
        startedAt = 1_000,
        endedAt = endedAt,
        routineId = null,
        notes = null,
        bodyweightKg = null,
        sync = SyncMetadata(createdAt = 1_000, hlc = "0000000001000:0000:test"),
    )
}
