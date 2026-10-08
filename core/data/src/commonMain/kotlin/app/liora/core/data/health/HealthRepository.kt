package app.liora.core.data.health

import app.liora.core.common.Hlc
import app.liora.core.data.body.BodyReading
import app.liora.core.data.body.BodyRepository
import app.liora.core.database.dao.LocalMetaDao
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.database.model.LocalMetaEntity
import app.liora.core.database.model.WorkoutEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** A finished workout as Health Connect keeps it: an exercise session. */
data class HealthSession(
    /** The workout's id, by which Health Connect replaces the session when the workout changes. */
    val id: String,
    val title: String?,
    val start: Instant,
    val end: Instant,
    val notes: String?,
    /** Grows with every change to the workout, so Health Connect keeps the newest. */
    val version: Long,
)

/** Readings since a cursor, and the cursor to read from next. */
class HealthReadings(
    val readings: List<BodyReading>,
    val cursor: String?,
)

/** Whether Health Connect is on this phone. */
enum class HealthAvailability {
    Available,
    NotInstalled,
    UpdateNeeded,
}

/** What Health Connect lets Liora do on this phone. */
data class HealthAccess(
    val availability: HealthAvailability,
    /** Saving finished workouts as exercise sessions. */
    val writeWorkouts: Boolean = false,
    /** Bringing in weight and body fat. */
    val readBody: Boolean = false,
) {
    val connected: Boolean get() = availability == HealthAvailability.Available && (writeWorkouts || readBody)
}

/** Health Connect on this phone. Throws [HealthUnavailableException] when it can't be reached. */
interface HealthStore {
    suspend fun access(): HealthAccess

    /** Adds [sessions], replacing earlier versions of them. */
    suspend fun writeSessions(sessions: List<HealthSession>)

    /** Removes the sessions of the workouts with these [ids]. */
    suspend fun deleteSessions(ids: List<String>)

    /**
     * Weight and body fat that other apps wrote since [cursor] (null: all Liora may read), and the cursor
     * to read from next. Only the types Liora may read.
     */
    suspend fun readings(cursor: String?): HealthReadings

    /** Gives back every permission Liora has. */
    suspend fun revoke()
}

/** Health Connect was there a moment ago, but not now (an update, a permission taken back). */
class HealthUnavailableException(
    cause: Throwable,
) : Exception("Health Connect can't be reached", cause)

/**
 * Liora and Health Connect: finished workouts go out as exercise sessions, and weight and body fat come
 * in, for the days that have none of their own here.
 */
interface HealthRepository {
    /** What Health Connect lets Liora do; null until it's been asked (see [refresh]). */
    val access: StateFlow<HealthAccess?>

    /** Asks Health Connect again, e.g. after its permission screen or its settings. */
    suspend fun refresh(): HealthAccess

    /**
     * Saves the workouts Health Connect doesn't have as they are now (all past ones, the first time), removes
     * deleted ones, and brings in new readings, as far as Health Connect allows each. Returns how many
     * readings came in.
     */
    suspend fun exchange(): Int

    /** Gives back Liora's permissions. What's in Health Connect and here stays. */
    suspend fun turnOff()
}

internal class OfflineHealthRepository(
    private val store: HealthStore,
    private val workouts: WorkoutDao,
    private val body: BodyRepository,
    private val meta: LocalMetaDao,
) : HealthRepository {
    private val state = MutableStateFlow<HealthAccess?>(null)
    override val access: StateFlow<HealthAccess?> = state.asStateFlow()
    private val mutex = Mutex()

    override suspend fun refresh(): HealthAccess {
        val access =
            try {
                store.access()
            } catch (_: HealthUnavailableException) {
                // There, but not answering: nothing is allowed until it does.
                HealthAccess(HealthAvailability.Available)
            }
        state.value = access
        return access
    }

    override suspend fun exchange(): Int =
        mutex.withLock {
            try {
                val access = refresh()
                if (access.availability != HealthAvailability.Available) return@withLock 0
                if (access.writeWorkouts) saveWorkouts()
                if (access.readBody) bringInReadings() else 0
            } catch (_: HealthUnavailableException) {
                0
            }
        }

    override suspend fun turnOff() {
        try {
            store.revoke()
        } catch (_: HealthUnavailableException) {
            // Health Connect's own settings can still take the permissions back.
        }
        refresh()
    }

    /**
     * Writes the workouts whose version Health Connect doesn't have yet and removes deleted ones. What it
     * has is kept here by workout, not as a point in time: a workout synced from another phone can arrive
     * with an older timestamp than the last one saved.
     */
    private suspend fun saveWorkouts() {
        val saved =
            meta
                .get(
                    SAVED,
                )?.let { runCatching { json.decodeFromString(savedSerializer, it) }.getOrNull() }
                .orEmpty()
        val finished = workouts.finishedWithDeleted()
        val (deleted, live) = finished.partition { it.sync.deletedAt != null }
        val sessions = live.map { it.toSession() }.filter { saved[it.id] != it.version }
        val gone = deleted.map { it.id }.filter { it in saved }
        if (sessions.isEmpty() && gone.isEmpty()) return
        store.writeSessions(sessions)
        store.deleteSessions(gone)
        val now = saved - gone.toSet() + sessions.associate { it.id to it.version }
        meta.put(LocalMetaEntity(SAVED, json.encodeToString(savedSerializer, now)))
    }

    private suspend fun bringInReadings(): Int {
        val read = store.readings(meta.get(READ_CURSOR))
        val added = body.addMissing(read.readings, TimeZone.currentSystemDefault())
        read.cursor?.let { meta.put(LocalMetaEntity(READ_CURSOR, it)) }
        return added
    }

    private fun WorkoutEntity.toSession(): HealthSession {
        val hlc = Hlc.parse(sync.hlc)
        return HealthSession(
            id = id,
            title = name,
            start = Instant.fromEpochMilliseconds(startedAt),
            end = Instant.fromEpochMilliseconds(checkNotNull(endedAt)),
            notes = notes,
            // The HLC's milliseconds and counter, which only grow, as one number.
            version = hlc.millis * COUNTER_RANGE + hlc.counter,
        )
    }

    private companion object {
        // Kept in local_meta: each phone has its own Health Connect.
        const val SAVED = "health_saved"
        const val READ_CURSOR = "health_read_cursor"
        const val COUNTER_RANGE = 0x10000L
        val json = Json
        val savedSerializer = MapSerializer(String.serializer(), Long.serializer())
    }
}
