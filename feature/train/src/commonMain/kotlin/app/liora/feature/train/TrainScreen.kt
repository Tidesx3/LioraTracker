package app.liora.feature.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.feature.train.resources.Res
import app.liora.feature.train.resources.cd_settings
import app.liora.feature.train.resources.train_quick_start
import app.liora.feature.train.resources.train_quick_start_body
import app.liora.feature.train.resources.train_resume
import app.liora.feature.train.resources.train_routines
import app.liora.feature.train.resources.train_routines_empty_body
import app.liora.feature.train.resources.train_routines_empty_title
import app.liora.feature.train.resources.train_start_empty
import app.liora.feature.train.resources.train_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun TrainScreen(
    onOpenLogger: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrainViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TrainContent(
        uiState = uiState,
        onStartWorkout = {
            viewModel.startEmptyWorkout()
            onOpenLogger()
        },
        onResumeWorkout = onOpenLogger,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

@Composable
private fun TrainContent(
    uiState: TrainUiState,
    onStartWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.train_title),
                actions = {
                    LioraIconButton(
                        icon = LioraIcons.Settings,
                        contentDescription = stringResource(Res.string.cd_settings),
                        onClick = onOpenSettings,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
        ) {
            item {
                QuickStartCard(
                    hasActiveWorkout = uiState.hasActiveWorkout,
                    onStartWorkout = onStartWorkout,
                    onResumeWorkout = onResumeWorkout,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item { SectionHeader(title = stringResource(Res.string.train_routines)) }
            item {
                EmptyState(
                    icon = LioraIcons.Train,
                    title = stringResource(Res.string.train_routines_empty_title),
                    body = stringResource(Res.string.train_routines_empty_body),
                )
            }
        }
    }
}

@Composable
private fun QuickStartCard(
    hasActiveWorkout: Boolean,
    onStartWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.train_quick_start),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.train_quick_start_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = if (hasActiveWorkout) onResumeWorkout else onStartWorkout,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(
                    painter = painterResource(LioraIcons.Play),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text =
                        stringResource(
                            if (hasActiveWorkout) Res.string.train_resume else Res.string.train_start_empty,
                        ),
                )
            }
        }
    }
}
