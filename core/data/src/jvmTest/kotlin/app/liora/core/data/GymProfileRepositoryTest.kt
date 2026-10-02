package app.liora.core.data

import app.liora.core.common.HybridLogicalClock
import app.liora.core.common.IdGenerator
import app.liora.core.data.gym.OfflineGymProfileRepository
import app.liora.core.data.settings.OfflineSettingsRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.domain.GymProfiles
import app.liora.core.model.DumbbellRun
import app.liora.core.model.GymProfile
import app.liora.core.model.Mass
import app.liora.core.model.PlatePairs
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Gyms sync; which one a device is at doesn't, and falls back sensibly. */
class GymProfileRepositoryTest {
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(1_790_000_000_000)
        }
    private val database = inMemoryLioraDatabase()
    private val dao = database.gymProfileDao()
    private val transactions = TransactionRunner(database)
    private val stamper = SyncStamper(HybridLogicalClock(clock) { "test-device" }, clock)
    private val settings = OfflineSettingsRepository(database.preferenceDao(), transactions, stamper)
    private val gyms =
        OfflineGymProfileRepository(
            dao,
            database.localMetaDao(),
            settings,
            transactions,
            IdGenerator(clock),
            stamper,
        )

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun withNoGymSetUpStandardEquipmentInTheChosenUnitApplies() =
        runTest {
            assertEquals(GymProfiles.standard(WeightUnit.Kilogram), gyms.active.first())

            settings.update { it.copy(units = Units(weight = WeightUnit.Pound)) }
            assertEquals(GymProfiles.standard(WeightUnit.Pound), gyms.active.first())
        }

    @Test
    fun aNewGymIsSavedWholeAndBecomesTheOneInUse() =
        runTest {
            val id = gyms.save(STUDIO)

            val saved = STUDIO.copy(id = id)
            assertEquals(listOf(saved), gyms.profiles.first())
            assertEquals(saved, gyms.active.first())
            assertEquals(saved, gyms.get(id))
        }

    @Test
    fun savingUnchangedWritesNothing() =
        runTest {
            val id = gyms.save(STUDIO)
            val written = dao.get(id)!!.sync

            gyms.save(STUDIO.copy(id = id))
            assertEquals(written, dao.get(id)!!.sync)

            gyms.save(STUDIO.copy(id = id, name = "Studio Süd"))
            assertEquals("Studio Süd", gyms.get(id)?.name)
            assertTrue(dao.get(id)!!.sync.hlc > written.hlc)
        }

    @Test
    fun switchingGymsAndDeletingTheOneInUse() =
        runTest {
            val home = gyms.save(STUDIO.copy(name = "Home"))
            val studio = gyms.save(STUDIO)
            assertEquals(studio, gyms.active.first().id)

            gyms.use(home)
            assertEquals(home, gyms.active.first().id)

            // Gone from the list, kept as a tombstone so the deletion syncs; the other gym takes over.
            gyms.delete(home)
            assertEquals(listOf(studio), gyms.profiles.first().map { it.id })
            assertNotNull(dao.get(home)?.sync?.deletedAt)
            assertEquals(studio, gyms.active.first().id)
        }

    private companion object {
        /** A gym with pound plates, to check the weights come back exactly. */
        val STUDIO =
            GymProfile(
                id = "",
                name = "Studio Nord",
                unit = WeightUnit.Pound,
                barbell = Mass.of(45.0, WeightUnit.Pound),
                ezBar = Mass.of(25.0, WeightUnit.Pound),
                plates =
                    listOf(
                        PlatePairs(Mass.of(45.0, WeightUnit.Pound), 6),
                        PlatePairs(Mass.of(2.5, WeightUnit.Pound), 2),
                    ),
                dumbbells =
                    listOf(
                        DumbbellRun(
                            Mass.of(5.0, WeightUnit.Pound),
                            Mass.of(120.0, WeightUnit.Pound),
                            Mass.of(5.0, WeightUnit.Pound),
                        ),
                    ),
                stackStep = Mass.of(10.0, WeightUnit.Pound),
            )
    }
}
