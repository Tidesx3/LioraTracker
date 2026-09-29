package app.liora.feature.train.routine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.domain.clockDigits
import app.liora.core.domain.clockDigitsText
import app.liora.core.domain.parseClockDigits
import app.liora.core.domain.parseDecimalInput
import app.liora.core.model.RepRange

// Compact target inputs for the routine editor. Each keeps the text as typed ("82," while typing
// 82,5) and only adopts the model's value when it changes from outside, e.g. a copied set.

/** A decimal such as a weight; accepts both decimal separators. */
@Composable
internal fun DecimalTargetField(
    value: Double?,
    onValueChange: (Double?) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberNumberFormatter()
    var text by remember { mutableStateOf(value?.let(numbers::format).orEmpty()) }
    LaunchedEffect(value) { if (parseDecimalInput(text) != value) text = value?.let(numbers::format).orEmpty() }
    TargetField(
        value = TextFieldValue(text, TextRange(text.length)),
        onValueChange = { typed ->
            val parsed = parseDecimalInput(typed.text)
            if (typed.text.isEmpty() || parsed != null) {
                text = typed.text
                onValueChange(parsed?.takeIf { it > 0 })
            }
        },
        placeholder = placeholder,
        keyboardType = KeyboardType.Decimal,
        modifier = modifier,
    )
}

/** A whole number such as reps. */
@Composable
internal fun IntTargetField(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value?.toString().orEmpty()) }
    LaunchedEffect(value) { if (text.toIntOrNull() != value) text = value?.toString().orEmpty() }
    TargetField(
        value = TextFieldValue(text, TextRange(text.length)),
        onValueChange = { typed ->
            text = typed.text.filter { it.isDigit() }.take(MAX_REP_DIGITS)
            onValueChange(text.toIntOrNull()?.takeIf { it > 0 })
        },
        placeholder = placeholder,
        keyboardType = KeyboardType.Number,
        modifier = modifier,
    )
}

/** A time typed like on a kitchen timer: 1, 3, 0 reads 1:30. Only needs the number keyboard. */
@Composable
internal fun ClockTargetField(
    seconds: Int?,
    onSecondsChange: (Int?) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var digits by remember { mutableStateOf(seconds?.let(::clockDigits).orEmpty()) }
    LaunchedEffect(seconds) {
        if (parseClockDigits(digits)?.takeIf { it > 0 } !=
            seconds
        ) {
            digits = seconds?.let(::clockDigits).orEmpty()
        }
    }
    val text = clockDigitsText(digits)
    TargetField(
        value = TextFieldValue(text, TextRange(text.length)),
        onValueChange = { typed ->
            digits =
                typed.text
                    .filter { it.isDigit() }
                    .trimStart('0')
                    .take(MAX_CLOCK_DIGITS)
            onSecondsChange(parseClockDigits(digits)?.takeIf { it > 0 })
        },
        placeholder = placeholder,
        keyboardType = KeyboardType.Number,
        modifier = modifier,
    )
}

@Composable
private fun TargetField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    val textStyle =
        MaterialTheme.typography.bodyLarge
            .tabularNumbers()
            .copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(40.dp),
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        decorationBox = { innerTextField ->
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    if (value.text.isEmpty()) {
                        Text(placeholder, style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                    innerTextField()
                }
            }
        },
    )
}

// Min and max are typed one digit at a time, so a max below the min (the "1" of "12") only drops the
// range for now instead of flipping the numbers around.

internal fun RepRange?.withMin(min: Int?): RepRange? {
    val max = this?.takeIf { it.isRange }?.max
    return when {
        min == null -> max?.let(::RepRange)
        max == null || min >= max -> RepRange(min)
        else -> RepRange(min, max)
    }
}

internal fun RepRange?.withMax(max: Int?): RepRange? {
    val min = this?.min
    return when {
        min == null -> max?.let(::RepRange)
        max == null || max <= min -> RepRange(min)
        else -> RepRange(min, max)
    }
}

private const val MAX_REP_DIGITS = 3
private const val MAX_CLOCK_DIGITS = 5
