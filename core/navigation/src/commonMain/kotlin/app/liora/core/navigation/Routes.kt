package app.liora.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Every destination in the app. Features register entries for these keys and navigate between each
// other only through them, so feature modules never depend on one another.

// Top-level tabs
@Serializable
data object TrainRoute : NavKey

@Serializable
data object HistoryRoute : NavKey

@Serializable
data object ProgressRoute : NavKey

@Serializable
data object ExercisesRoute : NavKey

// Full-screen destinations
@Serializable
data object LoggerRoute : NavKey

@Serializable
data object BodyRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data class ExerciseDetailRoute(
    val exerciseId: String,
) : NavKey

/**
 * Create (no id) or edit a custom exercise; optionally as a variation of a built-in or with a name typed
 * in search. With [resultKey], the saved exercise's id is handed back under that key.
 */
@Serializable
data class ExerciseEditorRoute(
    val exerciseId: String? = null,
    val variationOf: String? = null,
    val initialName: String? = null,
    val resultKey: String? = null,
) : NavKey

/** Choose one or more exercises; their ids come back, in the order picked, under [requestKey]. */
@Serializable
data class ExercisePickerRoute(
    val requestKey: String,
) : NavKey

@Serializable
data class RoutineDetailRoute(
    val routineId: String,
) : NavKey

/** Create (no id, optionally inside [folderId]) or edit a routine. */
@Serializable
data class RoutineEditorRoute(
    val routineId: String? = null,
    val folderId: String? = null,
) : NavKey
