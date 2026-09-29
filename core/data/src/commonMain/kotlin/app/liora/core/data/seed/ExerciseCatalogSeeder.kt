package app.liora.core.data.seed

import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.dao.LocalMetaDao
import app.liora.core.database.model.ExerciseEntity
import app.liora.core.database.model.ExerciseNameEntity
import app.liora.core.database.model.LocalMetaEntity
import app.liora.core.database.model.SyncMetadata
import app.liora.core.model.Equipment
import app.liora.core.model.ExerciseCategory
import app.liora.core.model.Muscle
import app.liora.core.model.TrackingType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/** Supplies the bundled catalog JSON (built by `tools/seed/build-seed.mjs`), e.g. from Android assets. */
fun interface ExerciseSeedSource {
    suspend fun read(): String
}

/**
 * Loads the built-in exercise catalog into the database when the bundled seed is newer than what this
 * device has. Built-ins are upserted by stable id, so history and user settings survive re-seeding.
 */
class ExerciseCatalogSeeder(
    private val source: ExerciseSeedSource,
    private val exerciseDao: ExerciseDao,
    private val meta: LocalMetaDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    /** Returns true when the catalog was (re)written. */
    suspend fun seedIfNeeded(): Boolean {
        val installed = meta.get(KEY_SEED_VERSION)?.toIntOrNull() ?: 0
        val text = source.read()
        val bundled =
            VERSION_PATTERN
                .find(text.take(HEADER_LENGTH))
                ?.groupValues
                ?.get(1)
                ?.toInt() ?: return false
        if (bundled <= installed) return false

        val seed = json.decodeFromString<SeedFile>(text)
        val now = clock.now().toEpochMilliseconds()
        transactions.inTransaction {
            exerciseDao.deleteBuiltInNames()
            exerciseDao.upsertExercises(seed.exercises.map { it.toEntity(now) })
            exerciseDao.upsertNames(seed.exercises.flatMap { it.toNameEntities() })
            meta.put(LocalMetaEntity(KEY_SEED_VERSION, seed.version.toString()))
        }
        return true
    }

    private companion object {
        const val KEY_SEED_VERSION = "exercise_seed_version"
        const val HEADER_LENGTH = 200
        val VERSION_PATTERN = Regex("\"version\"\\s*:\\s*(\\d+)")
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class SeedFile(
    val version: Int,
    val exercises: List<SeedExercise>,
)

@Serializable
internal data class SeedExercise(
    val id: String,
    val names: Map<String, String>,
    val aliases: Map<String, List<String>> = emptyMap(),
    val tracking: String,
    val equipment: String,
    val category: String,
    val primary: List<String>,
    val secondary: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val rank: Int? = null,
) {
    fun toEntity(now: Long) =
        ExerciseEntity(
            id = id,
            name = names["en"] ?: names.values.first(),
            trackingType = TrackingType.fromKey(tracking) ?: TrackingType.WeightReps,
            equipment = Equipment.fromKey(equipment) ?: Equipment.Other,
            category = ExerciseCategory.fromKey(category) ?: ExerciseCategory.Strength,
            primaryMuscles = primary.mapNotNull(Muscle::fromKey).toSet(),
            secondaryMuscles = secondary.mapNotNull(Muscle::fromKey).toSet(),
            instructions = instructions,
            imagePaths = images,
            isCustom = false,
            variationOf = null,
            notes = null,
            rank = rank,
            // Built-ins are identical on every device, so they never need syncing.
            sync = SyncMetadata(createdAt = now, hlc = "", dirty = false),
        )

    fun toNameEntities(): List<ExerciseNameEntity> =
        names.map { (locale, name) -> ExerciseNameEntity(id, locale, name, isAlias = false) } +
            aliases.flatMap { (locale, list) ->
                list.filter { it != names[locale] }.map { ExerciseNameEntity(id, locale, it, isAlias = true) }
            }
}
