package app.liora.core.data

import app.liora.core.common.HybridLogicalClock
import app.liora.core.data.exercise.LegacyNotes
import app.liora.core.data.seed.ExerciseCatalogSeeder
import app.liora.core.data.seed.ExerciseSeedSource
import app.liora.core.data.sync.SyncStamper
import app.liora.core.data.walkthrough.LocalWalkthroughRepository
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/** The first-launch walkthrough shows once on a new install, never on one that ran an earlier Liora. */
class WalkthroughRepositoryTest {
    private val database = inMemoryLioraDatabase()
    private val transactions = TransactionRunner(database)
    private val walkthrough = LocalWalkthroughRepository(database.localMetaDao(), transactions)
    private val seeder =
        ExerciseCatalogSeeder(
            source = ExerciseSeedSource { SEED },
            exerciseDao = database.exerciseDao(),
            meta = database.localMetaDao(),
            transactions = transactions,
            clock = Clock.System,
        )
    private val startup =
        AppStartup(
            walkthrough = walkthrough,
            seeder = seeder,
            legacyNotes =
                LegacyNotes(
                    database.exerciseDao(),
                    database.routineDao(),
                    transactions,
                    SyncStamper(HybridLogicalClock { "test-device" }, Clock.System),
                ),
        )

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun aNewInstallHasItPendingUntilItsFinished() =
        runTest {
            assertFalse(walkthrough.pending.first())
            assertTrue(walkthrough.settle())
            assertTrue(walkthrough.pending.first())
            // Asking again doesn't decide again.
            assertTrue(walkthrough.settle())

            walkthrough.finish()
            assertFalse(walkthrough.settle())
            assertFalse(walkthrough.pending.first())
        }

    @Test
    fun anInstallThatRanAnEarlierLioraSkipsIt() =
        runTest {
            // An earlier Liora seeded the catalog and never heard of the walkthrough.
            seeder.seedIfNeeded()

            assertFalse(walkthrough.settle())
            assertFalse(walkthrough.pending.first())
        }

    @Test
    fun startupDecidesBeforeItSeeds() =
        runTest {
            startup.run()

            // The catalog is in now, and still the new install has its walkthrough.
            assertTrue(walkthrough.settle())
            assertTrue(walkthrough.pending.first())
        }

    @Test
    fun settingsCanShowItAgain() =
        runTest {
            startup.run()
            walkthrough.finish()

            walkthrough.showAgain()
            assertTrue(walkthrough.pending.first())
            // A restart doesn't take it back.
            startup.run()
            assertTrue(walkthrough.settle())
        }

    private companion object {
        val SEED =
            """
            {
              "version": 1,
              "source": "test",
              "exercises": [
                {"id":"fedb.bench","names":{"en":"Bench Press","de":"Bankdrücken"},"tracking":"weight_reps",
                 "equipment":"barbell","category":"strength","primary":["chest"]}
              ]
            }
            """.trimIndent()
    }
}
