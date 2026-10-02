package app.liora.feature.settings.gym

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.readableWidth
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.cd_use_gym
import app.liora.feature.settings.resources.gyms_add
import app.liora.feature.settings.resources.gyms_empty_body
import app.liora.feature.settings.resources.gyms_empty_title
import app.liora.feature.settings.resources.gyms_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GymsScreen(
    viewModel: GymsViewModel,
    onBack: () -> Unit,
    onOpen: (gymId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GymsContent(uiState, onBack = onBack, onOpen = onOpen, onUse = viewModel::use, modifier = modifier)
}

/** The gyms set up: tap one to change it, or its radio button to train with its equipment. */
@Composable
private fun GymsContent(
    state: GymsUiState,
    onBack: () -> Unit,
    onOpen: (gymId: String?) -> Unit,
    onUse: (gymId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.gyms_title),
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
        floatingActionButton = {
            val label = stringResource(Res.string.gyms_add)
            ExtendedFloatingActionButton(
                onClick = { onOpen(null) },
                icon = { Icon(painterResource(LioraIcons.Add), contentDescription = null) },
                text = { Text(label) },
                // Material clears the text's semantics, which would leave the button unnamed.
                modifier = Modifier.semantics { contentDescription = label },
            )
        },
    ) { padding ->
        when {
            state.loading -> {
                Unit
            }

            state.gyms.isEmpty() -> {
                EmptyState(
                    icon = LioraIcons.Train,
                    title = stringResource(Res.string.gyms_empty_title),
                    body = stringResource(Res.string.gyms_empty_body),
                    modifier = Modifier.padding(padding).readableWidth(),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.readableWidth(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp),
                ) {
                    items(state.gyms, key = { it.id }) { gym ->
                        val name = gymName(gym)
                        val useLabel = stringResource(Res.string.cd_use_gym, name)
                        ListItem(
                            headlineContent = { Text(name) },
                            supportingContent = { Text(gymSummary(gym)) },
                            leadingContent = {
                                RadioButton(
                                    selected = gym.id == state.inUse,
                                    onClick = { onUse(gym.id) },
                                    modifier = Modifier.semantics { contentDescription = useLabel },
                                )
                            },
                            modifier = Modifier.clickable { onOpen(gym.id) },
                        )
                    }
                }
            }
        }
    }
}
