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
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.model.WeightUnit
import app.liora.core.ui.durationLabel
import app.liora.feature.history.resources.Res
import app.liora.feature.history.resources.cd_records
import app.liora.feature.history.resources.stat_duration
import app.liora.feature.history.resources.stat_records
import app.liora.feature.history.resources.stat_sets
import app.liora.feature.history.resources.stat_volume
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/** Duration, volume, sets and (when there are any) records, side by side. */
@Composable
internal fun WorkoutTotals(
    duration: Duration,
    volumeKg: Double,
    sets: Int,
    records: Int,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val numbers = rememberNumberFormatter()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Total(stringResource(Res.string.stat_duration), durationLabel(duration), valueStyle)
        // Bodyweight and cardio days move no counted load; "0 kg" would only look like a mistake.
        if (volumeKg > 0.0) {
            Total(
                stringResource(Res.string.stat_volume),
                "${numbers.format(volumeKg, maxFractionDigits = 0)} ${WeightUnit.Kilogram.symbol}",
                valueStyle,
            )
        }
        Total(stringResource(Res.string.stat_sets), numbers.format(sets), valueStyle)
        if (records > 0) Total(stringResource(Res.string.stat_records), numbers.format(records), valueStyle)
    }
}

@Composable
private fun Total(
    label: String,
    value: String,
    valueStyle: TextStyle,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = valueStyle.tabularNumbers())
    }
}

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
