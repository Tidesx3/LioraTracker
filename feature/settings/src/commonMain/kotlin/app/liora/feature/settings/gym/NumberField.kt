package app.liora.feature.settings.gym

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.domain.parseDecimalInput
import kotlin.math.abs

/**
 * A number above zero, typed with either decimal separator and kept as typed ("1," on the way to
 * 1,25). It takes the model's value only when that changes from outside, such as switching units.
 * [required] marks it as missing while empty.
 */
@Composable
internal fun NumberField(
    value: Double?,
    onValueChange: (Double?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    whole: Boolean = false,
    required: Boolean = false,
) {
    val numbers = rememberNumberFormatter()
    val format = { number: Double -> numbers.format(number, maxFractionDigits = MAX_DECIMALS) }
    val parse = { typed: String -> if (whole) typed.trim().toIntOrNull()?.toDouble() else parseDecimalInput(typed) }
    var text by remember { mutableStateOf(value?.let(format).orEmpty()) }
    LaunchedEffect(value) {
        val typed = parse(text)
        val same = if (typed == null || value == null) typed == value else abs(typed - value) < SAME_VALUE
        if (!same) text = value?.let(format).orEmpty()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            onValueChange(parse(typed))
        },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        isError = if (text.isBlank()) required else parse(text)?.let { it > 0 } != true,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (whole) KeyboardType.Number else KeyboardType.Decimal),
        modifier = modifier,
    )
}

private const val MAX_DECIMALS = 3
private const val SAME_VALUE = 1e-9
