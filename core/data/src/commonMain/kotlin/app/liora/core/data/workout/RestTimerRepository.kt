package app.liora.core.data.workout

import app.liora.core.database.dao.LocalMetaDao
import app.liora.core.database.model.LocalMetaEntity
import app.liora.core.model.RestTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The rest countdown between sets. Stored device-locally (never synced), so the logger, the workout
 * notification and the end-of-rest alarm all read the same timer, even after the app was killed.
 */
interface RestTimerRepository {
    /** The current countdown, or null. One that has run out stays until the next start or stop. */
    val timer: Flow<RestTimer?>

    suspend fun current(): RestTimer?

    suspend fun start(duration: Duration)

    /** Adds time, or with a negative [by] takes some away; running out this way stops the timer. */
    suspend fun adjust(by: Duration)

    suspend fun stop()
}

internal class LocalRestTimerRepository(
    private val meta: LocalMetaDao,
    private val clock: Clock,
) : RestTimerRepository {
    override val timer: Flow<RestTimer?> = meta.observe(KEY).map { it?.let(::decode) }.distinctUntilChanged()

    override suspend fun current(): RestTimer? = meta.get(KEY)?.let(::decode)

    override suspend fun start(duration: Duration) {
        val now = clock.now()
        write(RestTimer(startedAt = now, endsAt = now + duration))
    }

    override suspend fun adjust(by: Duration) {
        val timer = current() ?: return
        val now = clock.now()
        if (timer.isOver(now)) return
        val endsAt = timer.endsAt + by
        if (endsAt <= now) stop() else write(timer.copy(endsAt = endsAt))
    }

    override suspend fun stop() = meta.delete(KEY)

    private suspend fun write(timer: RestTimer) =
        meta.put(
            LocalMetaEntity(
                KEY,
                "${timer.startedAt.toEpochMilliseconds()}$SEPARATOR${timer.endsAt.toEpochMilliseconds()}",
            ),
        )

    private fun decode(value: String): RestTimer? {
        val (start, end) =
            value.split(SEPARATOR).mapNotNull { it.toLongOrNull() }.takeIf { it.size == 2 }
                ?: return null
        return RestTimer(Instant.fromEpochMilliseconds(start), Instant.fromEpochMilliseconds(end))
    }

    private companion object {
        const val KEY = "rest_timer"
        const val SEPARATOR = ","
    }
}
