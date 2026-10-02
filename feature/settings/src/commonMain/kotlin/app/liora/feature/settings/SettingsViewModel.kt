package app.liora.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.gym.GymProfileRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.domain.Settings
import app.liora.core.model.GymProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A change to one choice, applied to the settings as stored. */
internal typealias SettingsChange = (Settings) -> Settings

/** Settings as they stand; null until read. Each change is saved as it's made. */
class SettingsViewModel(
    private val repository: SettingsRepository,
    gyms: GymProfileRepository,
) : ViewModel() {
    val settings: StateFlow<Settings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The gym weights round to on this device. */
    val gym: StateFlow<GymProfile?> = gyms.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Saves [change]. It applies to the settings as stored rather than as last shown, so a choice that
     * changed meanwhile isn't put back.
     */
    internal fun update(change: SettingsChange) {
        viewModelScope.launch { repository.update(change) }
    }
}
