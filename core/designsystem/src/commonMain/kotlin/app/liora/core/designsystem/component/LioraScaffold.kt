package app.liora.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.liora.core.designsystem.glass.LocalGlassBackdrop
import app.liora.core.designsystem.glass.aurora
import app.liora.core.designsystem.glass.glassSource
import app.liora.core.designsystem.glass.rememberGlassBackdrop
import app.liora.core.designsystem.theme.LioraTheme

/**
 * A screen: Material's [Scaffold], with Liquid Glass when it's on ([LioraTheme.glass]). Then the
 * screen sits on the aurora, and its content is the backdrop its bars bend: the top bar frosts what
 * scrolls beneath it. Either way it leaves room at the bottom for the tabs floating over it
 * ([LocalFloatingChromeInsets]), so lists scroll on beneath them and end above them.
 */
@Composable
fun LioraScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    val floating = LocalFloatingChromeInsets.current
    val insets = contentWindowInsets.add(floating)
    val glass = LioraTheme.glass
    if (!glass.enabled) {
        Scaffold(
            modifier = modifier,
            topBar = topBar,
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            floatingActionButtonPosition = floatingActionButtonPosition,
            containerColor = containerColor,
            contentColor = contentColor,
            contentWindowInsets = insets,
            content = content,
        )
        return
    }
    val backdrop = rememberGlassBackdrop()
    // The screen's own color, if it asks for one; the aurora otherwise.
    val background =
        if (containerColor == MaterialTheme.colorScheme.background) {
            Modifier.aurora(glass)
        } else {
            Modifier.background(containerColor)
        }
    Scaffold(
        modifier = modifier,
        topBar = { CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = topBar) },
        bottomBar = { CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = bottomBar) },
        snackbarHost = { CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = snackbarHost) },
        floatingActionButton = {
            // Material places it by the window's edge, not the content's insets: lift it over the tabs.
            Box(Modifier.padding(bottom = floating.asPaddingValues().calculateBottomPadding())) {
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = floatingActionButton)
            }
        },
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = Color.Transparent,
        contentColor = contentColor,
        contentWindowInsets = insets,
    ) { padding ->
        Box(Modifier.fillMaxSize().glassSource(backdrop).then(background)) {
            // Glass inside the content can't bend the content it's part of.
            CompositionLocalProvider(LocalGlassBackdrop provides null) { content(padding) }
        }
    }
}

/**
 * The room the navigation floating over the bottom of the screen takes (the tabs and the workout in
 * progress, with Liquid Glass), which [LioraScaffold] adds to its content's insets. The app shell
 * provides it; it's zero where nothing floats there.
 */
val LocalFloatingChromeInsets = compositionLocalOf { WindowInsets(0) }
