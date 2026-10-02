package app.liora.feature.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.backup.BackupContents
import app.liora.core.data.backup.BackupRepository
import app.liora.core.data.backup.ExportFileException
import app.liora.core.data.backup.InvalidBackupException
import app.liora.core.data.backup.NewerBackupException
import app.liora.core.data.backup.WorkoutCsvExport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

/** What the Data section is busy with. */
enum class DataWork { BackingUp, Reading, Restoring, Exporting }

/** How a piece of work ended, shown once. */
enum class DataMessage { BackupSaved, Restored, NothingNew, CsvSaved, SaveFailed, ReadFailed, NotABackup, NewerBackup }

/** What a backup holds, shown before it's restored. */
data class BackupSummary(
    val exportedAt: Instant,
    val workouts: Int,
    val routines: Int,
    val customExercises: Int,
    val measurements: Int,
    val photos: Int,
)

data class DataUiState(
    val working: DataWork? = null,
    /** A backup that was read and waits for the user to restore it. */
    val pending: BackupSummary? = null,
    val message: DataMessage? = null,
)

/**
 * Backing up, restoring and exporting, one at a time. The work itself finishes even if the screen
 * closes (see [BackupRepository]); only the message about it is lost then.
 */
class DataViewModel(
    private val backups: BackupRepository,
    private val csv: WorkoutCsvExport,
    private val clock: Clock,
) : ViewModel() {
    private val state = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = state.asStateFlow()

    private var pending: BackupContents? = null

    /** Today, which names the files. */
    fun today(): LocalDate = clock.todayIn(TimeZone.currentSystemDefault())

    fun backUp(destination: String) =
        run(DataWork.BackingUp) {
            backups.backUp(destination)
            DataMessage.BackupSaved
        }

    /** Reads the backup at [source]; restoring it waits for [restore]. */
    fun read(source: String) =
        run(DataWork.Reading) {
            val contents = backups.read(source)
            pending = contents
            state.update { it.copy(pending = contents.summary()) }
            null
        }

    fun restore() {
        val contents = pending ?: return
        pending = null
        state.update { it.copy(pending = null) }
        run(DataWork.Restoring) {
            if (backups.restore(contents) > 0) DataMessage.Restored else DataMessage.NothingNew
        }
    }

    fun cancelRestore() {
        pending = null
        state.update { it.copy(pending = null) }
    }

    /** Exports workouts as CSV, with exercise names in [language] and unnamed workouts called [untitled]. */
    fun exportCsv(
        destination: String,
        language: String,
        untitled: String,
    ) = run(DataWork.Exporting) {
        csv.export(destination, language, untitled, TimeZone.currentSystemDefault())
        DataMessage.CsvSaved
    }

    fun messageShown() {
        state.update { it.copy(message = null) }
    }

    /** Does [block] unless something else is running; it returns the message to show, if any. */
    private fun run(
        work: DataWork,
        block: suspend () -> DataMessage?,
    ) {
        if (state.value.working != null) return
        state.update { it.copy(working = work, message = null) }
        viewModelScope.launch {
            val message =
                try {
                    block()
                } catch (_: ExportFileException) {
                    if (work == DataWork.Reading ||
                        work == DataWork.Restoring
                    ) {
                        DataMessage.ReadFailed
                    } else {
                        DataMessage.SaveFailed
                    }
                } catch (_: InvalidBackupException) {
                    DataMessage.NotABackup
                } catch (_: NewerBackupException) {
                    DataMessage.NewerBackup
                }
            state.update { it.copy(working = null, message = message) }
        }
    }
}

private fun BackupContents.summary() =
    BackupSummary(
        exportedAt = exportedAt,
        workouts = workouts,
        routines = routines,
        customExercises = customExercises,
        measurements = measurements,
        photos = photos,
    )
