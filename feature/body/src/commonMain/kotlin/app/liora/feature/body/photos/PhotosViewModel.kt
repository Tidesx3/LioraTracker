package app.liora.feature.body.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.body.BodyRepository
import app.liora.core.data.body.ProgressPhotoRepository
import app.liora.core.domain.BodyMeasurements
import app.liora.core.domain.TrainingCalendar
import app.liora.core.model.MeasurementType
import app.liora.core.model.PhotoPose
import app.liora.core.model.ProgressPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** One day's photos, front to back, with what the scale said around then. */
data class PhotoDay(
    val day: LocalDate,
    val bodyweight: Double?,
    val photos: List<ProgressPhoto>,
)

data class PhotosUiState(
    val loading: Boolean = true,
    /** The pose shown; null for all. */
    val pose: PhotoPose? = null,
    /** Newest day first. */
    val days: List<PhotoDay> = emptyList(),
    /** Every photo, whatever [pose] shows. */
    val total: Int = 0,
    /** Photos still being brought in. */
    val adding: Int = 0,
    /** How many of the last ones brought in couldn't be read; told once. */
    val failed: Int = 0,
)

/** The progress photo gallery: by day, filtered by pose, and where new photos come in. */
class PhotosViewModel(
    private val repository: ProgressPhotoRepository,
    body: BodyRepository,
) : ViewModel() {
    private val pose = MutableStateFlow<PhotoPose?>(null)
    private val imports = MutableStateFlow(Imports())

    val uiState: StateFlow<PhotosUiState> =
        combine(repository.photos, body.measurements, pose, imports) { photos, measurements, pose, imports ->
            val zone = TimeZone.currentSystemDefault()
            val weights = BodyMeasurements.daily(measurements, MeasurementType.Bodyweight, zone)
            val days =
                photos
                    .filter { pose == null || it.pose == pose }
                    .groupBy { TrainingCalendar.dayOf(it.takenAt, zone) }
                    .map { (day, onDay) ->
                        PhotoDay(day, BodyMeasurements.around(weights, day)?.value, onDay.sortedWith(FrontToBack))
                    }.sortedByDescending { it.day }
            PhotosUiState(false, pose, days, photos.size, imports.running, imports.failed)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotosUiState())

    fun selectPose(value: PhotoPose?) {
        pose.value = value
    }

    /** Brings in photos from the camera or the gallery, tagged with [pose]. */
    fun add(
        sources: List<String>,
        pose: PhotoPose?,
    ) {
        imports.update { it.copy(running = it.running + sources.size) }
        viewModelScope.launch {
            // Chosen photos come in even if the gallery is left meanwhile.
            withContext(NonCancellable) {
                var failed = 0
                sources.forEach { source ->
                    if (repository.add(source, pose) == null) failed++
                    imports.update { it.copy(running = it.running - 1) }
                }
                if (failed > 0) imports.update { it.copy(failed = failed) }
            }
        }
    }

    fun failureShown() {
        imports.update { it.copy(failed = 0) }
    }

    private data class Imports(
        val running: Int = 0,
        val failed: Int = 0,
    )

    private companion object {
        /** Front, side, back, then photos without a pose, each in the order taken. */
        val FrontToBack: Comparator<ProgressPhoto> =
            compareBy<ProgressPhoto> { it.pose?.ordinal ?: PhotoPose.entries.size }.thenBy { it.takenAt }
    }
}
