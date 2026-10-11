package app.liora.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    // The app shell has consumed the room the floating chrome takes, so as insets Material would leave it
    // out again: it goes into the content's padding, and under the floating button, here instead.
    val chrome = LocalFloatingChromeInsets.current.asPaddingValues().calculateBottomPadding()
    val glass = LioraTheme.glass
    // What the bars bend, with glass: the content, on the aurora unless the screen asks for its own color.
    val backdrop = if (glass.enabled) rememberGlassBackdrop() else null
    val background =
        when {
            backdrop == null -> {
                Modifier
            }

            containerColor == MaterialTheme.colorScheme.background -> {
                Modifier
                    .fillMaxSize()
                    .glassSource(
                        backdrop,
                    ).aurora(glass)
            }

            else -> {
                Modifier.fillMaxSize().glassSource(backdrop).background(containerColor)
            }
        }
    Scaffold(
        modifier = modifier,
        topBar = { CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = topBar) },
        bottomBar = { CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = bottomBar) },
        snackbarHost = { CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = snackbarHost) },
        floatingActionButton = {
            Box(Modifier.padding(bottom = chrome)) {
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = floatingActionButton)
            }
        },
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = if (glass.enabled) Color.Transparent else containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
    ) { padding ->
        Box(background) {
            // Glass inside the content can't bend the content it's part of.
            CompositionLocalProvider(LocalGlassBackdrop provides null) { content(padding.plusBottom(chrome)) }
        }
    }
}

@Composable
private fun PaddingValues.plusBottom(extra: Dp): PaddingValues {
    if (extra == 0.dp) return this
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + extra,
    )
}

/**
 * The room the navigation floating over the bottom of the screen takes (the tabs and the workout in
 * progress, with Liquid Glass), which [LioraScaffold] adds to its content's padding. The app shell
 * provides it; it's zero where nothing floats there.
 */
val LocalFloatingChromeInsets = compositionLocalOf { WindowInsets(0) }
