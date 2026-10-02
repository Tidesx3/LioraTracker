package app.liora.core.data

import androidx.room.useReaderConnection
import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.body.OfflineBodyRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.database.model.MeasurementEntity
import app.liora.core.database.model.SyncMetadata
import app.liora.core.model.MeasurementType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Body measurements saved a day at a time: added, corrected, cleared, and kept to one per day. */
class BodyRepositoryTest {
    private val zone = TimeZone.of("Europe/Vienna")
    private val clock = TestClock(at(2026, 9, 2, 7, 15))
    private val database = inMemoryLioraDatabase()
    private val dao = database.measurementDao()
    private val stamper = SyncStamper(HybridLogicalClock(clock) { "test-device" }, clock)
    private val body = OfflineBodyRepository(dao, TransactionRunner(database), IdGenerator(clock), stamper, clock)

    private val today = LocalDate(2026, 9, 2)

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun aDaysValuesAreSavedAsMeasurements() =
        runTest {
            body.saveDay(today, mapOf(MeasurementType.Bodyweight to 82.5, MeasurementType.Waist to 0.86), zone)
            body.saveDay(LocalDate(2026, 8, 26), mapOf(MeasurementType.Bodyweight to 83.4), zone)

            val saved = body.measurements.first()
            // Oldest first; today's count as taken now, an earlier day's at midday.
            assertEquals(listOf(83.4, 82.5, 0.86), saved.map { it.value })
            assertEquals(at(2026, 8, 26, 12, 0), saved[0].takenAt)
            assertEquals(clock.now(), saved[1].takenAt)
            assertTrue(dao.observeAll().first().all { it.sync.dirty })
        }

    @Test
    fun savingADayAgainCorrectsItsEntryInPlace() =
        runTest {
            body.saveDay(today, mapOf(MeasurementType.Bodyweight to 85.2), zone)
            val first = body.measurements.first().single()

            clock.now += 1.hours
            body.saveDay(today, mapOf(MeasurementType.Bodyweight to 82.5), zone)

            val corrected = body.measurements.first().single()
            assertEquals(first.id, corrected.id)
            assertEquals(first.takenAt, corrected.takenAt)
            assertEquals(82.5, corrected.value)
        }

    @Test
    fun clearingATypeTombstonesItAndLeavesTheRest() =
        runTest {
            body.saveDay(today, mapOf(MeasurementType.Bodyweight to 82.5, MeasurementType.Waist to 0.86), zone)
            body.saveDay(LocalDate(2026, 9, 1), mapOf(MeasurementType.Waist to 0.87), zone)
            val todaysWaist = body.measurements.first().single { it.type == MeasurementType.Waist && it.value == 0.86 }

            body.saveDay(today, mapOf(MeasurementType.Waist to null), zone)

            val left = body.measurements.first()
            assertEquals(
                listOf(MeasurementType.Waist to 0.87, MeasurementType.Bodyweight to 82.5),
                left.map { it.type to it.value },
            )
            // Synced rows are never deleted, only tombstoned.
            assertNotNull(deletedAt(todaysWaist.id))
        }

    @Test
    fun aDayWithSeveralEntriesKeepsOne() =
        runTest {
            // Morning and evening readings, e.g. from another app.
            dao.upsertAll(
                listOf(
                    row("a", at(2026, 9, 1, 7, 0), 82.4),
                    row("b", at(2026, 9, 1, 21, 0), 83.1),
                ),
            )

            body.saveDay(LocalDate(2026, 9, 1), mapOf(MeasurementType.Bodyweight to 82.6), zone)

            val left = body.measurements.first().single()
            assertEquals("b", left.id)
            assertEquals(82.6, left.value)
        }

    @Test
    fun typesFromANewerVersionAreSkipped() =
        runTest {
            dao.upsertAll(listOf(row("x", at(2026, 9, 1, 7, 0), 1.0, type = "wingspan")))
            body.saveDay(today, mapOf(MeasurementType.Bodyweight to 82.5), zone)

            assertEquals(listOf(MeasurementType.Bodyweight), body.measurements.first().map { it.type })
            assertNotNull(dao.observeAll().first().singleOrNull { it.type == "wingspan" })
        }

    /** A row's tombstone time; fails if the row was hard-deleted. */
    private suspend fun deletedAt(id: String): Long? =
        database.useReaderConnection { connection ->
            connection.usePrepared("SELECT deleted_at FROM measurement WHERE id = ?") { row ->
                row.bindText(1, id)
                check(row.step()) { "$id was hard-deleted" }
                if (row.isNull(0)) null else row.getLong(0)
            }
        }

    private fun row(
        id: String,
        takenAt: Instant,
        value: Double,
        type: String = MeasurementType.Bodyweight.key,
    ) = MeasurementEntity(id, takenAt.toEpochMilliseconds(), type, value, SyncMetadata(createdAt = 0, hlc = "0"))

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ) = LocalDateTime(year, month, day, hour, minute).toInstant(zone)

    private class TestClock(
        var now: Instant,
    ) : Clock {
        override fun now() = now
    }
}
