package app.liora.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.motion.ConfettiBurst
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.domain.RecordHighlight
import app.liora.core.model.TrackingType
import org.jetbrains.compose.resources.painterResource

/** One exercise on a [RecordsCard]: its name, how it's tracked, and its records. */
@Immutable
data class RecordsCardEntry(
    val exerciseName: String,
    val trackingType: TrackingType,
    val records: List<RecordHighlight>,
)

/**
 * Personal records, gold like the trophy on the sets that set them: [title] by a trophy, then each
 * exercise with a row per record, the record on the left and the value it was set at on the right.
 * With [celebrate], confetti bursts from the trophy as the card first shows, e.g. at the end of a workout.
 */
@Composable
fun RecordsCard(
    title: String,
    entries: List<RecordsCardEntry>,
    modifier: Modifier = Modifier,
    celebrate: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = LioraTheme.colors.personalRecord.copy(alpha = BACKGROUND_ALPHA),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Above the title, so the confetti flies over it.
                Box(Modifier.zIndex(1f), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(LioraIcons.Record),
                        contentDescription = null,
                        tint = LioraTheme.colors.personalRecord,
                        modifier = Modifier.size(20.dp),
                    )
                    if (celebrate) ConfettiBurst(Modifier.matchParentSize(), reach = CardBurstReach)
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
            entries.forEach { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.exerciseName, style = MaterialTheme.typography.titleSmall)
                    entry.records.forEach { record ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = recordLabel(record.key),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                            )
                            Text(
                                text = valueText(record.key.type.unit, record.value, entry.trackingType),
                                style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                                color = LioraTheme.colors.personalRecord,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val BACKGROUND_ALPHA = 0.12f

/** Across the card, from the trophy in its corner. */
private val CardBurstReach = 72.dp
