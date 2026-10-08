package app.liora.feature.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.data.sync.SyncProblem
import app.liora.core.data.sync.SyncState
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraCard
import app.liora.core.designsystem.component.LioraCardDefaults
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.illustration.Illustration
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.ui.CloudConnection
import app.liora.core.ui.LocalCloudConsent
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.dialog_cancel
import app.liora.feature.settings.resources.sync_account
import app.liora.feature.settings.resources.sync_all_in
import app.liora.feature.settings.resources.sync_connect
import app.liora.feature.settings.resources.sync_connect_failed
import app.liora.feature.settings.resources.sync_disconnect
import app.liora.feature.settings.resources.sync_disconnect_body
import app.liora.feature.settings.resources.sync_disconnect_confirm
import app.liora.feature.settings.resources.sync_disconnect_question
import app.liora.feature.settings.resources.sync_last
import app.liora.feature.settings.resources.sync_now
import app.liora.feature.settings.resources.sync_off_body
import app.liora.feature.settings.resources.sync_off_title
import app.liora.feature.settings.resources.sync_row_running
import app.liora.feature.settings.resources.sync_sign_in
import app.liora.feature.settings.resources.sync_space
import app.liora.feature.settings.resources.sync_title
import app.liora.feature.settings.resources.sync_waiting
import app.liora.feature.settings.resources.sync_waiting_body
import app.liora.feature.settings.resources.sync_when
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SyncScreen(
    viewModel: SyncViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val consent = LocalCloudConsent.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var connecting by remember { mutableStateOf(false) }
    SyncContent(
        state = uiState,
        today = viewModel.today(),
        connecting = connecting,
        snackbar = snackbar,
        onBack = onBack,
        onConnect = {
            scope.launch {
                connecting = true
                val connection = consent.connect()
                connecting = false
                when (connection) {
                    is CloudConnection.Connected -> viewModel.connected(connection.account)
                    CloudConnection.Declined -> Unit
                    CloudConnection.Failed -> snackbar.showSnackbar(getString(Res.string.sync_connect_failed))
                }
            }
        },
        onSyncNow = viewModel::syncNow,
        onDisconnect = viewModel::disconnect,
        modifier = modifier,
    )
}

/** Sync off: what it does and a button to connect. On: where it syncs, how it's going, and what to do. */
@Composable
private fun SyncContent(
    state: SyncUiState,
    today: LocalDate,
    connecting: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDisconnect by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.sync_title),
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val sync = state.sync ?: return@Scaffold
        val account = sync.account
        if (account == null) {
            EmptyState(
                illustration = Illustration.Sync,
                title = stringResource(Res.string.sync_off_title),
                body = stringResource(Res.string.sync_off_body),
                modifier = Modifier.padding(padding).readableWidth(),
                action = {
                    Button(onClick = onConnect, enabled = !connecting) {
                        Text(stringResource(Res.string.sync_connect))
                    }
                },
            )
        } else {
            Connected(
                account = account,
                sync = sync,
                today = today,
                connecting = connecting,
                onConnect = onConnect,
                onSyncNow = onSyncNow,
                onDisconnect = { confirmingDisconnect = true },
                modifier =
                    Modifier
                        .padding(padding)
                        .readableWidth()
                        .verticalScroll(rememberScrollState()),
            )
        }
    }
    if (confirmingDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmingDisconnect = false },
            title = { Text(stringResource(Res.string.sync_disconnect_question)) },
            text = { Text(stringResource(Res.string.sync_disconnect_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDisconnect = false
                        onDisconnect()
                    },
                ) { Text(stringResource(Res.string.sync_disconnect_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDisconnect = false }) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun Connected(
    account: String,
    sync: SyncState,
    today: LocalDate,
    connecting: Boolean,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LioraCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.sync_account)) },
                    supportingContent = { Text(account) },
                    colors = LioraCardDefaults.listItemColors(),
                )
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.sync_last)) },
                    supportingContent = {
                        Text(
                            if (sync.running) {
                                stringResource(
                                    Res.string.sync_row_running,
                                )
                            } else {
                                lastSyncedText(sync, today)
                            },
                        )
                    },
                    trailingContent =
                        if (sync.running) {
                            { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                        } else {
                            null
                        },
                    colors = LioraCardDefaults.listItemColors(),
                )
                Pending(sync.pending)
                sync.usedBytes?.let { bytes ->
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.sync_space)) },
                        supportingContent = { Text(storageText(bytes)) },
                        colors = LioraCardDefaults.listItemColors(),
                    )
                }
            }
        }
        sync.problem?.let { problem ->
            Problem(problem, connecting = connecting, onSignIn = onConnect)
        }
        Text(
            text = stringResource(Res.string.sync_when),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSyncNow, enabled = !sync.running) { Text(stringResource(Res.string.sync_now)) }
            TextButton(onClick = onDisconnect) { Text(stringResource(Res.string.sync_disconnect)) }
        }
    }
}

/** How many changes here haven't gone up yet, or that everything has. */
@Composable
private fun Pending(
    pending: Int,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                if (pending == 0) {
                    stringResource(Res.string.sync_all_in)
                } else {
                    pluralStringResource(Res.plurals.sync_waiting, pending, pending)
                },
            )
        },
        supportingContent =
            if (pending == 0) null else ({ Text(pluralStringResource(Res.plurals.sync_waiting_body, pending)) }),
        colors = LioraCardDefaults.listItemColors(),
        modifier = modifier,
    )
}

/** What went wrong, and for signing in again the button that does it. A busy Drive passes on its own. */
@Composable
private fun Problem(
    problem: SyncProblem,
    connecting: Boolean,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LioraCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(problem.text()),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (problem == SyncProblem.Unreachable) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
            if (problem == SyncProblem.SignInNeeded) {
                Button(onClick = onSignIn, enabled = !connecting) { Text(stringResource(Res.string.sync_sign_in)) }
            }
        }
    }
}
