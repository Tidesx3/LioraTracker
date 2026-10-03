package app.liora.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.liora.core.designsystem.theme.tabularNumbers

/**
 * Figures side by side in equal columns, so they line up from card to card and screen to screen. Give
 * each [Stat] `Modifier.weight(1f)`.
 */
@Composable
fun StatRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

/**
 * One figure: the number big and its unit smaller ("82.5 kg"), what it counts underneath, and an
 * optional [detail] below that, such as a date or last month's figure. The value stays on one line,
 * shrinking a little in a narrow column rather than wrapping. [highlight] gives it the accent, e.g.
 * for a clock that's running. [motion] rolls a figure that changes live, like an odometer.
 */
@Composable
fun Stat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    highlight: Boolean = false,
    valueStyle: TextStyle = MaterialTheme.typography.titleLarge,
    motion: StatMotion = StatMotion.None,
) {
    Column(modifier) {
        val color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        val style = valueStyle.tabularNumbers().copy(color = color)
        if (motion == StatMotion.None) {
            BasicText(
                text = figure(value),
                style = style,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_VALUE_SIZE, maxFontSize = valueStyle.fontSize),
            )
        } else {
            RollingText(figure(value), style, rollIn = motion == StatMotion.RollIn)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** How a [Stat]'s figure moves. */
enum class StatMotion {
    /** Not at all: for figures that stay as they are while shown. */
    None,

    /** Rolls when it changes, e.g. a workout's volume as sets are logged. */
    Roll,

    /** Rolls in from blank when it first shows, then like [Roll]: totals revealed at the end. */
    RollIn,
}

/** [text] with its words smaller than its numbers: "1 h 5 min", "82.5 kg", "6 weeks", "2 Trainings". */
fun figure(text: String): AnnotatedString =
    buildAnnotatedString {
        var start = 0
        for (word in WORD.findAll(text)) {
            append(text.substring(start, word.range.first))
            withStyle(SpanStyle(fontSize = UNIT_SIZE.em)) { append(word.value) }
            start = word.range.last + 1
        }
        append(text.substring(start))
    }

/** A run of letters, with the dot of an abbreviation such as "Std.". */
private val WORD = Regex("""\p{L}[\p{L}.]*""")
private const val UNIT_SIZE = 0.62f
private val MIN_VALUE_SIZE = 12.sp
