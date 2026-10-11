package app.liora.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import app.liora.core.designsystem.glass.LocalGlassBackdrop
import app.liora.core.designsystem.glass.glassEdge
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.resources.Res
import app.liora.core.designsystem.resources.cd_back
import app.liora.core.designsystem.theme.LioraTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Top bar with a bold headline title, used by every screen. With Liquid Glass it has no color of its
 * own: what scrolls beneath it frosts over and fades out at its edge ([glassEdge]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LioraTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val glass = LioraTheme.glass
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = if (glass.enabled) modifier.glassEdge(LocalGlassBackdrop.current, glass) else modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors =
            if (glass.enabled) {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                )
            } else {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            },
    )
}

@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LioraIconButton(
        icon = LioraIcons.Back,
        contentDescription = stringResource(Res.string.cd_back),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun LioraIconButton(
    icon: DrawableResource,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painter = painterResource(icon), contentDescription = contentDescription)
    }
}
