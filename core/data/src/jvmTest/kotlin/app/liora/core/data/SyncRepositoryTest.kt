package app.liora.core.data

import app.liora.core.data.sync.OfflineSyncRepository
import app.liora.core.data.sync.PulledBatches
import app.liora.core.data.sync.SyncBackend
import app.liora.core.data.sync.SyncOutcome
import app.liora.core.data.sync.SyncProblem
import app.liora.core.data.sync.SyncScheduler
import app.liora.core.data.sync.SyncSignInNeededException
import app.liora.core.data.sync.SyncStores
import app.liora.core.data.sync.SyncUnreachableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Turning sync on and off, and what the background work makes of each sync. */
class SyncRepositoryTest {
    private val phone = TestPhone("phone")
    private val drive = InMemoryStore()
    private val stores = FakeStores()
    private val scheduler = FakeScheduler()
    private val repository =
        OfflineSyncRepository(phone.sync, stores, scheduler, phone.database.localMetaDao(), phone.database.syncDao())

    @AfterTest
    fun tearDown() = phone.database.close()

    @Test
    fun connectingSendsEverythingAndKeepsSyncing() =
        runTest {
            phone.trainForAWhile()
            // Synced somewhere else before: a new account still gets everything.
            phone.sync.sync(InMemoryStore())

            repository.connect(ACCOUNT)
            assertTrue(scheduler.started)
            assertEquals(1, scheduler.syncsAsked)
            assertEquals(ACCOUNT, repository.state.first().account)

            assertEquals(SyncOutcome.Done, repository.sync())
            val tablet = TestPhone("tablet")
            tablet.sync.sync(drive)
            assertEquals(phone.history.workouts.first(), tablet.history.workouts.first())
            assertTrue(repository.state.first().lastSynced != null)
            // How much of the Drive it takes, as the store says after the sync.
            assertEquals(drive.usedBytes(), repository.state.first().usedBytes)
            tablet.database.close()
        }

    @Test
    fun signingInAgainCarriesOnWhereItStopped() =
        runTest {
            phone.trainForAWhile()
            repository.connect(ACCOUNT)
            repository.sync()
            stores.failure = SyncSignInNeededException()
            assertEquals(SyncOutcome.Stopped, repository.sync())
            assertEquals(SyncProblem.SignInNeeded, repository.state.first().problem)

            stores.failure = null
            repository.connect(ACCOUNT)
            assertNull(repository.state.first().problem)
            repository.sync()
            // Nothing changed since the first sync, so there's nothing to send again.
            assertEquals(1, drive.batches.size)
        }

    @Test
    fun whatHasntGoneUpYetIsCounted() =
        runTest {
            repository.connect(ACCOUNT)
            phone.trainForAWhile()
            val waiting = repository.state.first().pending
            assertTrue(waiting > 0)

            repository.sync()
            // Everything went, but the workout still going: it waits to be finished.
            assertEquals(0, repository.state.first().pending)
            phone.workouts.finish()
            assertTrue(repository.state.first().pending > 0)
        }

    @Test
    fun onlyAProblemTheUserCanSolveNeedsThem() =
        runTest {
            repository.connect(ACCOUNT)
            stores.failure = SyncUnreachableException("offline")
            repository.sync()
            assertFalse(repository.state.first().needsYou)

            stores.failure = SyncSignInNeededException()
            repository.sync()
            assertTrue(repository.state.first().needsYou)
        }

    @Test
    fun anUnreachableStoreIsTriedAgainLater() =
        runTest {
            repository.connect(ACCOUNT)
            stores.failure = SyncUnreachableException("offline")
            assertEquals(SyncOutcome.TryLater, repository.sync())
            assertEquals(SyncProblem.Unreachable, repository.state.first().problem)

            stores.failure = null
            assertEquals(SyncOutcome.Done, repository.sync())
            assertNull(repository.state.first().problem)
        }

    @Test
    fun disconnectingStopsSyncingAndGivesAccessBack() =
        runTest {
            repository.connect(ACCOUNT)
            repository.disconnect()

            assertFalse(scheduler.started)
            assertEquals(listOf(ACCOUNT), stores.forgotten)
            assertNull(repository.state.first().account)
            assertNull(repository.state.first().usedBytes)
            stores.failure = SyncUnreachableException("not used")
            assertEquals(SyncOutcome.Done, repository.sync())
            repository.syncNow()
            assertEquals(1, scheduler.syncsAsked)
        }

    private inner class FakeStores : SyncStores {
        var failure: Exception? = null
        val forgotten = mutableListOf<String>()

        override fun open(account: String): SyncBackend =
            failure?.let { error ->
                object : SyncBackend by drive {
                    override suspend fun pull(
                        deviceId: String,
                        cursor: String?,
                    ): PulledBatches = throw error
                }
            } ?: drive

        override suspend fun forget(account: String) {
            forgotten += account
        }
    }

    private class FakeScheduler : SyncScheduler {
        override val running = MutableStateFlow(false)
        var started = false
        var syncsAsked = 0

        override fun start() {
            started = true
        }

        override fun syncNow() {
            syncsAsked++
        }

        override fun stop() {
            started = false
        }
    }

    private companion object {
        const val ACCOUNT = "lifter@example.com"
    }
}
