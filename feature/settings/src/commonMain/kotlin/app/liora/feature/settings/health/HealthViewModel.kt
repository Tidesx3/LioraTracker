package app.liora.feature.settings.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.health.HealthAccess
import app.liora.core.data.health.HealthRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class HealthViewModel(
    private val health: HealthRepository,
) : ViewModel() {
    /** What Health Connect lets Liora do; null until it's been asked. */
    val access: StateFlow<HealthAccess?> = health.access

    /** Asks Health Connect again: its settings may have changed while Liora was away. */
    fun refresh() {
        viewModelScope.launch { health.refresh() }
    }

    /** Back from Health Connect's permission screen: trade with it right away, past workouts too. */
    fun permissionsAnswered() {
        viewModelScope.launch { health.exchange() }
    }

    fun turnOff() {
        viewModelScope.launch { health.turnOff() }
    }
}
