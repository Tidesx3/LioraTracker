package app.liora.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.Stat
import app.liora.core.designsystem.component.StatRow
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.ui.durationLabel
import app.liora.core.ui.volumeText
import app.liora.feature.history.resources.Res
import app.liora.feature.history.resources.cd_records
import app.liora.feature.history.resources.stat_duration
import app.liora.feature.history.resources.stat_records
import app.liora.feature.history.resources.stat_sets
import app.liora.feature.history.resources.stat_volume
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/** Duration, volume, sets and, when there are any, records, in equal columns. */
@Composable
internal fun WorkoutTotals(
    duration: Duration,
    volumeKg: Double,
    sets: Int,
    modifier: Modifier = Modifier,
    records: Int = 0,
    valueStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val numbers = rememberNumberFormatter()
    StatRow(modifier) {
        Stat(
            durationLabel(duration),
            stringResource(Res.string.stat_duration),
            Modifier.weight(1f),
            valueStyle = valueStyle,
        )
        // Bodyweight and cardio days move no counted load: a dash, as "0 kg" would look like a mistake.
        Stat(
            if (volumeKg > 0.0) volumeText(volumeKg) else NONE,
            stringResource(Res.string.stat_volume),
            Modifier.weight(1f),
            valueStyle = valueStyle,
        )
        Stat(numbers.format(sets), stringResource(Res.string.stat_sets), Modifier.weight(1f), valueStyle = valueStyle)
        if (records > 0) {
            Stat(
                numbers.format(records),
                stringResource(Res.string.stat_records),
                Modifier.weight(1f),
                valueStyle = valueStyle,
            )
        }
    }
}

private const val NONE = "–"

/** A gold trophy with the number of records a workout set. */
@Composable
internal fun RecordsBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(LioraIcons.Record),
            contentDescription = stringResource(Res.string.cd_records),
            tint = LioraTheme.colors.personalRecord,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = rememberNumberFormatter().format(count),
            style = MaterialTheme.typography.labelLarge.tabularNumbers(),
            color = LioraTheme.colors.personalRecord,
        )
    }
}
