package app.liora.core.data.body

import app.liora.core.common.IdGenerator
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.MeasurementDao
import app.liora.core.database.model.MeasurementEntity
import app.liora.core.domain.BodyMeasurements
import app.liora.core.model.Measurement
import app.liora.core.model.MeasurementType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.Instant

/** Bodyweight, body fat and circumferences, kept by day. */
interface BodyRepository {
    /** Every measurement, oldest first. */
    val measurements: Flow<List<Measurement>>

    /**
     * Saves what was measured on [day] in [zone]'s calendar. Each type in [values] gets its value,
     * replacing what the day had for it, or loses the day's entries when the value is null. Types not
     * in [values] stay as they are.
     */
    suspend fun saveDay(
        day: LocalDate,
        values: Map<MeasurementType, Double?>,
        zone: TimeZone,
    )
}

internal class OfflineBodyRepository(
    private val dao: MeasurementDao,
    private val transactions: TransactionRunner,
    private val ids: IdGenerator,
    private val stamper: SyncStamper,
    private val clock: Clock,
) : BodyRepository {
    override val measurements: Flow<List<Measurement>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toModel() } }

    override suspend fun saveDay(
        day: LocalDate,
        values: Map<MeasurementType, Double?>,
        zone: TimeZone,
    ) = transactions.inTransaction {
        val from = day.atStartOfDayIn(zone).toEpochMilliseconds()
        val until = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
        val takenAt = BodyMeasurements.takenAt(day, clock.now(), zone).toEpochMilliseconds()
        val changed =
            values.flatMap { (type, value) ->
                val existing = dao.between(type.key, from, until)
                val kept = existing.lastOrNull()
                when {
                    value == null -> {
                        existing.map { it.copy(sync = stamper.tombstone(it.sync)) }
                    }

                    kept == null -> {
                        listOf(MeasurementEntity(ids.newId(), takenAt, type.key, value, stamper.newRow()))
                    }

                    // The day keeps one value: its last entry takes it, and any earlier ones go.
                    else -> {
                        existing.dropLast(1).map { it.copy(sync = stamper.tombstone(it.sync)) } +
                            kept.copy(value = value, sync = stamper.touch(kept.sync))
                    }
                }
            }
        dao.upsertAll(changed)
    }
}

/** Null for a type this app version doesn't know, e.g. one synced from a newer version. */
private fun MeasurementEntity.toModel(): Measurement? =
    MeasurementType.fromKey(type)?.let { Measurement(id, it, Instant.fromEpochMilliseconds(takenAt), value) }
