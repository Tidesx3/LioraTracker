package app.liora.feature.body.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.body.BodyRepository
import app.liora.core.model.MeasurementType
import app.liora.feature.body.MeasurementSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone

sealed interface MeasurementDetailUiState {
    data object Loading : MeasurementDetailUiState

    /** [summary] is null once nothing is logged, e.g. after the last entry was cleared. */
    data class Loaded(
        val summary: MeasurementSummary?,
    ) : MeasurementDetailUiState
}

/** One measurement: its chart, how far it moved, and every day it was taken. */
class MeasurementDetailViewModel(
    val type: MeasurementType,
    body: BodyRepository,
) : ViewModel() {
    val uiState: StateFlow<MeasurementDetailUiState> =
        body.measurements
            .map { all ->
                MeasurementDetailUiState.Loaded(MeasurementSummary.of(all, type, TimeZone.currentSystemDefault()))
            }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasurementDetailUiState.Loading)
}
