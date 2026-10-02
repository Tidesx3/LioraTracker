package app.liora.feature.body.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.body.BodyRepository
import app.liora.core.data.body.ProgressPhotoRepository
import app.liora.core.domain.BodyMeasurements
import app.liora.core.domain.DayValue
import app.liora.core.domain.ProgressPhotos
import app.liora.core.domain.TimeApart
import app.liora.core.domain.TrainingCalendar
import app.liora.core.model.MeasurementType
import app.liora.core.model.PhotoPose
import app.liora.core.model.ProgressPhoto
import app.liora.core.navigation.ComparePhotosRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

enum class CompareSide { Before, After }

/** A photo being compared, with its day and what the scale said around then. */
data class ComparedPhoto(
    val photo: ProgressPhoto,
    val day: LocalDate,
    val bodyweight: Double?,
)

data class CompareUiState(
    val loading: Boolean = true,
    /** The pose to choose from; null for all. */
    val pose: PhotoPose? = null,
    /** The photos to choose from, oldest first. */
    val choices: List<ProgressPhoto> = emptyList(),
    val before: ComparedPhoto? = null,
    val after: ComparedPhoto? = null,
    /** The side a chosen photo goes to. */
    val active: CompareSide = CompareSide.Before,
    val apart: TimeApart? = null,
    /** From before to after, when the scale was used around both days. */
    val weightChange: Double? = null,
)

/** Two progress photos side by side, the earlier on the left; either side can be swapped. */
class CompareViewModel(
    private val route: ComparePhotosRoute,
    private val repository: ProgressPhotoRepository,
    body: BodyRepository,
) : ViewModel() {
    private val zone = TimeZone.currentSystemDefault()
    private val selection = MutableStateFlow<Selection?>(null)

    init {
        viewModelScope.launch { selection.value = initialSelection(repository.photos.first()) }
    }

    val uiState: StateFlow<CompareUiState> =
        combine(repository.photos, body.measurements, selection.filterNotNull()) { photos, measurements, chosen ->
            val weights = BodyMeasurements.daily(measurements, MeasurementType.Bodyweight, zone)
            val byId = photos.associateBy { it.id }
            val before = chosen.beforeId?.let(byId::get)?.let { compared(it, weights) }
            val after = chosen.afterId?.let(byId::get)?.let { compared(it, weights) }
            CompareUiState(
                loading = false,
                pose = chosen.pose,
                choices = photos.filter { chosen.pose == null || it.pose == chosen.pose },
                before = before,
                after = after,
                active = chosen.active,
                apart = if (before != null && after != null) ProgressPhotos.apart(before.day, after.day) else null,
                weightChange =
                    before?.bodyweight?.let { start -> after?.bodyweight?.let { end -> end - start } },
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState())

    /** Shows [pose]'s photos, starting again with its first and latest. */
    fun selectPose(pose: PhotoPose?) {
        viewModelScope.launch {
            val photos = repository.photos.first()
            val pair = ProgressPhotos.firstAndLatest(photos, pose)
            val only = photos.lastOrNull { pose == null || it.pose == pose }
            selection.value = Selection(pose, pair?.before?.id, pair?.after?.id ?: only?.id, CompareSide.Before)
        }
    }

    fun activate(side: CompareSide) {
        selection.value = selection.value?.copy(active = side)
    }

    /** Puts [photo] on the active side; should it be the later of the two, the sides swap. */
    fun choose(photo: ProgressPhoto) {
        val current = selection.value ?: return
        val state = uiState.value
        var before = if (current.active == CompareSide.Before) photo else state.before?.photo
        var after = if (current.active == CompareSide.After) photo else state.after?.photo
        var active = current.active
        if (before != null && after != null && before.takenAt > after.takenAt) {
            before = after.also { after = before }
            active = if (active == CompareSide.Before) CompareSide.After else CompareSide.Before
        }
        selection.value = current.copy(beforeId = before?.id, afterId = after?.id, active = active)
    }

    private fun compared(
        photo: ProgressPhoto,
        weights: List<DayValue>,
    ): ComparedPhoto {
        val day = TrainingCalendar.dayOf(photo.takenAt, zone)
        return ComparedPhoto(photo, day, BodyMeasurements.around(weights, day)?.value)
    }

    /** What the route asked for, or else [ProgressPhotos.opening]. */
    private fun initialSelection(photos: List<ProgressPhoto>): Selection {
        val byId = photos.associateBy { it.id }
        val after = route.afterId?.let(byId::get)
        val before = route.beforeId?.let(byId::get) ?: after?.let { ProgressPhotos.startFor(photos, it) }
        if (before != null || after != null) {
            // Narrow the choice to their pose only when they share one.
            val shared = listOfNotNull(before, after).map { it.pose }.distinct().singleOrNull()
            return Selection(shared, before?.id, after?.id, CompareSide.Before)
        }
        val pair = ProgressPhotos.opening(photos)
        val pose = pair?.before?.pose?.takeIf { it == pair.after.pose }
        return Selection(pose, pair?.before?.id, pair?.after?.id ?: photos.lastOrNull()?.id, CompareSide.Before)
    }

    private data class Selection(
        val pose: PhotoPose?,
        val beforeId: String?,
        val afterId: String?,
        val active: CompareSide,
    )
}
