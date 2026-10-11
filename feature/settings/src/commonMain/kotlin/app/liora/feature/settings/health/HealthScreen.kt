package app.liora.feature.settings.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.data.health.HealthAccess
import app.liora.core.data.health.HealthAvailability
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraAlertDialog
import app.liora.core.designsystem.component.LioraButton
import app.liora.core.designsystem.component.LioraCard
import app.liora.core.designsystem.component.LioraCardDefaults
import app.liora.core.designsystem.component.LioraScaffold
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.illustration.Illustration
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.ui.LocalHealthConnectScreens
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.dialog_cancel
import app.liora.feature.settings.resources.health_allow_rest
import app.liora.feature.settings.resources.health_body
import app.liora.feature.settings.resources.health_body_on
import app.liora.feature.settings.resources.health_get
import app.liora.feature.settings.resources.health_missing_body
import app.liora.feature.settings.resources.health_missing_title
import app.liora.feature.settings.resources.health_not_allowed
import app.liora.feature.settings.resources.health_off_body
import app.liora.feature.settings.resources.health_off_title
import app.liora.feature.settings.resources.health_row_missing
import app.liora.feature.settings.resources.health_row_off
import app.liora.feature.settings.resources.health_row_on
import app.liora.feature.settings.resources.health_set_up
import app.liora.feature.settings.resources.health_settings
import app.liora.feature.settings.resources.health_title
import app.liora.feature.settings.resources.health_turn_off
import app.liora.feature.settings.resources.health_turn_off_body
import app.liora.feature.settings.resources.health_turn_off_question
import app.liora.feature.settings.resources.health_update
import app.liora.feature.settings.resources.health_update_body
import app.liora.feature.settings.resources.health_update_title
import app.liora.feature.settings.resources.health_workouts
import app.liora.feature.settings.resources.health_workouts_on
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HealthScreen(
    viewModel: HealthViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val screens = LocalHealthConnectScreens.current
    val scope = rememberCoroutineScope()
    // Permissions can change in Health Connect's own settings while Liora is in the background.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    HealthContent(
        access = access,
        onBack = onBack,
        onSetUp = {
            scope.launch {
                screens.requestPermissions()
                viewModel.permissionsAnswered()
            }
        },
        onInstall = screens::install,
        onOpenSettings = screens::openSettings,
        onTurnOff = viewModel::turnOff,
        modifier = modifier,
    )
}

/** Not on the phone, not set up yet, or set up: what goes out, what comes in, and the way to change it. */
@Composable
private fun HealthContent(
    access: HealthAccess?,
    onBack: () -> Unit,
    onSetUp: () -> Unit,
    onInstall: () -> Unit,
    onOpenSettings: () -> Unit,
    onTurnOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingTurnOff by rememberSaveable { mutableStateOf(false) }
    LioraScaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.health_title),
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        val emptyModifier = Modifier.padding(padding).readableWidth()
        when {
            access == null -> {
                Unit
            }

            access.availability == HealthAvailability.NotInstalled -> {
                Invitation(
                    title = Res.string.health_missing_title,
                    body = Res.string.health_missing_body,
                    action = Res.string.health_get,
                    onAction = onInstall,
                    modifier = emptyModifier,
                )
            }

            access.availability == HealthAvailability.UpdateNeeded -> {
                Invitation(
                    title = Res.string.health_update_title,
                    body = Res.string.health_update_body,
                    action = Res.string.health_update,
                    onAction = onInstall,
                    modifier = emptyModifier,
                )
            }

            !access.connected -> {
                Invitation(
                    title = Res.string.health_off_title,
                    body = Res.string.health_off_body,
                    action = Res.string.health_set_up,
                    onAction = onSetUp,
                    modifier = emptyModifier,
                )
            }

            else -> {
                Connected(
                    access = access,
                    onOpenSettings = onOpenSettings,
                    onTurnOff = { confirmingTurnOff = true },
                    modifier =
                        Modifier
                            .padding(padding)
                            .readableWidth()
                            .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
    if (confirmingTurnOff) {
        LioraAlertDialog(
            onDismissRequest = { confirmingTurnOff = false },
            title = { Text(stringResource(Res.string.health_turn_off_question)) },
            text = { Text(stringResource(Res.string.health_turn_off_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingTurnOff = false
                        onTurnOff()
                    },
                ) { Text(stringResource(Res.string.health_turn_off)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingTurnOff = false }) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun Invitation(
    title: StringResource,
    body: StringResource,
    action: StringResource,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = Illustration.Body,
        title = stringResource(title),
        body = stringResource(body),
        modifier = modifier,
        action = { LioraButton(onClick = onAction) { Text(stringResource(action)) } },
    )
}

@Composable
private fun Connected(
    access: HealthAccess,
    onOpenSettings: () -> Unit,
    onTurnOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LioraCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                Permission(Res.string.health_workouts, Res.string.health_workouts_on, access.writeWorkouts)
                Permission(Res.string.health_body, Res.string.health_body_on, access.readBody)
            }
        }
        if (!access.writeWorkouts || !access.readBody) {
            Text(
                text = stringResource(Res.string.health_allow_rest),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LioraButton(onClick = onOpenSettings) { Text(stringResource(Res.string.health_settings)) }
            TextButton(onClick = onTurnOff) { Text(stringResource(Res.string.health_turn_off)) }
        }
    }
}

/** What Liora does with one kind of data, or that it isn't allowed to. */
@Composable
private fun Permission(
    title: StringResource,
    allowed: StringResource,
    granted: Boolean,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            Text(
                stringResource(if (granted) allowed else Res.string.health_not_allowed),
                color = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        },
        colors = LioraCardDefaults.listItemColors(),
        modifier = modifier,
    )
}

/** Health Connect in a few words, for its row in Settings. */
@Composable
internal fun healthStatusText(access: HealthAccess): String =
    stringResource(
        when {
            access.availability != HealthAvailability.Available -> Res.string.health_row_missing
            access.connected -> Res.string.health_row_on
            else -> Res.string.health_row_off
        },
    )
