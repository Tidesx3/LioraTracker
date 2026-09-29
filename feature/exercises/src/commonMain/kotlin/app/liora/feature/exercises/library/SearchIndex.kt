package app.liora.feature.exercises.library

import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.domain.ExerciseSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The catalog as a search index in [language], rebuilt off the main thread whenever either changes. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun ExerciseRepository.searchIndex(
    language: Flow<String?>,
    scope: CoroutineScope,
): StateFlow<ExerciseSearch?> =
    language
        .filterNotNull()
        .flatMapLatest { observeExercises(it) }
        .map(::ExerciseSearch)
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

/** Keeps the index through a configuration change or a quick trip to another screen. */
private const val STOP_TIMEOUT_MS = 5_000L
