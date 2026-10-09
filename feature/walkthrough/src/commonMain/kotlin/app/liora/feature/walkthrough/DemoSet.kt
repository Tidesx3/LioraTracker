package app.liora.feature.walkthrough

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.LioraCard
import app.liora.core.designsystem.motion.SetCheck
import app.liora.core.designsystem.motion.SetCheckSize
import app.liora.core.designsystem.motion.SetFillSpec
import app.liora.core.designsystem.motion.drawSetFill
import app.liora.core.designsystem.motion.rememberLioraHaptics
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.TrackingType
import app.liora.core.model.WeightUnit
import app.liora.core.ui.LocalUnits
import app.liora.core.ui.setSummary
import app.liora.feature.walkthrough.resources.Res
import app.liora.feature.walkthrough.resources.col_previous
import app.liora.feature.walkthrough.resources.col_reps
import app.liora.feature.walkthrough.resources.col_set
import app.liora.feature.walkthrough.resources.logging_done
import app.liora.feature.walkthrough.resources.logging_try
import org.jetbrains.compose.resources.stringResource

/**
 * A set as the logger draws it, with last time's values beside it, to tick off for real: the check pops,
 * the row fills and the phone ticks, as in a workout.
 */
@Composable
internal fun DemoSet(
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    var ticked by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberLioraHaptics()
    val unit = LocalUnits.current.weight
    val numbers = rememberNumberFormatter()
    // Round numbers in the unit chosen, a step up from last time.
    val (previous, weight) = if (unit == WeightUnit.Pound) 130.0 to 135.0 else 57.5 to 60.0
    Column(
        modifier.widthIn(max = DemoWidth),
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LioraCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = RowPadding, end = RowPadding, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(CellGap),
            ) {
                ColumnTitle(stringResource(Res.string.col_set), Modifier.width(BadgeWidth))
                ColumnTitle(stringResource(Res.string.col_previous), Modifier.weight(1f), TextAlign.Start)
                ColumnTitle(unit.symbol, Modifier.width(WeightWidth))
                ColumnTitle(stringResource(Res.string.col_reps), Modifier.width(RepsWidth))
                Spacer(Modifier.width(SetCheckSize))
            }
            val tint = LioraTheme.colors.completed.copy(alpha = COMPLETED_ALPHA)
            val filled by animateFloatAsState(if (ticked) 1f else 0f, SetFillSpec)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .drawBehind { drawSetFill(filled, tint, endPadding = RowPadding) }
                    .padding(horizontal = RowPadding),
                horizontalArrangement = Arrangement.spacedBy(CellGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = numbers.format(1),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(BadgeWidth),
                )
                Text(
                    text = setSummary(TrackingType.WeightReps, Mass.of(previous, unit), RepRange(REPS), null, null),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                DemoCell(numbers.format(weight, unit.fractionDigits), ticked, WeightWidth)
                DemoCell(numbers.format(REPS), ticked, RepsWidth)
                SetCheck(
                    completed = ticked,
                    onClick = {
                        ticked = !ticked
                        if (ticked) haptics.setLogged()
                    },
                )
            }
        }
        Text(
            text = stringResource(if (ticked) Res.string.logging_done else Res.string.logging_try),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColumnTitle(
    text: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Center,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1,
        modifier = modifier,
    )
}

/** A value of the demo set, as the logger's cells show it; filled in and fixed. */
@Composable
private fun DemoCell(
    text: String,
    completed: Boolean,
    width: Dp,
) {
    Surface(
        modifier = Modifier.width(width).height(CellHeight),
        shape = MaterialTheme.shapes.small,
        color =
            if (completed) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.bodyLarge.tabularNumbers(), maxLines = 1)
        }
    }
}

private val DemoWidth = 420.dp

// The logger's set row, measured the same.
private val RowHeight = 52.dp
private val RowPadding = 12.dp
private val CellHeight = 40.dp
private val CellGap = 6.dp
private val BadgeWidth = 32.dp
private val WeightWidth = 64.dp
private val RepsWidth = 52.dp
private const val COMPLETED_ALPHA = 0.14f
private const val REPS = 8
