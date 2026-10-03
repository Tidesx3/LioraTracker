package app.liora.android.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent
import app.liora.core.navigation.NavigationMove
import app.liora.core.navigation.Navigator

/**
 * How one screen follows another. Going deeper slides the new screen a little way in from the end as it
 * fades in, and back reverses it; tabs fade through, as they're side by side rather than deeper; a fold
 * or unfold, which changes the layout but not the screen, crossfades. All short, so a tap that opens a
 * screen is never kept waiting, and with the system's animations off they're cuts.
 */
internal fun <T : Any> screenTransition(
    navigator: Navigator,
): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
    {
        when {
            initialState.entries.last().contentKey == targetState.entries.last().contentKey -> crossfade()
            navigator.lastMove == NavigationMove.Tab -> fadeThrough()
            else -> sharedAxis(forward = navigator.lastMove == NavigationMove.Forward)
        }
    }

/**
 * Predictive back: as the gesture runs, the screen shrinks and drifts toward the side being swiped,
 * the one underneath coming up behind it; it fades only near the end, so it can be pulled back.
 */
internal fun <T : Any> predictiveBack(): AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform =
    { edge ->
        val toward = if (edge == NavigationEvent.EDGE_RIGHT) -1 else 1
        ContentTransform(
            targetContentEnter =
                fadeIn(tween(PREDICTIVE_MILLIS), initialAlpha = BEHIND_ALPHA) +
                    scaleIn(tween(PREDICTIVE_MILLIS), initialScale = BEHIND_SCALE),
            initialContentExit =
                scaleOut(tween(PREDICTIVE_MILLIS), targetScale = PULLED_SCALE) +
                    slideOutHorizontally(tween(PREDICTIVE_MILLIS)) { width -> toward * width / PULL_FRACTION } +
                    fadeOut(
                        keyframes {
                            durationMillis = PREDICTIVE_MILLIS
                            1f at PREDICTIVE_MILLIS * FADE_FROM / PERCENT using LinearEasing
                        },
                    ),
        )
    }

private fun sharedAxis(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return (
        slideInHorizontally(tween(AXIS_MILLIS, easing = FastOutSlowInEasing)) { width ->
            direction * width / SLIDE_FRACTION
        } + fadeIn(tween(AXIS_MILLIS - OUT_MILLIS, delayMillis = OUT_MILLIS))
    ) togetherWith (
        slideOutHorizontally(tween(AXIS_MILLIS, easing = FastOutSlowInEasing)) { width ->
            -direction * width / SLIDE_FRACTION
        } + fadeOut(tween(OUT_MILLIS))
    )
}

private fun fadeThrough(): ContentTransform =
    (
        fadeIn(tween(AXIS_MILLIS - OUT_MILLIS, delayMillis = OUT_MILLIS)) +
            scaleIn(tween(AXIS_MILLIS - OUT_MILLIS, delayMillis = OUT_MILLIS), initialScale = THROUGH_SCALE)
    ) togetherWith fadeOut(tween(OUT_MILLIS))

private fun crossfade(): ContentTransform =
    fadeIn(tween(CROSSFADE_MILLIS)) togetherWith fadeOut(tween(CROSSFADE_MILLIS))

/** Material's shared axis and fade through: the old screen fades out over the first stretch. */
private const val AXIS_MILLIS = 300
private const val OUT_MILLIS = 90
private const val SLIDE_FRACTION = 10
private const val THROUGH_SCALE = 0.94f
private const val CROSSFADE_MILLIS = 200

private const val PREDICTIVE_MILLIS = 300
private const val PULLED_SCALE = 0.88f
private const val PULL_FRACTION = 12
private const val BEHIND_SCALE = 0.96f
private const val BEHIND_ALPHA = 0.5f
private const val FADE_FROM = 70
private const val PERCENT = 100
