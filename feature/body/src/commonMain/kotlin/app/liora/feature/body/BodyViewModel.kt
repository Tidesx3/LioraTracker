package app.liora.feature.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.body.BodyRepository
import app.liora.core.domain.BodyMeasurements
import app.liora.core.domain.DayValue
import app.liora.core.domain.MeasurementChange
import app.liora.core.model.Measurement
import app.liora.core.model.MeasurementType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone

sealed interface BodyUiState {
    data object Loading : BodyUiState

    /** Nothing measured yet. */
    data object Empty : BodyUiState

    /** Every type measured so far, bodyweight first, in [MeasurementType] order. */
    data class Loaded(
        val summaries: List<MeasurementSummary>,
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

/** The Body page: bodyweight with its chart, and the latest of every other measurement. */
class BodyViewModel(
    body: BodyRepository,
) : ViewModel() {
    val uiState: StateFlow<BodyUiState> =
        body.measurements
            .map { all ->
                val zone = TimeZone.currentSystemDefault()
                val summaries = MeasurementType.entries.mapNotNull { MeasurementSummary.of(all, it, zone) }
                if (summaries.isEmpty()) BodyUiState.Empty else BodyUiState.Loaded(summaries)
            }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyUiState.Loading)
}
