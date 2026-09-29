package app.liora.feature.exercises.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.liora.core.model.Exercise
import app.liora.core.ui.customExerciseBadge
import app.liora.core.ui.label
import app.liora.feature.exercises.resources.Res
import app.liora.feature.exercises.resources.exercises_hidden_badge
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource

/**
 * One exercise in a list: thumbnail, name, target muscles and equipment. Also used by pickers.
 * [selected] marks the exercise open in the detail pane beside the list.
 */
@Composable
fun ExerciseRow(
    exercise: Exercise,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (selected) {
                        Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .semantics { this.selected = true }
                    } else {
                        Modifier
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ExerciseThumbnail(exercise = exercise, size = 52.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = exerciseSubtitle(exercise),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            exercise.settings.archived -> Tag(stringResource(Res.string.exercises_hidden_badge))
            exercise.isCustom -> Tag(stringResource(customExerciseBadge))
        }
        trailing()
    }
}

/** "Chest, Triceps · Barbell". */
@Composable
fun exerciseSubtitle(exercise: Exercise): String {
    val muscles = exercise.primaryMuscles.take(2).map { stringResource(it.label) }
    val equipment = stringResource(exercise.equipment.label)
    return if (muscles.isEmpty()) equipment else muscles.joinToString(", ") + " · " + equipment
}

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

@Composable
private fun Tag(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
