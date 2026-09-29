package app.liora.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.settings_empty_body
import app.liora.feature.settings.resources.settings_empty_title
import app.liora.feature.settings.resources.settings_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.settings_title),
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        EmptyState(
            icon = LioraIcons.Settings,
            title = stringResource(Res.string.settings_empty_title),
            body = stringResource(Res.string.settings_empty_body),
            modifier = Modifier.padding(padding),
        )
    }
}
