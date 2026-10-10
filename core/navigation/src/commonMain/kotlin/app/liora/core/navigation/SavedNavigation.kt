package app.liora.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Every route, so a back stack can be written down and read back after Android ends the process (e.g.
 * while the camera app takes a progress photo). A new route in Routes.kt goes here too; one missing makes
 * the stack unsavable, and the app then starts afresh as if this weren't here.
 */
private val routes =
    SerializersModule {
        polymorphic(NavKey::class) {
            subclass(TrainRoute::class)
            subclass(HistoryRoute::class)
            subclass(ProgressRoute::class)
            subclass(ExercisesRoute::class)
            subclass(LoggerRoute::class)
            subclass(BodyRoute::class)
            subclass(MeasurementDetailRoute::class)
            subclass(LogMeasurementsRoute::class)
            subclass(ProgressPhotosRoute::class)
            subclass(ProgressPhotoRoute::class)
            subclass(ComparePhotosRoute::class)
            subclass(MonthlyReportRoute::class)
            subclass(SettingsRoute::class)
            subclass(SyncRoute::class)
            subclass(HealthConnectRoute::class)
            subclass(ImportRoute::class)
            subclass(GymsRoute::class)
            subclass(GymEditorRoute::class)
            subclass(ExerciseDetailRoute::class)
            subclass(ExerciseEditorRoute::class)
            subclass(ExercisePickerRoute::class)
            subclass(RoutineDetailRoute::class)
            subclass(WorkoutDetailRoute::class)
            subclass(EditWorkoutRoute::class)
            subclass(WorkoutDoneRoute::class)
            subclass(RoutineEditorRoute::class)
        }
    }

private val json = Json { serializersModule = routes }

/** A navigator's tabs and their stacks, as text; each route by its serial name, which R8 leaves alone. */
@Serializable
private class SavedNavigator(
    val topLevel: Int,
    val stacks: List<List<NavKey>>,
)

/** This navigator's tabs and back stacks as text, or null when one of its routes can't be written. */
fun Navigator.save(): String? =
    try {
        val (topLevel, stacks) = snapshot()
        json.encodeToString(SavedNavigator.serializer(), SavedNavigator(topLevel, stacks))
    } catch (_: SerializationException) {
        null
    }

/** Puts back what [save] wrote; text it can't read (another version's) leaves the navigator as it is. */
fun Navigator.restore(saved: String) {
    val navigator =
        try {
            json.decodeFromString(SavedNavigator.serializer(), saved)
        } catch (_: SerializationException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }
    restore(navigator.topLevel, navigator.stacks)
}
