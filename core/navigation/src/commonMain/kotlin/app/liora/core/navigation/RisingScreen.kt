package app.liora.core.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.ui.NavDisplay

/**
 * Entry metadata for a screen that rises over the app like a sheet, such as the logger, which opens from
 * the workout bar at the bottom and is minimized with a downward arrow: it rises as it opens and sinks as
 * it closes, and with predictive back it sinks under the finger. Screens opened on top of it, and back to
 * it, move like any other.
 */
fun risingScreen(): Map<String, Any> =
    NavDisplay.transitionSpec { rise() } +
        NavDisplay.popTransitionSpec { sink() } +
        NavDisplay.predictivePopTransitionSpec { pulledDown() }

private fun rise(): ContentTransform =
    (
        slideInVertically(tween(RISE_MILLIS, easing = FastOutSlowInEasing)) { height -> height / TRAVEL_FRACTION } +
            fadeIn(tween(RISE_MILLIS))
    ) togetherWith fadeOut(tween(FADE_MILLIS))

private fun sink(): ContentTransform =
    fadeIn(tween(RISE_MILLIS)) togetherWith (
        slideOutVertically(tween(RISE_MILLIS, easing = FastOutSlowInEasing)) { height -> height / TRAVEL_FRACTION } +
            fadeOut(tween(RISE_MILLIS))
    )

/** Follows the gesture down and a little smaller, fading only near the end, so it can be pulled back up. */
private fun pulledDown(): ContentTransform =
    fadeIn(tween(RISE_MILLIS), initialAlpha = BEHIND_ALPHA) togetherWith (
        slideOutVertically(tween(RISE_MILLIS)) { height -> height / TRAVEL_FRACTION } +
            scaleOut(tween(RISE_MILLIS), targetScale = PULLED_SCALE) +
            fadeOut(
                keyframes {
                    durationMillis = RISE_MILLIS
                    1f at RISE_MILLIS * FADE_FROM / PERCENT using LinearEasing
                },
            )
    )

private const val RISE_MILLIS = 300
private const val FADE_MILLIS = 150
private const val TRAVEL_FRACTION = 5
private const val BEHIND_ALPHA = 0.5f
private const val PULLED_SCALE = 0.94f
private const val FADE_FROM = 70
private const val PERCENT = 100
