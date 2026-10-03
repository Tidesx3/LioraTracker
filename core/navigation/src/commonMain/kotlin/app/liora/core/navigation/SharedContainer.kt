package app.liora.core.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/** The shared-transition scope around the app's NavDisplay; null where screens don't animate between each other. */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Keys that pair a list item with the screen it opens, the same in every feature. */
object SharedKeys {
    fun workout(id: String): Any = "workout:$id"

    fun routine(id: String): Any = "routine:$id"

    fun exercise(id: String): Any = "exercise:$id"
}

/**
 * A container transform: a list item grows into the screen it opens, and that screen shrinks back
 * into it on the way back. Give the item and the opened screen's root the same [key]. Only where one
 * screen takes another's place; beside a list, the detail pane simply changes. With the system's
 * animations off it's a plain cut, like every other transition.
 */
@Composable
fun rememberSharedContainer(
    key: Any,
    shape: Shape = MaterialTheme.shapes.medium,
): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return Modifier
    if (LocalPaneRole.current != PaneRole.Single) return Modifier
    val screen = LocalNavAnimatedContentScope.current
    return with(shared) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = screen,
            enter = fadeIn(tween(durationMillis = FADE_MILLIS, delayMillis = FADE_DELAY_MILLIS)),
            exit = fadeOut(tween(durationMillis = FADE_DELAY_MILLIS)),
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/** The item's content fades out quickly as it starts to grow, and the screen's fades in over the rest. */
private const val FADE_DELAY_MILLIS = 90
private const val FADE_MILLIS = 210
