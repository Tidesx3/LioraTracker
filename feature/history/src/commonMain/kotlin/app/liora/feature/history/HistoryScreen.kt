package app.liora.feature.history

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.feature.history.resources.Res
import app.liora.feature.history.resources.history_empty_body
import app.liora.feature.history.resources.history_empty_title
import app.liora.feature.history.resources.history_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HistoryScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { LioraTopAppBar(title = stringResource(Res.string.history_title)) },
    ) { padding ->
        EmptyState(
            icon = LioraIcons.History,
            title = stringResource(Res.string.history_empty_title),
            body = stringResource(Res.string.history_empty_body),
            modifier = Modifier.padding(padding),
        )
    }
}
