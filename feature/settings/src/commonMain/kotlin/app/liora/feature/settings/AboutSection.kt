package app.liora.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liora.core.data.update.UpdateRepository
import app.liora.core.designsystem.component.LioraCardDefaults
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.domain.update.UpdateState
import app.liora.core.domain.update.release
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.updates_auto_check
import app.liora.feature.settings.resources.updates_auto_check_body
import app.liora.feature.settings.resources.updates_checking
import app.liora.feature.settings.resources.updates_debug
import app.liora.feature.settings.resources.updates_never_checked
import app.liora.feature.settings.resources.updates_newer
import app.liora.feature.settings.resources.updates_unreachable
import app.liora.feature.settings.resources.updates_up_to_date
import app.liora.feature.settings.resources.updates_version
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** About Liora's updates: the version installed, where updates stand, and whether Liora looks by itself. */
internal data class UpdatesUiState(
    val versionName: String,
    val update: UpdateState = UpdateState.Off,
    val autoCheck: Boolean = true,
)

/** Updates from GitHub, as Settings › About Liora shows them (PLAN 4e). */
internal class UpdatesViewModel(
    private val updates: UpdateRepository,
) : ViewModel() {
    val uiState: StateFlow<UpdatesUiState> =
        combine(updates.state, updates.autoCheck) { update, autoCheck ->
            UpdatesUiState(updates.versionName, update, autoCheck)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdatesUiState(updates.versionName))

    fun check() {
        viewModelScope.launch { updates.check() }
    }

    fun update() {
        viewModelScope.launch { updates.update() }
    }

    fun install() {
        viewModelScope.launch { updates.install() }
    }

    fun setAutoCheck(on: Boolean) {
        viewModelScope.launch { updates.setAutoCheck(on) }
    }
}

/** What Settings does with updates. */
internal class UpdatesActions(
    val onCheck: () -> Unit,
    val onUpdate: () -> Unit,
    val onInstall: () -> Unit,
    val onAutoCheck: (Boolean) -> Unit,
)

/** The version installed and where updates stand; tapping it looks for a newer one. */
@Composable
internal fun VersionRow(
    state: UpdatesUiState,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val update = state.update
    val canCheck = update !is UpdateState.Off && update !is UpdateState.Checking && update.release == null
    ListItem(
        headlineContent = { Text(stringResource(Res.string.updates_version, state.versionName)) },
        supportingContent = { Text(updateStatus(update)) },
        colors = LioraCardDefaults.listItemColors(),
        modifier = if (canCheck) modifier.clickable(onClick = onCheck) else modifier,
    )
}

/** Whether Liora looks for updates by itself, once a day; not in a debug build, which never does. */
@Composable
internal fun AutoCheckRow(
    state: UpdatesUiState,
    onAutoCheck: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.update is UpdateState.Off) return
    ListItem(
        headlineContent = { Text(stringResource(Res.string.updates_auto_check)) },
        supportingContent = { Text(stringResource(Res.string.updates_auto_check_body)) },
        trailingContent = { Switch(checked = state.autoCheck, onCheckedChange = onAutoCheck) },
        colors = LioraCardDefaults.listItemColors(),
        modifier = modifier.clickable { onAutoCheck(!state.autoCheck) },
    )
}

@Composable
private fun updateStatus(update: UpdateState): String {
    val release = update.release
    return when {
        release != null -> {
            stringResource(Res.string.updates_newer, release.version.toString())
        }

        update is UpdateState.Off -> {
            stringResource(Res.string.updates_debug)
        }

        update is UpdateState.Checking -> {
            stringResource(Res.string.updates_checking)
        }

        update is UpdateState.Unreachable -> {
            stringResource(Res.string.updates_unreachable)
        }

        else -> {
            val checkedAt =
                (update as? UpdateState.UpToDate)?.checkedAt ?: return stringResource(Res.string.updates_never_checked)
            val dates = rememberDateFormatter()
            val local = checkedAt.toLocalDateTime(TimeZone.currentSystemDefault())
            stringResource(Res.string.updates_up_to_date, dates.dayAndMonth(local.date), dates.time(local.time))
        }
    }
}
