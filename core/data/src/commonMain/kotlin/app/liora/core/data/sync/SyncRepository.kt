package app.liora.core.data.sync

import app.liora.core.data.backup.NewerBackupException
import app.liora.core.database.dao.LocalMetaDao
import app.liora.core.database.dao.SyncDao
import app.liora.core.database.model.LocalMetaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Instant

/** Why the last sync didn't go through. */
enum class SyncProblem {
    /** The store couldn't be reached. Syncing tries again by itself. */
    Unreachable,

    /** The user has to sign in again. */
    SignInNeeded,

    /** The store is out of space. */
    StoreFull,

    /** Another device runs a newer Liora, whose changes this one can't read. */
    NewerApp,
}

/** Whether this device syncs, to which account, and how the last sync went. */
data class SyncState(
    /** The account synced to; null while sync is off. */
    val account: String? = null,
    val lastSynced: Instant? = null,
    val problem: SyncProblem? = null,
    /** A sync is running right now. */
    val running: Boolean = false,
    /** How much of the user's space the store took after the last sync, in bytes, when it said. */
    val usedBytes: Long? = null,
    /** How many changes here haven't gone to the store yet. */
    val pending: Int = 0,
) {
    /** Syncing has stopped until the user does something: sign in again, make room, update Liora. */
    val needsYou: Boolean get() = account != null && problem != null && problem != SyncProblem.Unreachable
}

/** How one sync ended, for the background work that ran it. */
enum class SyncOutcome {
    Done,

    /** It couldn't reach the store; try again after a while. */
    TryLater,

    /** It can't go on until the user does something (see [SyncState.problem]). */
    Stopped,
}

/** Runs syncs in the background (WorkManager on Android). */
interface SyncScheduler {
    /** Whether a sync is running right now. */
    val running: Flow<Boolean>

    /** Syncs now and then from here on, until [stop]. */
    fun start()

    /** Syncs as soon as there's a network. */
    fun syncNow()

    fun stop()
}

/** Where an account's data syncs to: its Google Drive. */
interface SyncStores {
    fun open(account: String): SyncBackend

    /** Gives back this app's access to [account]'s store. What's stored there stays. */
    suspend fun forget(account: String)
}

/** Syncing this device's data with the user's own store, in the background. */
interface SyncRepository {
    val state: Flow<SyncState>

    /**
     * Syncs to [account] from now on. A new account starts over: it gets everything here, and this device
     * gets everything there. The same account again (after signing in again) carries on where it stopped.
     */
    suspend fun connect(account: String)

    /** Stops syncing and gives back access to the store. Nothing is deleted, here or there. */
    suspend fun disconnect()

    /** Syncs as soon as there's a network, if sync is on. */
    suspend fun syncNow()

    /** Runs one sync, as the background work does, and keeps how it went. */
    suspend fun sync(): SyncOutcome
}

internal class OfflineSyncRepository(
    private val engine: SyncEngine,
    private val stores: SyncStores,
    private val scheduler: SyncScheduler,
    private val meta: LocalMetaDao,
    private val syncDao: SyncDao,
) : SyncRepository {
    override val state: Flow<SyncState> =
        combine(
            meta.observe(ACCOUNT),
            engine.lastSynced,
            meta.observe(PROBLEM),
            scheduler.running,
            meta.observe(USED_BYTES),
        ) { account, lastSynced, problem, running, usedBytes ->
            SyncState(
                account = account,
                lastSynced = lastSynced,
                problem = SyncProblem.entries.firstOrNull { it.name == problem },
                running = running,
                usedBytes = usedBytes?.toLongOrNull(),
            )
        }.combine(syncDao.observePending()) { state, pending -> state.copy(pending = pending) }

    override suspend fun connect(account: String) {
        if (meta.get(ACCOUNT) != account) engine.startOver()
        meta.put(LocalMetaEntity(ACCOUNT, account))
        meta.delete(PROBLEM)
        scheduler.start()
        scheduler.syncNow()
    }

    override suspend fun disconnect() {
        val account = meta.get(ACCOUNT) ?: return
        scheduler.stop()
        meta.delete(ACCOUNT)
        meta.delete(PROBLEM)
        meta.delete(USED_BYTES)
        try {
            stores.forget(account)
        } catch (_: SyncUnreachableException) {
            // Offline: syncing has stopped anyway, and the account's own settings can still revoke access.
        }
    }

    override suspend fun syncNow() {
        if (meta.get(ACCOUNT) != null) scheduler.syncNow()
    }

    override suspend fun sync(): SyncOutcome {
        val account = meta.get(ACCOUNT) ?: return SyncOutcome.Done
        val backend = stores.open(account)
        val problem =
            try {
                engine.sync(backend)
                null
            } catch (_: SyncUnreachableException) {
                SyncProblem.Unreachable
            } catch (_: SyncSignInNeededException) {
                SyncProblem.SignInNeeded
            } catch (_: SyncStoreFullException) {
                SyncProblem.StoreFull
            } catch (_: NewerBackupException) {
                SyncProblem.NewerApp
            }
        // Disconnected while it ran: there's nothing to report to.
        if (meta.get(ACCOUNT) != account) return SyncOutcome.Done
        if (problem == null) {
            meta.delete(PROBLEM)
            rememberUsedBytes(backend)
        } else {
            meta.put(LocalMetaEntity(PROBLEM, problem.name))
        }
        return when (problem) {
            null -> SyncOutcome.Done
            SyncProblem.Unreachable -> SyncOutcome.TryLater
            else -> SyncOutcome.Stopped
        }
    }

    /** Asks the store how much space it takes now; a store that can't say right now keeps the last answer. */
    private suspend fun rememberUsedBytes(backend: SyncBackend) {
        val used =
            try {
                backend.usedBytes()
            } catch (_: SyncUnreachableException) {
                null
            } ?: return
        meta.put(LocalMetaEntity(USED_BYTES, used.toString()))
    }

    private companion object {
        // Kept in local_meta: each device chooses for itself whether and where it syncs.
        const val ACCOUNT = "sync_account"
        const val PROBLEM = "sync_problem"
        const val USED_BYTES = "sync_used_bytes"
    }
}
