package app.liora.feature.settings.gym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.gym.GymProfileRepository
import app.liora.core.model.GymProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GymsUiState(
    val loading: Boolean = true,
    val gyms: List<GymProfile> = emptyList(),
    /** The gym weights round to on this device. */
    val inUse: String? = null,
)

class GymsViewModel(
    private val repository: GymProfileRepository,
) : ViewModel() {
    val uiState: StateFlow<GymsUiState> =
        combine(repository.profiles, repository.active) { gyms, active ->
            GymsUiState(loading = false, gyms = gyms, inUse = active.id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GymsUiState())

    fun use(id: String) {
        viewModelScope.launch { repository.use(id) }
    }
}
