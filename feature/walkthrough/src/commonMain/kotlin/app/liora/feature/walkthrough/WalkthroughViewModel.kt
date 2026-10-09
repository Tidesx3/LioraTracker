package app.liora.feature.walkthrough

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.sync.SyncRepository
import app.liora.core.data.walkthrough.WalkthroughRepository
import app.liora.core.model.DistanceUnit
import app.liora.core.model.LengthUnit
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the walkthrough's choices stand at; null until read. */
internal data class WalkthroughUiState(
    val weightUnit: WeightUnit? = null,
    /** The account synced to, once sync is on. */
    val syncAccount: String? = null,
)

class WalkthroughViewModel(
    private val settings: SettingsRepository,
    private val sync: SyncRepository,
    private val walkthrough: WalkthroughRepository,
) : ViewModel() {
    internal val uiState: StateFlow<WalkthroughUiState> =
        combine(settings.settings, sync.state) { settings, sync ->
            WalkthroughUiState(weightUnit = settings.units.weight, syncAccount = sync.account)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WalkthroughUiState())

    /** Weight in [unit], and distances and body measurements to match. Settings has the details. */
    internal fun chooseWeightUnit(unit: WeightUnit) {
        viewModelScope.launch {
            settings.update { current ->
                if (current.units.weight == unit) current else current.copy(units = unitsFor(unit))
            }
        }
    }

    /** Google granted access to [account]'s Drive: sync to it from now on. */
    internal fun connected(account: String) {
        viewModelScope.launch { sync.connect(account) }
    }

    /** Done: the app shell comes back as this lands. */
    internal fun finish() {
        viewModelScope.launch { walkthrough.finish() }
    }

    private fun unitsFor(weight: WeightUnit): Units =
        when (weight) {
            WeightUnit.Kilogram -> Units()
            WeightUnit.Pound -> Units(weight, DistanceUnit.Mile, LengthUnit.Inch)
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
