package app.liora.feature.body

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.feature.body.resources.Res
import app.liora.feature.body.resources.body_empty_body
import app.liora.feature.body.resources.body_empty_title
import app.liora.feature.body.resources.body_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BodyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.body_title),
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        EmptyState(
            icon = LioraIcons.Body,
            title = stringResource(Res.string.body_empty_title),
            body = stringResource(Res.string.body_empty_body),
            modifier = Modifier.padding(padding),
        )
    }
}
