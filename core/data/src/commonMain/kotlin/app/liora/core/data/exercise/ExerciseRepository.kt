package app.liora.core.data.exercise

import app.liora.core.common.IdGenerator
import app.liora.core.data.sync.SyncStamper
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.model.ExerciseEntity
import app.liora.core.database.model.ExerciseNameEntity
import app.liora.core.database.model.ExerciseSettingsEntity
import app.liora.core.database.model.SyncMetadata
import app.liora.core.model.Exercise
import app.liora.core.model.ExerciseCategory
import app.liora.core.model.ExerciseDraft
import app.liora.core.model.ExerciseSettings
import app.liora.core.model.Mass
import app.liora.core.model.TrackingType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface ExerciseRepository {
    /** All non-deleted exercises with names resolved for [language] (ISO 639-1, e.g. `de`). */
    fun observeExercises(language: String): Flow<List<Exercise>>

    fun observeExercise(
        id: String,
        language: String,
    ): Flow<Exercise?>

    /** Creates a custom exercise and returns its id. */
    suspend fun createCustom(draft: ExerciseDraft): String

    /**
     * Updates a custom exercise. Throws [TrackingTypeLockedException] when the tracking type changes
     * although sets were already logged with the old one.
     */
    suspend fun updateCustom(
        id: String,
        draft: ExerciseDraft,
    )

    /**
     * The exercise's own note (seat height, grip, the plan), shown under it in every workout and routine
     * and on its page, for built-in and custom exercises alike. A blank note clears it.
     */
    suspend fun setNote(
        id: String,
        note: String?,
    )

    /**
     * A machine or cable's own load step, for a stack that doesn't move in the gym's; null goes back to
     * the gym's. Progression and the logger's ± add it to the weight lifted.
     */
    suspend fun setLoadStep(
        id: String,
        step: Mass?,
    )

    /** Hides an exercise from pickers and the library; its history stays intact. */
    suspend fun setArchived(
        id: String,
        archived: Boolean,
    )

    /** Whether a workout or routine uses the exercise. Used exercises can only be archived. */
    suspend fun isInUse(id: String): Boolean

    suspend fun hasLoggedSets(id: String): Boolean

    /** Deletes an unused custom exercise. Throws [ExerciseInUseException] if it has history. */
    suspend fun deleteCustom(id: String)
}

class TrackingTypeLockedException : IllegalStateException("Tracking type can't change once sets are logged")

class ExerciseInUseException : IllegalStateException("Exercise is used by a workout or routine")

internal class OfflineExerciseRepository(
    private val exerciseDao: ExerciseDao,
    private val transactions: TransactionRunner,
    private val ids: IdGenerator,
    private val stamper: SyncStamper,
) : ExerciseRepository {
    override fun observeExercises(language: String): Flow<List<Exercise>> =
        combine(
            exerciseDao.observeAll(),
            exerciseDao.observeNames(),
            exerciseDao.observeSettings(),
        ) { exercises, names, settings ->
            val namesById = names.groupBy { it.exerciseId }
            val settingsById = settings.associateBy { it.exerciseId }
            val byId = exercises.associateBy { it.id }
            exercises.map {
                it.toModel(namesById[it.id].orEmpty(), settingsById[it.id], language, imagesOf(it, byId))
            }
        }

    override fun observeExercise(
        id: String,
        language: String,
    ): Flow<Exercise?> =
        observeExercises(language)
            .map { all ->
                all.firstOrNull { it.id == id }
            }.distinctUntilChanged()

    override suspend fun createCustom(draft: ExerciseDraft): String {
        require(draft.isValid) { "A custom exercise needs a name" }
        val id = ids.newId()
        transactions.inTransaction {
            exerciseDao.upsertExercises(listOf(draft.toEntity(id, stamper.newRow())))
            exerciseDao.upsertNames(listOf(customName(id, draft.name)))
            writeNote(exerciseDao, stamper, id, draft.notes)
        }
        return id
    }

    override suspend fun updateCustom(
        id: String,
        draft: ExerciseDraft,
    ) {
        require(draft.isValid) { "A custom exercise needs a name" }
        transactions.inTransaction {
            val existing = exerciseDao.get(id)
            require(
                existing != null && existing.isCustom && existing.sync.deletedAt == null,
            ) { "No custom exercise $id" }
            if (existing.trackingType != draft.trackingType && exerciseDao.hasLoggedSets(id)) {
                throw TrackingTypeLockedException()
            }
            exerciseDao.upsertExercises(listOf(draft.toEntity(id, stamper.touch(existing.sync))))
            exerciseDao.deleteNames(id)
            exerciseDao.upsertNames(listOf(customName(id, draft.name)))
            writeNote(exerciseDao, stamper, id, draft.notes)
        }
    }

    override suspend fun setNote(
        id: String,
        note: String?,
    ) {
        transactions.inTransaction { writeNote(exerciseDao, stamper, id, note) }
    }

    override suspend fun setLoadStep(
        id: String,
        step: Mass?,
    ) {
        val kilograms = step?.kilograms?.takeIf { it > 0 }
        transactions.inTransaction {
            if (exerciseDao.getSettings(id)?.takeIf { it.sync.deletedAt == null }?.loadStepKg == kilograms) {
                return@inTransaction
            }
            editSettings(exerciseDao, stamper, id) { copy(loadStepKg = kilograms) }
        }
    }

    override suspend fun setArchived(
        id: String,
        archived: Boolean,
    ) {
        transactions.inTransaction { editSettings(exerciseDao, stamper, id) { copy(archived = archived) } }
    }

    override suspend fun isInUse(id: String): Boolean = exerciseDao.isInUse(id)

    override suspend fun hasLoggedSets(id: String): Boolean = exerciseDao.hasLoggedSets(id)

    override suspend fun deleteCustom(id: String) {
        transactions.inTransaction {
            val existing = exerciseDao.get(id) ?: return@inTransaction
            require(existing.isCustom) { "Built-in exercises can only be archived" }
            if (exerciseDao.isInUse(id)) throw ExerciseInUseException()
            exerciseDao.upsertExercises(listOf(existing.copy(sync = stamper.tombstone(existing.sync))))
            exerciseDao.deleteNames(id)
            exerciseDao.getSettings(id)?.let { exerciseDao.upsertSettings(it.copy(sync = stamper.tombstone(it.sync))) }
        }
    }
}

/**
 * Changes an exercise's settings row, creating it with nothing overridden if there is none yet. Call it
 * inside a transaction.
 */
internal suspend fun editSettings(
    exerciseDao: ExerciseDao,
    stamper: SyncStamper,
    id: String,
    change: ExerciseSettingsEntity.() -> ExerciseSettingsEntity,
) {
    val existing = exerciseDao.getSettings(id)
    val base =
        existing?.copy(sync = stamper.touch(existing.sync).copy(deletedAt = null))
            ?: ExerciseSettingsEntity(
                exerciseId = id,
                stickyNote = null,
                restWorkingSeconds = null,
                restWarmupSeconds = null,
                archived = false,
                sync = stamper.newRow(),
            )
    exerciseDao.upsertSettings(base.change())
}

/** Makes [note] the exercise's own note, trimmed, unless it already is. Call it inside a transaction. */
private suspend fun writeNote(
    exerciseDao: ExerciseDao,
    stamper: SyncStamper,
    id: String,
    note: String?,
) {
    val text = note?.trim()?.ifEmpty { null }
    if (exerciseDao.getSettings(id)?.takeIf { it.sync.deletedAt == null }?.stickyNote == text) return
    editSettings(exerciseDao, stamper, id) { copy(stickyNote = text) }
}

/** A custom exercise is found by the one name the user gave it, in any language. */
internal fun customName(
    id: String,
    name: String,
) = ExerciseNameEntity(id, ExerciseNameEntity.LOCALE_ANY, name.trim(), isAlias = false)

internal fun ExerciseDraft.toEntity(
    id: String,
    sync: SyncMetadata,
) = ExerciseEntity(
    id = id,
    name = name.trim(),
    trackingType = trackingType,
    equipment = equipment,
    category =
        if (trackingType ==
            TrackingType.DistanceDuration
        ) {
            ExerciseCategory.Cardio
        } else {
            ExerciseCategory.Strength
        },
    primaryMuscles = primaryMuscles,
    secondaryMuscles = secondaryMuscles - primaryMuscles,
    instructions = emptyList(),
    imagePaths = emptyList(),
    isCustom = true,
    variationOf = variationOf,
    // The note is the exercise's own, kept in its settings like a built-in's (see `setNote`).
    notes = null,
    rank = null,
    sync = sync,
)

/**
 * The exercise's own pictures, or for a variation without any (custom ones have none) its base
 * exercise's, following variations of variations. Only shown, never stored on the variation.
 */
internal fun imagesOf(
    exercise: ExerciseEntity,
    byId: Map<String, ExerciseEntity>,
): List<String> {
    var current = exercise
    val seen = mutableSetOf(current.id)
    while (current.imagePaths.isEmpty()) {
        current = current.variationOf?.takeIf(seen::add)?.let(byId::get) ?: break
    }
    return current.imagePaths
}

internal fun ExerciseEntity.toModel(
    names: List<ExerciseNameEntity>,
    settings: ExerciseSettingsEntity?,
    language: String,
    images: List<String> = imagePaths,
): Exercise {
    fun primaryName(locale: String) = names.firstOrNull { it.locale == locale && !it.isAlias }?.name
    val displayName =
        primaryName(language)
            ?: primaryName(FALLBACK_LANGUAGE)
            ?: primaryName(ExerciseNameEntity.LOCALE_ANY)
            ?: name
    return Exercise(
        id = id,
        name = displayName,
        searchTerms = (names.map { it.name } + name).distinct() - displayName,
        trackingType = trackingType,
        equipment = equipment,
        category = category,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        instructions = instructions,
        imageUrls = images.map(::imageUrl),
        isCustom = isCustom,
        variationOf = variationOf,
        notes = notes,
        rank = rank,
        settings =
            settings?.takeIf { it.sync.deletedAt == null }?.let {
                ExerciseSettings(
                    stickyNote = it.stickyNote,
                    restWorkingSeconds = it.restWorkingSeconds,
                    restWarmupSeconds = it.restWarmupSeconds,
                    archived = it.archived,
                    loadStep = it.loadStepKg?.let(::Mass),
                )
            } ?: ExerciseSettings(),
    )
}

/** Seed images are paths inside free-exercise-db; anything with a scheme is already a URL. */
private fun imageUrl(path: String): String =
    if ("://" in path ||
        path.startsWith("content:")
    ) {
        path
    } else {
        SEED_IMAGE_BASE + path
    }

private const val FALLBACK_LANGUAGE = "en"

/** Pinned to the same commit as the seed, so images always match the catalog. */
private const val SEED_IMAGE_BASE =
    "https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/"
