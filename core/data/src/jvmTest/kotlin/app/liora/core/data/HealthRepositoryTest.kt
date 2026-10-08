package app.liora.core.data

import app.liora.core.data.body.BodyReading
import app.liora.core.data.health.HealthAccess
import app.liora.core.data.health.HealthAvailability
import app.liora.core.data.health.HealthReadings
import app.liora.core.data.health.HealthSession
import app.liora.core.data.health.HealthStore
import app.liora.core.data.health.HealthUnavailableException
import app.liora.core.data.health.OfflineHealthRepository
import app.liora.core.database.model.SyncMetadata
import app.liora.core.database.model.WorkoutEntity
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** Workouts out to Health Connect as sessions, weight and body fat in for the days without their own. */
class HealthRepositoryTest {
    private val phone = TestPhone("phone")
    private val store = FakeHealthStore()
    private val health =
        OfflineHealthRepository(store, phone.database.workoutDao(), phone.body, phone.database.localMetaDao())

    @AfterTest
    fun tearDown() = phone.database.close()

    @Test
    fun theFirstTimeEveryFinishedWorkoutGoesOut() =
        runTest {
            phone.trainForAWhile()

            health.exchange()

            // Two finished; the deleted one was never there, and the one in progress isn't finished.
            assertEquals(
                listOf("Legs", "Push"),
                store.sessions.values
                    .mapNotNull { it.title }
                    .sorted(),
            )
            assertTrue(store.deleted.isEmpty())
            val legs = store.sessions.values.single { it.title == "Legs" }
            val workout =
                phone.history.workouts
                    .first()
                    .single { it.name == "Legs" }
            assertEquals(workout.id, legs.id)
            assertTrue(legs.end >= legs.start)
        }

    @Test
    fun afterThatOnlyChangesGoOut() =
        runTest {
            phone.trainForAWhile()
            health.exchange()
            val writes = store.writes

            health.exchange()
            assertEquals(writes, store.writes)

            val legs =
                phone.history.workouts
                    .first()
                    .single { it.name == "Legs" }
            val before = store.sessions.getValue(legs.id).version
            phone.clock.advance(1.hours)
            phone.history.edit(legs.id).rename("Leg day")
            health.exchange()
            assertEquals("Leg day", store.sessions.getValue(legs.id).title)
            assertTrue(store.sessions.getValue(legs.id).version > before)

            phone.history.delete(legs.id)
            health.exchange()
            assertEquals(listOf(legs.id), store.deleted)
        }

    @Test
    fun aWorkoutSyncedInWithAnOlderStampStillGoesOut() =
        runTest {
            phone.trainForAWhile()
            health.exchange()

            // From another phone, finished long ago and only synced now.
            phone.database.workoutDao().upsert(
                WorkoutEntity(
                    id = "from-the-tablet",
                    name = "Arms",
                    startedAt = 0,
                    endedAt = 3_600_000,
                    routineId = null,
                    notes = null,
                    bodyweightKg = null,
                    sync = SyncMetadata(createdAt = 0, hlc = "0000000000001:0000:tablet", dirty = false),
                ),
            )
            health.exchange()

            assertEquals("Arms", store.sessions.getValue("from-the-tablet").title)
        }

    @Test
    fun readingsFillOnlyTheDaysWithoutTheirOwn() =
        runTest {
            phone.body.saveDay(MONDAY, mapOf(MeasurementType.Bodyweight to 82.5), ZONE)
            store.readings =
                listOf(
                    // Monday has its own weight: it stays.
                    BodyReading(MeasurementType.Bodyweight, at(MONDAY, 7), 80.0),
                    // Tuesday: the evening's weigh-in counts.
                    BodyReading(MeasurementType.Bodyweight, at(TUESDAY, 7), 81.2),
                    BodyReading(MeasurementType.Bodyweight, at(TUESDAY, 20), 81.8),
                    BodyReading(MeasurementType.BodyFat, at(TUESDAY, 7), 18.5),
                )

            assertEquals(2, health.exchange())

            val measurements = phone.body.measurements.first()
            assertEquals(
                listOf(82.5, 81.8),
                measurements.filter { it.type == MeasurementType.Bodyweight }.map { it.value },
            )
            assertEquals(18.5, measurements.single { it.type == MeasurementType.BodyFat }.value)

            // The next read carries on from where this one stopped.
            store.readings = emptyList()
            health.exchange()
            assertEquals(listOf(null, "cursor-1"), store.cursorsAsked)
        }

    @Test
    fun onlyWhatHealthConnectAllowsHappens() =
        runTest {
            phone.trainForAWhile()
            store.readings = listOf(BodyReading(MeasurementType.Bodyweight, at(TUESDAY, 7), 81.2))

            store.access = HealthAccess(HealthAvailability.NotInstalled)
            assertEquals(0, health.exchange())
            assertEquals(0, store.writes)

            store.access = HealthAccess(HealthAvailability.Available, writeWorkouts = true, readBody = false)
            assertEquals(0, health.exchange())
            assertTrue(store.sessions.isNotEmpty())
            assertTrue(store.cursorsAsked.isEmpty())
            assertEquals(store.access, health.access.value)
        }

    @Test
    fun healthConnectGoingAwayMidwayIsNoProblem() =
        runTest {
            phone.trainForAWhile()
            store.failure = HealthUnavailableException(IllegalStateException("updating"))
            assertEquals(0, health.exchange())

            store.failure = null
            health.exchange()
            assertEquals(2, store.sessions.size)
        }

    @Test
    fun turningOffGivesThePermissionsBack() =
        runTest {
            health.turnOff()
            assertFalse(health.access.value!!.connected)
        }

    private fun at(
        day: LocalDate,
        hour: Int,
    ) = LocalDateTime(day.year, day.month, day.day, hour, 0).toInstant(ZONE)

    /** Health Connect in memory, granting everything until told otherwise. */
    private class FakeHealthStore : HealthStore {
        var access = HealthAccess(HealthAvailability.Available, writeWorkouts = true, readBody = true)
        var failure: HealthUnavailableException? = null
        val sessions = mutableMapOf<String, HealthSession>()
        val deleted = mutableListOf<String>()
        var writes = 0
        var readings = emptyList<BodyReading>()
        val cursorsAsked = mutableListOf<String?>()

        override suspend fun access(): HealthAccess = access

        override suspend fun writeSessions(sessions: List<HealthSession>) {
            failure?.let { throw it }
            writes++
            sessions.forEach { this.sessions[it.id] = it }
        }

        override suspend fun deleteSessions(ids: List<String>) {
            deleted += ids
            sessions -= ids.toSet()
        }

        override suspend fun readings(cursor: String?): HealthReadings {
            cursorsAsked += cursor
            return HealthReadings(readings, "cursor-${cursorsAsked.size}")
        }

        override suspend fun revoke() {
            access = HealthAccess(HealthAvailability.Available)
        }
    }

    private companion object {
        val ZONE = TimeZone.currentSystemDefault()
        val MONDAY = LocalDate(2026, 9, 28)
        val TUESDAY = LocalDate(2026, 9, 29)
    }
}
