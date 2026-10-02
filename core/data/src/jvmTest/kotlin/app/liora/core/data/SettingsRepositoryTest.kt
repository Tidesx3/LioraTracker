package app.liora.core.data

import app.liora.core.common.HybridLogicalClock
import app.liora.core.data.settings.OfflineSettingsRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.inMemoryLioraDatabase
import app.liora.core.database.model.PreferenceEntity
import app.liora.core.database.model.SyncMetadata
import app.liora.core.domain.OneRepMaxFormula
import app.liora.core.domain.Settings
import app.liora.core.domain.ThemeMode
import app.liora.core.model.DistanceUnit
import app.liora.core.model.LengthUnit
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Settings in the synced preference table: defaults, changes, and only what changed is written. */
class SettingsRepositoryTest {
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(1_790_000_000_000)
        }
    private val database = inMemoryLioraDatabase()
    private val dao = database.preferenceDao()
    private val settings =
        OfflineSettingsRepository(
            dao,
            TransactionRunner(database),
            SyncStamper(HybridLogicalClock(clock) { "test-device" }, clock),
        )

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun aNewInstallHasTheDefaults() =
        runTest {
            assertEquals(Settings(), settings.settings.first())
        }

    @Test
    fun onlyTheChangedChoiceIsWritten() =
        runTest {
            settings.update { it.copy(theme = ThemeMode.Dark) }

            assertEquals(ThemeMode.Dark, settings.settings.first().theme)
            // Nothing else: another device's choices must not be overwritten with this one's defaults.
            assertEquals(listOf("appearance.theme"), dao.all().map { it.key })

            settings.update { it.copy(oneRepMaxFormula = OneRepMaxFormula.Brzycki, stallWindow = 28.days) }
            settings.update { it.copy(rest = it.rest.copy(working = 150.seconds)) }
            val stored = settings.settings.first()
            assertEquals(OneRepMaxFormula.Brzycki, stored.oneRepMaxFormula)
            assertEquals(28.days, stored.stallWindow)
            assertEquals(150.seconds, stored.rest.working)
            assertEquals(Settings().rest.warmup, stored.rest.warmup)
        }

    @Test
    fun unitsAndTheRpeColumnAreKept() =
        runTest {
            settings.update {
                it.copy(
                    rpe = true,
                    units = Units(WeightUnit.Pound, DistanceUnit.Mile, LengthUnit.Inch),
                )
            }

            val stored = settings.settings.first()
            assertEquals(true, stored.rpe)
            assertEquals(Units(WeightUnit.Pound, DistanceUnit.Mile, LengthUnit.Inch), stored.units)
            assertEquals(
                mapOf(
                    "logger.rpe" to "true",
                    "units.weight" to "lb",
                    "units.distance" to "mi",
                    "units.body_length" to "in",
                ),
                dao.all().associate { it.key to it.value },
            )

            // Only pounds back to kilograms: the distance stays in miles.
            settings.update { it.copy(units = it.units.copy(weight = WeightUnit.Kilogram)) }
            assertEquals(Units(WeightUnit.Kilogram, DistanceUnit.Mile, LengthUnit.Inch), settings.current().units)
        }

    @Test
    fun valuesFromANewerVersionFallBackToTheDefault() =
        runTest {
            dao.upsertAll(
                listOf(
                    PreferenceEntity("progress.e1rm_formula", "lombardi", SyncMetadata(createdAt = 0, hlc = "0")),
                    PreferenceEntity("appearance.theme", "dark", SyncMetadata(createdAt = 0, hlc = "0")),
                ),
            )
            val read = settings.settings.first()
            assertEquals(OneRepMaxFormula.Epley, read.oneRepMaxFormula)
            assertEquals(ThemeMode.Dark, read.theme)
        }
}
