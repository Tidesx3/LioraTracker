package app.liora.core.data.backup

import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.domain.WorkoutCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

/** Every finished workout as a spreadsheet, one row per set (see [WorkoutCsv]). */
interface WorkoutCsvExport {
    /**
     * Writes the CSV to [destination] (see [ExportFiles]), with exercise names in [language] and
     * unnamed workouts called [untitled]. Throws [ExportFileException] when the file can't be written.
     */
    suspend fun export(
        destination: String,
        language: String,
        untitled: String,
        zone: TimeZone,
    )
}

internal class OfflineWorkoutCsvExport(
    private val history: WorkoutHistoryRepository,
    private val exercises: ExerciseRepository,
    private val settings: SettingsRepository,
    private val files: ExportFiles,
) : WorkoutCsvExport {
    override suspend fun export(
        destination: String,
        language: String,
        untitled: String,
        zone: TimeZone,
    ) = withContext(NonCancellable) {
        val workouts = history.workouts.first()
        val names = exercises.observeExercises(language).first().associate { it.id to it.name }
        val units = settings.current().units
        val csv = withContext(Dispatchers.Default) { WorkoutCsv.format(workouts, names, units, zone, untitled) }
        files.writeText(destination, csv)
    }
}
