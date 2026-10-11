package app.liora.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.glass.GlassDefaults
import app.liora.core.designsystem.glass.LocalGlassBackdrop
import app.liora.core.designsystem.glass.glass
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
 * own: what scrolls beneath it frosts over and fades out at its edge ([glassEdge]), the navigation icon
 * sits on a circle of glass and the [actions] share a capsule of it. [primaryAction], the screen's main
 * one (the logger's Finish), comes last, outside the glass.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LioraTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    primaryAction: @Composable () -> Unit = {},
) {
    val glass = LioraTheme.glass
    val backdrop = LocalGlassBackdrop.current
    val buttons = GlassDefaults.bar()
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
        navigationIcon = {
            // With glass, a little room on either side, so the circle doesn't crowd the edge or the title.
            Box(
                if (glass.enabled) {
                    Modifier
                        .padding(
                            start = 4.dp,
                            end = 8.dp,
                        ).glass(backdrop, CircleShape, buttons)
                } else {
                    Modifier
                },
            ) { navigationIcon() }
        },
        actions = {
            Row(
                modifier =
                    if (glass.enabled) {
                        Modifier
                            .padding(
                                end = 4.dp,
                            ).glass(backdrop, CircleShape, buttons)
                    } else {
                        Modifier
                    },
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
            primaryAction()
        },
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
