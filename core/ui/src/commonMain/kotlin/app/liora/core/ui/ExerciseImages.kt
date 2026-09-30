package app.liora.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import app.liora.core.model.Exercise
import coil3.compose.AsyncImage

/**
 * The exercise's photo, or its initial on a tonal tile when there is none (custom exercises, or no
 * network yet). The initial sits underneath, so a failed load simply leaves it visible.
 */
@Composable
fun ExerciseThumbnail(
    exercise: Exercise,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = exercise.name.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        exercise.imageUrls.firstOrNull()?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * The exercise's start position, shown whole on white (the photos are shot on white), for a glance at
 * how it's done. Nothing at all for exercises without a photo, or when it can't be loaded (no network
 * and not cached yet); a tonal frame holds its place while it loads.
 */
@Composable
fun ExercisePicture(
    exercise: Exercise,
    modifier: Modifier = Modifier,
) {
    val url = exercise.imageUrls.firstOrNull() ?: return
    var loaded by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        onSuccess = { loaded = true },
        onError = { failed = true },
        modifier =
            modifier
                .aspectRatio(PICTURE_ASPECT)
                .clip(MaterialTheme.shapes.medium)
                .background(if (loaded) Color.White else MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

/** Same frame as the exercise page's photos. */
private const val PICTURE_ASPECT = 4f / 3f
