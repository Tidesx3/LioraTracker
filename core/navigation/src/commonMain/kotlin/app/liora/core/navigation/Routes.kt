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

/** One measurement's chart and entries, by its type's stable key (e.g. `bodyweight`). */
@Serializable
data class MeasurementDetailRoute(
    val typeKey: String,
) : NavKey

/** What was measured on one day, to log or correct; [day] is an ISO date such as `2026-10-02`, null for today. */
@Serializable
data class LogMeasurementsRoute(
    val day: String? = null,
) : NavKey

@Serializable
data object ProgressPhotosRoute : NavKey

@Serializable
data class ProgressPhotoRoute(
    val photoId: String,
) : NavKey

/**
 * Two progress photos side by side. Without ids it starts with the first and latest in the pose
 * photographed most recently; with only [afterId], that photo against the first before it.
 */
@Serializable
data class ComparePhotosRoute(
    val beforeId: String? = null,
    val afterId: String? = null,
) : NavKey

/** A month looked back on, starting with the current one. */
@Serializable
data object MonthlyReportRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

/** The gyms set up, and which one weights round to. */
@Serializable
data object GymsRoute : NavKey

/** Set up a new gym (no id) or change one. */
@Serializable
data class GymEditorRoute(
    val gymId: String? = null,
) : NavKey

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

/**
 * Choose exercises; their ids come back, in the order picked, under [requestKey]. Without [multiple],
 * the first tap picks and returns (e.g. to replace an exercise).
 */
@Serializable
data class ExercisePickerRoute(
    val requestKey: String,
    val multiple: Boolean = true,
) : NavKey

@Serializable
data class RoutineDetailRoute(
    val routineId: String,
) : NavKey

/** A finished workout from history. */
@Serializable
data class WorkoutDetailRoute(
    val workoutId: String,
) : NavKey

/** A finished workout opened in the logger to correct it: its sets, exercises and times. */
@Serializable
data class EditWorkoutRoute(
    val workoutId: String,
) : NavKey

/** Create (no id, optionally inside [folderId]) or edit a routine. */
@Serializable
data class RoutineEditorRoute(
    val routineId: String? = null,
    val folderId: String? = null,
) : NavKey
