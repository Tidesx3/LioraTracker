package app.liora.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.LioraCard
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.domain.update.ReleaseNotes
import app.liora.core.domain.update.UpdateProblem
import app.liora.core.domain.update.UpdateState
import app.liora.core.domain.update.release
import app.liora.core.ui.resources.Res
import app.liora.core.ui.resources.update_action
import app.liora.core.ui.resources.update_after_workout
import app.liora.core.ui.resources.update_downloading
import app.liora.core.ui.resources.update_failed_network
import app.liora.core.ui.resources.update_failed_not_genuine
import app.liora.core.ui.resources.update_failed_not_installed
import app.liora.core.ui.resources.update_install
import app.liora.core.ui.resources.update_installing
import app.liora.core.ui.resources.update_less
import app.liora.core.ui.resources.update_more
import app.liora.core.ui.resources.update_not_now
import app.liora.core.ui.resources.update_ready
import app.liora.core.ui.resources.update_retry
import app.liora.core.ui.resources.update_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A newer Liora and getting it, on a card of its own, as the Train tab shows it: "Liora 0.5.0 is here",
 * then [UpdateDetails] with Not now ([onDismiss]). Nothing for a state without a newer release.
 */
@Composable
fun UpdateCard(
    state: UpdateState,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val release = state.release ?: return
    LioraCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(LioraIcons.Download),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.update_title, release.version.toString()),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            UpdateDetails(state, onUpdate, onInstall, onDismiss = onDismiss)
        }
    }
}

/**
 * What's new in a newer Liora, Update, then the download, the install, or why it didn't go through:
 * without a card or a title, for a place that already says which version is out, such as Settings ›
 * About Liora. Nothing for a state without a newer release.
 */
@Composable
fun UpdateDetails(
    state: UpdateState,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val release = state.release ?: return
    val language = currentLanguage()
    val notes = remember(release.notes, language) { ReleaseNotes.forLanguage(release.notes, language) }
    Column(modifier) {
        if (notes.isNotEmpty()) {
            Notes(notes)
            Spacer(Modifier.height(8.dp))
        }
        Progress(state)
        Actions(state, onUpdate, onInstall, onDismiss)
    }
}

/** What's new: its first few lines (whole items, never cut off mid-line) until asked for all. */
@Composable
private fun Notes(notes: String) {
    var expanded by rememberSaveable(notes) { mutableStateOf(false) }
    val lines = remember(notes) { notes.lines().filter { it.isNotBlank() } }
    val more = lines.size > COLLAPSED_LINES
    Column {
        Text(
            if (expanded || !more) notes else lines.take(COLLAPSED_LINES).joinToString("\n"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (more) {
            // Its label lines up with the notes; the button's own padding would indent it.
            TextButton(
                onClick = { expanded = !expanded },
                modifier =
                    Modifier.offset(
                        x = -ButtonDefaults.TextButtonContentPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    ),
            ) {
                Text(stringResource(if (expanded) Res.string.update_less else Res.string.update_more))
            }
        }
    }
}

/** Where the download and install stand, or why the update waits or didn't go through. */
@Composable
private fun Progress(state: UpdateState) {
    val waits = Res.string.update_after_workout
    val line =
        when (state) {
            is UpdateState.Available -> if (state.duringWorkout) waits else null
            is UpdateState.Downloading -> Res.string.update_downloading
            is UpdateState.ReadyToInstall -> if (state.duringWorkout) waits else Res.string.update_ready
            is UpdateState.Installing -> Res.string.update_installing
            is UpdateState.Failed -> state.problem.text()
            else -> null
        }
    val fraction = (state as? UpdateState.Downloading)?.fraction
    val busy = state is UpdateState.Downloading || state is UpdateState.Installing
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            fraction != null -> LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            busy -> LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        line?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state is UpdateState.Failed) colors.error else colors.onSurface,
            )
        }
    }
}

@Composable
private fun Actions(
    state: UpdateState,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    val (label, action, enabled) =
        when (state) {
            is UpdateState.Available -> Triple(Res.string.update_action, onUpdate, !state.duringWorkout)
            is UpdateState.ReadyToInstall -> Triple(Res.string.update_install, onInstall, !state.duringWorkout)
            is UpdateState.Failed -> Triple(Res.string.update_retry, onUpdate, true)
            else -> return
        }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onDismiss != null && state is UpdateState.Available) {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.update_not_now)) }
        }
        Button(onClick = action, enabled = enabled) { Text(stringResource(label)) }
    }
}

private fun UpdateProblem.text() =
    when (this) {
        UpdateProblem.Network -> Res.string.update_failed_network
        UpdateProblem.NotGenuine -> Res.string.update_failed_not_genuine
        UpdateProblem.NotInstalled -> Res.string.update_failed_not_installed
    }

private const val COLLAPSED_LINES = 3
