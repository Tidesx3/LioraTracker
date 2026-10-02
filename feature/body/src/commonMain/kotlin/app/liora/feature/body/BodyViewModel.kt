package app.liora.feature.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.body.BodyRepository
import app.liora.core.data.body.ProgressPhotoRepository
import app.liora.core.domain.BodyMeasurements
import app.liora.core.domain.DayValue
import app.liora.core.domain.MeasurementChange
import app.liora.core.model.Measurement
import app.liora.core.model.MeasurementType
import app.liora.core.model.ProgressPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone

sealed interface BodyUiState {
    data object Loading : BodyUiState

    data class Loaded(
        /** Every type measured so far, bodyweight first, in [MeasurementType] order; empty before the first. */
        val summaries: List<MeasurementSummary>,
        /** The latest few progress photos, newest first. */
        val recentPhotos: List<ProgressPhoto>,
        val photoCount: Int,
    ) : BodyUiState
}

/** One measurement by day, oldest first, and how far it moved over the last month. */
data class MeasurementSummary(
    val type: MeasurementType,
    val days: List<DayValue>,
    val change: MeasurementChange?,
) {
    val latest: DayValue get() = days.last()

    companion object {
        /** Null while [type] has never been measured. */
        fun of(
            measurements: List<Measurement>,
            type: MeasurementType,
            zone: TimeZone,
        ): MeasurementSummary? {
            val days = BodyMeasurements.daily(measurements, type, zone)
            return if (days.isEmpty()) null else MeasurementSummary(type, days, BodyMeasurements.change(days))
        }
    }
}

/** The Body page: bodyweight with its chart, progress photos, and the latest of every other measurement. */
class BodyViewModel(
    body: BodyRepository,
    photos: ProgressPhotoRepository,
) : ViewModel() {
    val uiState: StateFlow<BodyUiState> =
        combine(body.measurements, photos.photos) { measurements, allPhotos ->
            val zone = TimeZone.currentSystemDefault()
            BodyUiState.Loaded(
                summaries = MeasurementType.entries.mapNotNull { MeasurementSummary.of(measurements, it, zone) },
                recentPhotos = allPhotos.takeLast(RECENT_PHOTOS).reversed(),
                photoCount = allPhotos.size,
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyUiState.Loading)
}

/** How many of the latest photos the Body page shows. */
internal const val RECENT_PHOTOS = 4
