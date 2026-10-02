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
import app.liora.core.designsystem.component.LioraCard
import app.liora.core.designsystem.component.LioraCardDefaults
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.designsystem.util.workoutDisplayName
import app.liora.feature.history.resources.Res
import app.liora.feature.history.resources.date_time
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A finished workout in the list: its name, when, and how long, how much and how many sets. */
@Composable
internal fun WorkoutCard(
    item: WorkoutItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    LioraCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { if (selected) this.selected = true },
        containerColor =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                LioraCardDefaults
                    .containerColor()
            },
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
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
