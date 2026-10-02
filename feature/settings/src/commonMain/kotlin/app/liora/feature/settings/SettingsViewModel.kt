package app.liora.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.domain.OneRepMaxFormula
import app.liora.core.domain.Settings
import app.liora.core.domain.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Settings as they stand; null until read. Each change is saved as it's made. */
class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<Settings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setRestWorking(seconds: Int) = update { it.copy(rest = it.rest.copy(working = seconds.seconds)) }

    fun setRestWarmup(seconds: Int) = update { it.copy(rest = it.rest.copy(warmup = seconds.seconds)) }

    fun setFormula(formula: OneRepMaxFormula) = update { it.copy(oneRepMaxFormula = formula) }

    fun setStallWindow(window: Duration) = update { it.copy(stallWindow = window) }

    fun setTheme(theme: ThemeMode) = update { it.copy(theme = theme) }

    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    private fun update(change: (Settings) -> Settings) {
        viewModelScope.launch { repository.update(change) }
    }
}
