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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

sealed interface PhotoUiState {
    data object Loading : PhotoUiState

    /** Deleted; the screen closes itself. */
    data object Gone : PhotoUiState

    data class Loaded(
        val photo: ProgressPhoto,
        val day: LocalDate,
        /** What the scale said around then. */
        val bodyweight: Double?,
    ) : PhotoUiState
}

/** One progress photo: its pose and day can be corrected, and it can go. */
class PhotoViewModel(
    private val photoId: String,
    private val repository: ProgressPhotoRepository,
    body: BodyRepository,
    clock: Clock,
) : ViewModel() {
    private val zone = TimeZone.currentSystemDefault()

    /** The latest day the photo can move to. */
    val today: LocalDate = TrainingCalendar.dayOf(clock.now(), zone)

    val uiState: StateFlow<PhotoUiState> =
        combine(repository.photos, body.measurements) { photos, measurements ->
            val photo = photos.firstOrNull { it.id == photoId }
            if (photo == null) {
                PhotoUiState.Gone
            } else {
                val day = TrainingCalendar.dayOf(photo.takenAt, zone)
                val weights = BodyMeasurements.daily(measurements, MeasurementType.Bodyweight, zone)
                PhotoUiState.Loaded(photo, day, BodyMeasurements.around(weights, day)?.value)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotoUiState.Loading)

    /** Sets the pose; choosing the one it has already clears it. */
    fun togglePose(pose: PhotoPose) {
        val current = (uiState.value as? PhotoUiState.Loaded)?.photo?.pose
        viewModelScope.launch { repository.setPose(photoId, pose.takeUnless { it == current }) }
    }

    fun moveTo(day: LocalDate) {
        viewModelScope.launch { repository.moveTo(photoId, day, zone) }
    }

    /** The screen closes once the photo is gone, so the delete isn't cut short. */
    fun delete() {
        viewModelScope.launch { repository.delete(photoId) }
    }
}
