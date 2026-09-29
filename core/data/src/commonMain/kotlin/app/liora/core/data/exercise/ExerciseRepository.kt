package app.liora.core.data.exercise

import app.liora.core.database.dao.ExerciseDao
import app.liora.core.database.model.ExerciseEntity
import app.liora.core.database.model.ExerciseNameEntity
import app.liora.core.database.model.ExerciseSettingsEntity
import app.liora.core.model.Exercise
import app.liora.core.model.ExerciseSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface ExerciseRepository {
    /** All non-deleted exercises with names resolved for [language] (ISO 639-1, e.g. `de`). */
    fun observeExercises(language: String): Flow<List<Exercise>>
}

internal class OfflineExerciseRepository(
    private val exerciseDao: ExerciseDao,
) : ExerciseRepository {
    override fun observeExercises(language: String): Flow<List<Exercise>> =
        combine(
            exerciseDao.observeAll(),
            exerciseDao.observeNames(),
            exerciseDao.observeSettings(),
        ) { exercises, names, settings ->
            val namesById = names.groupBy { it.exerciseId }
            val settingsById = settings.associateBy { it.exerciseId }
            exercises.map { it.toModel(namesById[it.id].orEmpty(), settingsById[it.id], language) }
        }
}

internal fun ExerciseEntity.toModel(
    names: List<ExerciseNameEntity>,
    settings: ExerciseSettingsEntity?,
    language: String,
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
        imagePaths = imagePaths,
        isCustom = isCustom,
        variationOf = variationOf,
        notes = notes,
        rank = rank,
        settings =
            settings?.let {
                ExerciseSettings(
                    stickyNote = it.stickyNote,
                    restWorkingSeconds = it.restWorkingSeconds,
                    restWarmupSeconds = it.restWarmupSeconds,
                    archived = it.archived,
                )
            } ?: ExerciseSettings(),
    )
}

private const val FALLBACK_LANGUAGE = "en"
