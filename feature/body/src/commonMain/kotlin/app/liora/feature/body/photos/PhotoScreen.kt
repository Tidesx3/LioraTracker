package app.liora.feature.body.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.model.MeasurementType
import app.liora.core.model.PhotoPose
import app.liora.core.ui.StoredPhoto
import app.liora.core.ui.label
import app.liora.core.ui.measurementText
import app.liora.feature.body.DayPicker
import app.liora.feature.body.resources.Res
import app.liora.feature.body.resources.cd_photo
import app.liora.feature.body.resources.dialog_cancel
import app.liora.feature.body.resources.dialog_delete
import app.liora.feature.body.resources.photo_change_day
import app.liora.feature.body.resources.photo_delete
import app.liora.feature.body.resources.photo_delete_body
import app.liora.feature.body.resources.photo_delete_title
import app.liora.feature.body.resources.photos_compare
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Where a photo leads: back, or to compare it with an earlier one. */
internal class PhotoNavigation(
    val onBack: () -> Unit,
    val onCompare: (photoId: String) -> Unit,
)

@Composable
internal fun PhotoScreen(
    viewModel: PhotoViewModel,
    navigation: PhotoNavigation,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Close once the delete has landed; leaving earlier would cancel it.
    val currentOnBack by rememberUpdatedState(navigation.onBack)
    LaunchedEffect(uiState == PhotoUiState.Gone) { if (uiState == PhotoUiState.Gone) currentOnBack() }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var pickingDay by rememberSaveable { mutableStateOf(false) }
    val state = uiState as? PhotoUiState.Loaded

    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = state?.let { rememberDateFormatter().shortDate(it.day) }.orEmpty(),
                navigationIcon = { BackButton(onClick = navigation.onBack) },
                actions = {
                    if (state != null) {
                        LioraIconButton(LioraIcons.Compare, stringResource(Res.string.photos_compare), {
                            navigation.onCompare(state.photo.id)
                        })
                        LioraIconButton(LioraIcons.Delete, stringResource(Res.string.photo_delete), {
                            confirmingDelete = true
                        })
                    }
                },
            )
        },
    ) { padding ->
        if (state != null) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                StoredPhoto(
                    path = state.photo.path,
                    contentDescription =
                        stringResource(
                            Res.string.cd_photo,
                            rememberDateFormatter().shortDate(state.day),
                        ),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Details(
                    state = state,
                    onPickDay = { pickingDay = true },
                    onTogglePose = viewModel::togglePose,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
    if (state != null && pickingDay) {
        DayPicker(
            day = state.day,
            today = viewModel.today,
            onPick = {
                pickingDay = false
                viewModel.moveTo(it)
            },
            onDismiss = { pickingDay = false },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(Res.string.photo_delete_title)) },
            text = { Text(stringResource(Res.string.photo_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    viewModel.delete()
                }) {
                    Text(stringResource(Res.string.dialog_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(Res.string.dialog_cancel)) }
            },
        )
    }
}

/** The day (with what the scale said) and the pose, both correctable. */
@Composable
private fun Details(
    state: PhotoUiState.Loaded,
    onPickDay: () -> Unit,
    onTogglePose: (PhotoPose) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AssistChip(
                onClick = onPickDay,
                label = { Text(rememberDateFormatter().longDate(state.day)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(LioraIcons.Calendar),
                        contentDescription = stringResource(Res.string.photo_change_day),
                    )
                },
            )
            state.bodyweight?.let {
                Text(
                    text = measurementText(MeasurementType.Bodyweight, it),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoPose.entries.forEach { pose ->
                FilterChip(
                    selected = state.photo.pose == pose,
                    onClick = { onTogglePose(pose) },
                    label = { Text(stringResource(pose.label)) },
                )
            }
        }
    }
}
