package app.liora.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.designsystem.util.workoutDisplayName
import app.liora.feature.history.resources.Res
import app.liora.feature.history.resources.date_time
import app.liora.feature.history.resources.exercise_sets
import app.liora.feature.history.resources.more_exercises
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A finished workout in the list: when, how long and how much, and the first few exercises. */
@Composable
internal fun WorkoutCard(
    item: WorkoutItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    val numbers = rememberNumberFormatter()
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { if (selected) this.selected = true },
        colors =
            CardDefaults.cardColors(
                containerColor =
                    with(MaterialTheme.colorScheme) { if (selected) secondaryContainer else surfaceContainer },
            ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = workoutDisplayName(item.name),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.records > 0) RecordsBadge(item.records, Modifier.padding(start = 8.dp))
            }
            Text(
                text = stringResource(Res.string.date_time, dates.shortDate(item.date), dates.time(item.time)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WorkoutTotals(
                duration = item.duration,
                volumeKg = item.volumeKg,
                sets = item.sets,
                records = 0,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            item.exercises.take(SHOWN_EXERCISES).forEach { exercise ->
                Text(
                    text = stringResource(Res.string.exercise_sets, numbers.format(exercise.sets), exercise.name),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val more = item.exercises.size - SHOWN_EXERCISES
            if (more > 0) {
                Text(
                    text = pluralStringResource(Res.plurals.more_exercises, more, more),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Enough to recognize the day at a glance; the rest is one tap away. */
private const val SHOWN_EXERCISES = 3
