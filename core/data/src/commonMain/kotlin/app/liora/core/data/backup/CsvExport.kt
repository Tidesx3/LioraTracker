package app.liora.core.data.backup

import app.liora.core.data.body.BodyRepository
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.WorkoutHistoryRepository
import app.liora.core.domain.BodyCsv
import app.liora.core.domain.CsvContent
import app.liora.core.domain.WorkoutCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

/**
 * Workouts (one row per set, see [WorkoutCsv]), body measurements (one row per day, see [BodyCsv]) or
 * both in one file, as a spreadsheet.
 */
interface CsvExport {
    /**
     * Writes [content] as CSV to [destination] (see [ExportFiles]), with exercise names in [language] and
     * unnamed workouts called [untitled]. Throws [ExportFileException] when the file can't be written.
     */
    suspend fun export(
        destination: String,
        content: CsvContent,
        language: String,
        untitled: String,
        zone: TimeZone,
    )
}

internal class OfflineCsvExport(
    private val history: WorkoutHistoryRepository,
    private val body: BodyRepository,
    private val exercises: ExerciseRepository,
    private val settings: SettingsRepository,
    private val files: ExportFiles,
) : CsvExport {
    override suspend fun export(
        destination: String,
        content: CsvContent,
        language: String,
        untitled: String,
        zone: TimeZone,
    ) = withContext(NonCancellable) {
        val units = settings.current().units
        val workouts = if (content == CsvContent.Body) emptyList() else history.workouts.first()
        val measurements = if (content == CsvContent.Workouts) emptyList() else body.measurements.first()
        val names =
            if (content == CsvContent.Body) {
                emptyMap()
            } else {
                exercises.observeExercises(language).first().associate { it.id to it.name }
            }
        val csv =
            withContext(Dispatchers.Default) {
                when (content) {
                    CsvContent.Workouts -> WorkoutCsv.format(workouts, names, units, zone, untitled)
                    CsvContent.Body -> BodyCsv.format(measurements, units, zone)
                    CsvContent.Both -> WorkoutCsv.formatWithBody(workouts, measurements, names, units, zone, untitled)
                }
            }
        files.writeText(destination, csv)
    }
}
