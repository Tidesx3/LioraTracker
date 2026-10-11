package app.liora.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.glass.GlassDefaults
import app.liora.core.designsystem.glass.LocalGlassBackdrop
import app.liora.core.designsystem.glass.glass
import app.liora.core.designsystem.glass.glassRimBrush
import app.liora.core.designsystem.theme.LioraGlass
import app.liora.core.designsystem.theme.LioraTheme

// Liora's buttons: Material's, and with Liquid Glass ([LioraTheme.glass]) the same buttons made of glass,
// so every screen's buttons share one look. Use these rather than Material's own.

/**
 * The main action: filled with the accent. With Liquid Glass it's the accent's glass, bending what's
 * beneath it where it floats (in a top or bottom bar), at least a touch target tall so the glass and the
 * button are one. [colors] replace the accent, e.g. with the error color for a destructive action.
 */
@Composable
fun LioraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = LioraTheme.glass
    val solid = colors ?: ButtonDefaults.buttonColors()
    Button(
        onClick = onClick,
        modifier =
            if (glass.enabled) {
                modifier
                    .heightIn(
                        min = TOUCH_TARGET.dp,
                    ).glass(LocalGlassBackdrop.current, ButtonDefaults.shape, buttonGlass(colors, enabled))
            } else {
                modifier
            },
        enabled = enabled,
        colors = if (glass.enabled) glassButtonColors(glass, colors) else solid,
        elevation = if (glass.enabled) null else ButtonDefaults.buttonElevation(),
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * A secondary action, tinted with the accent. With Liquid Glass, a pane of accent-tinted glass. [colors]
 * replace the tint, e.g. to make it the main action where it's selected.
 */
@Composable
fun LioraTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = LioraTheme.glass
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors =
            colors ?: if (glass.enabled) {
                ButtonDefaults.filledTonalButtonColors(containerColor = glass.accentControl)
            } else {
                ButtonDefaults.filledTonalButtonColors()
            },
        border = if (glass.enabled) glassBorder(glass) else null,
        content = content,
    )
}

/** A quiet action, outlined. With Liquid Glass, a pane of clear glass. */
@Composable
fun LioraOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = LioraTheme.glass
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors =
            if (glass.enabled) {
                ButtonDefaults.outlinedButtonColors(containerColor = glass.control)
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
        border = if (glass.enabled) glassBorder(glass) else ButtonDefaults.outlinedButtonBorder(enabled),
        content = content,
    )
}

/**
 * A screen's floating action, labelled. With Liquid Glass, a pill of the accent's glass bending the list
 * scrolling beneath it, like the tabs below.
 */
@Composable
fun LioraFloatingButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LioraTheme.glass
    ExtendedFloatingActionButton(
        text = text,
        icon = icon,
        onClick = onClick,
        modifier =
            if (glass.enabled) {
                modifier
                    .dropShadow(
                        CircleShape,
                        Shadow(radius = 20.dp, color = glass.shadow, offset = DpOffset(0.dp, 6.dp)),
                    ).glass(LocalGlassBackdrop.current, CircleShape, GlassDefaults.accent())
            } else {
                modifier
            },
        shape = if (glass.enabled) CircleShape else FloatingActionButtonDefaults.extendedFabShape,
        containerColor = if (glass.enabled) Color.Transparent else FloatingActionButtonDefaults.containerColor,
        contentColor = if (glass.enabled) glass.onAccent else MaterialTheme.colorScheme.onPrimaryContainer,
        elevation =
            if (glass.enabled) {
                FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
            } else {
                FloatingActionButtonDefaults.elevation()
            },
    )
}

/** The light along a glass control's rim, as a border. */
@Composable
internal fun glassBorder(glass: LioraGlass): BorderStroke = BorderStroke(1.dp, glassRimBrush(glass.cardRim))

/** The accent's glass, or [custom]'s color in its place; clear glass while the button can't be used. */
@Composable
private fun buttonGlass(
    custom: ButtonColors?,
    enabled: Boolean,
) = when {
    !enabled -> {
        GlassDefaults.regular()
    }

    custom != null -> {
        GlassDefaults.accent().copy(
            tint = custom.containerColor.copy(alpha = CUSTOM_TINT),
            solidTint = custom.containerColor,
        )
    }

    else -> {
        GlassDefaults.accent()
    }
}

@Composable
private fun glassButtonColors(
    glass: LioraGlass,
    custom: ButtonColors?,
): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = Color.Transparent,
        contentColor = custom?.contentColor ?: glass.onAccent,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED),
    )

/** Material's minimum touch target: a glass button is at least this tall, so its glass is the whole button. */
private const val TOUCH_TARGET = 48
private const val CUSTOM_TINT = 0.86f
private const val DISABLED = 0.38f
