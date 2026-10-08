package app.liora.feature.settings.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.sync.SyncRepository
import app.liora.core.data.sync.SyncState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Whether sync is on, and how it's going; null until it's known. */
internal data class SyncUiState(
    val sync: SyncState? = null,
)

internal class SyncViewModel(
    private val repository: SyncRepository,
    private val clock: Clock,
) : ViewModel() {
    val uiState: StateFlow<SyncUiState> =
        repository.state
            .map { SyncUiState(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SyncUiState())

    /** Today, so a sync from today says so. */
    fun today(): LocalDate = clock.todayIn(TimeZone.currentSystemDefault())

    /** Google granted access to [account]'s Drive: sync to it from now on. */
    fun connected(account: String) {
        viewModelScope.launch { repository.connect(account) }
    }

    fun syncNow() {
        viewModelScope.launch { repository.syncNow() }
    }

    fun disconnect() {
        viewModelScope.launch { repository.disconnect() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
