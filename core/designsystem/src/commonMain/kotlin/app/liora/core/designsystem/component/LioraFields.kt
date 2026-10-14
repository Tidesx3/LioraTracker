package app.liora.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.glass.glassPane
import app.liora.core.designsystem.theme.LioraTheme

// Liora's chips, segmented buttons and text fields: Material's, as panes of glass with Liquid Glass on,
// like the buttons (LioraButtons.kt). Use these rather than Material's own.

/** A filter that's on or off. With Liquid Glass, clear glass, tinted with the accent while it's on. */
@Composable
fun LioraFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val glass = LioraTheme.glass
    val accent = MaterialTheme.colorScheme.primary
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        colors =
            if (glass.enabled) {
                FilterChipDefaults.filterChipColors(
                    containerColor = glass.control,
                    selectedContainerColor = glass.accentControl,
                    selectedLabelColor = accent,
                    selectedLeadingIconColor = accent,
                    selectedTrailingIconColor = accent,
                )
            } else {
                FilterChipDefaults.filterChipColors()
            },
        border = if (glass.enabled) glassBorder(glass) else FilterChipDefaults.filterChipBorder(true, selected),
    )
}

/** A small action in a row of them. With Liquid Glass, clear glass. */
@Composable
fun LioraAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val glass = LioraTheme.glass
    AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        leadingIcon = leadingIcon,
        colors =
            if (glass.enabled) {
                AssistChipDefaults.assistChipColors(
                    containerColor = glass.control,
                )
            } else {
                AssistChipDefaults.assistChipColors()
            },
        border = if (glass.enabled) glassBorder(glass) else AssistChipDefaults.assistChipBorder(true),
    )
}

/**
 * A fact shown as a chip, not an action: nothing happens on a tap, and screen readers read it as text. The
 * same size and shape as [LioraAssistChip]; with Liquid Glass, a pane of glass with the rim light like it,
 * without, a tonal pill.
 */
@Composable
fun LioraStatusChip(
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val glass = LioraTheme.glass
    val colors = MaterialTheme.colorScheme
    val shape = AssistChipDefaults.shape
    Row(
        modifier =
            modifier
                .heightIn(min = AssistChipDefaults.Height)
                .then(
                    if (glass.enabled) {
                        Modifier.glassPane(shape, fill = glass.control, rim = glass.cardRim)
                    } else {
                        Modifier.background(colors.surfaceContainerHigh, shape)
                    },
                ).padding(start = if (leadingIcon != null) 8.dp else 12.dp, end = 12.dp)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colors.onSurface,
            LocalTextStyle provides MaterialTheme.typography.labelLarge,
        ) {
            leadingIcon?.invoke()
            label()
        }
    }
}

/** A suggestion to pick. With Liquid Glass, clear glass. */
@Composable
fun LioraSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LioraTheme.glass
    SuggestionChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        colors =
            if (glass.enabled) {
                SuggestionChipDefaults.suggestionChipColors(containerColor = glass.control)
            } else {
                SuggestionChipDefaults.suggestionChipColors()
            },
        border = if (glass.enabled) glassBorder(glass) else SuggestionChipDefaults.suggestionChipBorder(true),
    )
}

/** One of a few choices side by side. With Liquid Glass, clear glass, tinted with the accent where chosen. */
@Composable
fun SingleChoiceSegmentedButtonRowScope.LioraSegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Material's check on the chosen one; empty where the segments are too narrow for it. */
    icon: @Composable () -> Unit = { SegmentedButtonDefaults.Icon(selected) },
    label: @Composable () -> Unit,
) {
    val glass = LioraTheme.glass
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        shape = shape,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        colors =
            if (glass.enabled) {
                SegmentedButtonDefaults.colors(
                    activeContainerColor = glass.accentControl,
                    activeContentColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = glass.control,
                    activeBorderColor = glass.cardRim,
                    inactiveBorderColor = glass.cardRim,
                )
            } else {
                SegmentedButtonDefaults.colors()
            },
        label = label,
    )
}

/**
 * A text field, outlined. With Liquid Glass its inside is a pane of glass and its outline catches the
 * light, in dark; in light the outline stays, so the field still reads on white glass.
 */
@Composable
fun LioraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    shape: Shape = OutlinedTextFieldDefaults.shape,
) {
    val glass = LioraTheme.glass
    val outline = if (glass.dark) glass.cardRim else MaterialTheme.colorScheme.outlineVariant
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        shape = shape,
        colors =
            if (glass.enabled) {
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = glass.control,
                    unfocusedContainerColor = glass.control,
                    errorContainerColor = glass.control,
                    disabledContainerColor = glass.control,
                    unfocusedBorderColor = outline,
                )
            } else {
                OutlinedTextFieldDefaults.colors()
            },
    )
}
