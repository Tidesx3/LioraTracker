package app.liora.core.data

import androidx.room.useReaderConnection
import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.routine.OfflineRoutineRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class RoutineRepositoryTest {
    private val database = inMemoryLioraDatabase()
    private val dao = database.routineDao()
    private val repository =
        OfflineRoutineRepository(
            routineDao = dao,
            transactions = TransactionRunner(database),
            ids = IdGenerator(Clock.System),
            stamper = SyncStamper(HybridLogicalClock { "test-device" }, Clock.System),
        )

    private val push =
        Routine(
            id = "push",
            name = "  Push A ",
            notes = "Heavy day",
            exercises =
                listOf(
                    RoutineExercise(
                        id = "bench",
                        exerciseId = "fedb.Barbell_Bench_Press_-_Medium_Grip",
                        restSeconds = 150,
                        sets =
                            listOf(
                                RoutineSet("bench-1", type = SetType.Warmup, weight = Mass(40.0), reps = RepRange(10)),
                                RoutineSet("bench-2", weight = Mass(80.0), reps = RepRange(6, 8)),
                            ),
                    ),
                    RoutineExercise(
                        id = "dips",
                        exerciseId = "fedb.Dips_-_Triceps_Version",
                        supersetGroup = 1,
                        sets = listOf(RoutineSet("dips-1", reps = RepRange(12))),
                    ),
                    RoutineExercise(
                        id = "plank",
                        exerciseId = "fedb.Plank",
                        supersetGroup = 1,
                        sets = listOf(RoutineSet("plank-1", duration = 1.minutes)),
                    ),
                ),
        )

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun savedRoutinesComeBackAsSaved() =
        runTest {
            repository.save(push)

            assertEquals(listOf(push.copy(name = "Push A")), repository.routines.first())
        }

    @Test
    fun editingOnlyRestampsWhatChanged() =
        runTest {
            repository.save(push)
            val before = dao.exercisesOf("push").associateBy { it.id }
            val setsBefore = dao.setsOf("push").associateBy { it.id }

            // Drop the plank, make the top set heavier, leave the rest alone.
            val bench = push.exercises[0]
            val edited =
                push.copy(
                    exercises =
                        listOf(
                            bench.copy(sets = listOf(bench.sets[0], bench.sets[1].copy(weight = Mass(82.5)))),
                            push.exercises[1].copy(supersetGroup = null),
                        ),
                )
            repository.save(edited)

            val after = dao.exercisesOf("push").associateBy { it.id }
            val setsAfter = dao.setsOf("push").associateBy { it.id }
            assertEquals(before.getValue("bench").sync, after.getValue("bench").sync)
            assertEquals(setsBefore.getValue("bench-1").sync, setsAfter.getValue("bench-1").sync)
            assertNotEquals(setsBefore.getValue("bench-2").sync.hlc, setsAfter.getValue("bench-2").sync.hlc)
            assertNotEquals(before.getValue("dips").sync.hlc, after.getValue("dips").sync.hlc)
            // The removed exercise and its set stay behind as tombstones, so the removal can sync.
            assertEquals(setOf("bench", "dips"), after.keys)
            assertEquals(setOf("bench-1", "bench-2", "dips-1"), setsAfter.keys)
            assertNotNull(deletedAt("routine_exercise", "plank"))
            assertNotNull(deletedAt("routine_set", "plank-1"))
            assertEquals(edited.copy(name = "Push A"), repository.get("push"))
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

    @Test
    fun deletingAFolderKeepsItsRoutines() =
        runTest {
            val folder = repository.createFolder("PPL")
            repository.save(push.copy(folderId = folder))
            assertEquals(listOf("PPL"), repository.folders.first().map { it.name })

            repository.deleteFolder(folder)

            assertTrue(repository.folders.first().isEmpty())
            assertNull(assertNotNull(repository.get("push")).folderId)
        }

    @Test
    fun duplicatesAreIndependentCopies() =
        runTest {
            repository.save(push)
            val copyId = repository.duplicate("push", "Push B")

            val copy = assertNotNull(repository.get(copyId))
            assertEquals("Push B", copy.name)
            assertEquals(push.exercises.map { it.exerciseId }, copy.exercises.map { it.exerciseId })
            assertTrue(copy.exercises.none { it.id in push.exercises.map(RoutineExercise::id) })
            assertEquals(listOf("push", copyId), repository.routines.first().map { it.id })

            repository.delete("push")
            assertEquals(listOf(copyId), repository.routines.first().map { it.id })
            assertTrue(dao.exercisesOf("push").isEmpty())
        }

    @Test
    fun movingIntoAFolderPutsTheRoutineLast() =
        runTest {
            val folder = repository.createFolder("Upper")
            repository.save(push)
            repository.save(Routine(id = "pull", name = "Pull"))

            repository.moveToFolder("push", folder)

            assertEquals(listOf("pull", "push"), repository.routines.first().map { it.id })
            assertEquals(folder, repository.get("push")?.folderId)
        }
}
