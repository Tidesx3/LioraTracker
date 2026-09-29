package app.liora.feature.progress

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.feature.progress.resources.Res
import app.liora.feature.progress.resources.progress_body_subtitle
import app.liora.feature.progress.resources.progress_body_title
import app.liora.feature.progress.resources.progress_empty_body
import app.liora.feature.progress.resources.progress_empty_title
import app.liora.feature.progress.resources.progress_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProgressScreen(
    onOpenBody: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { LioraTopAppBar(title = stringResource(Res.string.progress_title)) },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                BodyCard(
                    onClick = onOpenBody,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                EmptyState(
                    icon = LioraIcons.Progress,
                    title = stringResource(Res.string.progress_empty_title),
                    body = stringResource(Res.string.progress_empty_body),
                )
            }
        }
    }
}

@Composable
private fun BodyCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(Res.string.progress_body_title)) },
            supportingContent = { Text(stringResource(Res.string.progress_body_subtitle)) },
            leadingContent = {
                Icon(
                    painter = painterResource(LioraIcons.Body),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = {
                Icon(painter = painterResource(LioraIcons.ChevronRight), contentDescription = null)
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}
