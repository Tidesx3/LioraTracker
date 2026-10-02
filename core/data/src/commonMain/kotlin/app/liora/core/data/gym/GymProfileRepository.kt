package app.liora.core.data.gym

import app.liora.core.common.IdGenerator
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.GymProfileDao
import app.liora.core.database.dao.LocalMetaDao
import app.liora.core.database.model.GymProfileEntity
import app.liora.core.database.model.LocalMetaEntity
import app.liora.core.database.model.SyncMetadata
import app.liora.core.domain.GymProfiles
import app.liora.core.model.DumbbellRun
import app.liora.core.model.GymProfile
import app.liora.core.model.Mass
import app.liora.core.model.PlatePairs
import app.liora.core.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The gyms the user trains at, which sync, and which one this device is at, which doesn't. */
interface GymProfileRepository {
    /** The gyms set up, in the order they were added. */
    val profiles: Flow<List<GymProfile>>

    /**
     * The gym weights round to: the one chosen on this device, else the first one set up, else
     * [standard equipment][GymProfiles.standard] in the weight unit chosen in Settings.
     */
    val active: Flow<GymProfile>

    suspend fun get(id: String): GymProfile?

    /** Saves [profile] and returns its id. A new one (blank id) becomes the gym in use on this device. */
    suspend fun save(profile: GymProfile): String

    /** Removes a gym. If this device was at it, the first remaining one takes over. */
    suspend fun delete(id: String)

    /** Uses [id]'s equipment on this device from now on. */
    suspend fun use(id: String)
}

internal class OfflineGymProfileRepository(
    private val dao: GymProfileDao,
    private val meta: LocalMetaDao,
    private val settings: SettingsRepository,
    private val transactions: TransactionRunner,
    private val ids: IdGenerator,
    private val stamper: SyncStamper,
) : GymProfileRepository {
    override val profiles: Flow<List<GymProfile>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }.distinctUntilChanged()

    override val active: Flow<GymProfile> =
        combine(
            profiles,
            meta.observe(ACTIVE_KEY),
            settings.settings.map { it.units.weight }.distinctUntilChanged(),
        ) { gyms, chosen, unit ->
            gyms.firstOrNull { it.id == chosen } ?: gyms.firstOrNull() ?: GymProfiles.standard(unit)
        }.distinctUntilChanged()

    override suspend fun get(id: String): GymProfile? = dao.get(id)?.takeIf { it.sync.deletedAt == null }?.toModel()

    override suspend fun save(profile: GymProfile): String =
        transactions.inTransaction {
            val existing = profile.id.takeIf { it.isNotBlank() }?.let { dao.get(it) }
            if (existing == null) {
                val id = ids.newId()
                dao.upsert(profile.toEntity(id, stamper.newRow()))
                meta.put(LocalMetaEntity(ACTIVE_KEY, id))
                id
            } else {
                val updated = profile.toEntity(existing.id, existing.sync)
                if (updated != existing) dao.upsert(updated.copy(sync = stamper.touch(existing.sync)))
                existing.id
            }
        }

    override suspend fun delete(id: String) {
        transactions.inTransaction {
            val existing = dao.get(id)
            if (existing != null && existing.sync.deletedAt == null) {
                dao.upsert(existing.copy(sync = stamper.tombstone(existing.sync)))
            }
            if (meta.get(ACTIVE_KEY) == id) meta.delete(ACTIVE_KEY)
        }
    }

    override suspend fun use(id: String) {
        if (profiles.first().any { it.id == id }) meta.put(LocalMetaEntity(ACTIVE_KEY, id))
    }

    private companion object {
        /** Local only: two devices can be at different gyms. */
        const val ACTIVE_KEY = "active_gym_profile"
    }
}

private val json = Json { ignoreUnknownKeys = true }

private val unitKeys = mapOf(WeightUnit.Kilogram to "kg", WeightUnit.Pound to "lb")

@Serializable
private class PlatesRow(
    val kg: Double,
    val pairs: Int,
)

@Serializable
private class DumbbellsRow(
    val fromKg: Double,
    val toKg: Double,
    val stepKg: Double,
)

private fun GymProfile.toEntity(
    id: String,
    sync: SyncMetadata,
) = GymProfileEntity(
    id = id,
    name = name.trim(),
    unit = unitKeys.getValue(unit),
    barbellKg = barbell.kilograms,
    ezBarKg = ezBar.kilograms,
    plates = json.encodeToString(plates.map { PlatesRow(it.weight.kilograms, it.pairs) }),
    dumbbells =
        json.encodeToString(
            dumbbells.map { DumbbellsRow(it.from.kilograms, it.to.kilograms, it.step.kilograms) },
        ),
    stackStepKg = stackStep.kilograms,
    sync = sync,
)

/** Lists that don't parse (written by a newer version, say) read as empty rather than failing. */
private fun GymProfileEntity.toModel() =
    GymProfile(
        id = id,
        name = name,
        unit = unitKeys.entries.firstOrNull { it.value == unit }?.key ?: WeightUnit.Kilogram,
        barbell = Mass(barbellKg),
        ezBar = Mass(ezBarKg),
        plates =
            runCatching { json.decodeFromString<List<PlatesRow>>(plates) }
                .getOrDefault(emptyList())
                .map { PlatePairs(Mass(it.kg), it.pairs) },
        dumbbells =
            runCatching { json.decodeFromString<List<DumbbellsRow>>(dumbbells) }
                .getOrDefault(emptyList())
                .map { DumbbellRun(Mass(it.fromKg), Mass(it.toKg), Mass(it.stepKg)) },
        stackStep = Mass(stackStepKg),
    )
