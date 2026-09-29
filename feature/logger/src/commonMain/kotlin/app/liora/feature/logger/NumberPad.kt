package app.liora.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.cd_complete_set
import app.liora.feature.logger.resources.pad_backspace
import app.liora.feature.logger.resources.pad_decrease
import app.liora.feature.logger.resources.pad_hide
import app.liora.feature.logger.resources.pad_increase
import app.liora.feature.logger.resources.pad_next
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The logger's own number pad: big keys, the locale's decimal separator, ± steps, and a Next key that
 * moves along the set and, on its last field, ticks it off. No system keyboard pops up over the list,
 * and it fits the lower half of a Fold in tabletop posture.
 */
@Composable
internal fun NumberPad(
    decimals: Boolean,
    nextLogsSet: Boolean,
    onKey: (PadKey) -> Unit,
    modifier: Modifier = Modifier,
    canHide: Boolean = true,
    keyHeight: Dp = KeyHeight,
) {
    val separator = rememberDecimalSeparator()
    Surface(modifier = modifier.testTag(LoggerTags.PAD), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(KeyGap),
        ) {
            val digitRow: @Composable RowScope.(List<Int>) -> Unit = { digits ->
                digits.forEach { digit -> TextKey(digit.toString(), keyHeight) { onKey(PadKey.Digit(digit)) } }
            }
            PadRow {
                digitRow(listOf(1, 2, 3))
                IconKey(
                    LioraIcons.Decrease,
                    stringResource(Res.string.pad_decrease),
                    keyHeight,
                ) { onKey(PadKey.Decrease) }
            }
            PadRow {
                digitRow(listOf(4, 5, 6))
                IconKey(LioraIcons.Add, stringResource(Res.string.pad_increase), keyHeight) { onKey(PadKey.Increase) }
            }
            PadRow {
                digitRow(listOf(7, 8, 9))
                IconKey(
                    LioraIcons.Backspace,
                    stringResource(Res.string.pad_backspace),
                    keyHeight,
                ) { onKey(PadKey.Backspace) }
            }
            PadRow {
                TextKey(separator.toString(), keyHeight, enabled = decimals) { onKey(PadKey.Decimal) }
                TextKey("0", keyHeight) { onKey(PadKey.Digit(0)) }
                if (canHide) {
                    IconKey(
                        LioraIcons.HideKeyboard,
                        stringResource(Res.string.pad_hide),
                        keyHeight,
                    ) { onKey(PadKey.Hide) }
                } else {
                    Box(Modifier.weight(1f))
                }
                if (nextLogsSet) {
                    IconKey(
                        icon = LioraIcons.Check,
                        description = stringResource(Res.string.cd_complete_set),
                        height = keyHeight,
                        container = LioraTheme.colors.completed,
                        content = LioraTheme.colors.onCompleted,
                    ) { onKey(PadKey.Next) }
                } else {
                    IconKey(
                        icon = LioraIcons.Next,
                        description = stringResource(Res.string.pad_next),
                        height = keyHeight,
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                    ) { onKey(PadKey.Next) }
                }
            }
        }
    }
}

@Composable
private fun PadRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap), content = content)
}

@Composable
private fun RowScope.TextKey(
    label: String,
    height: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Key(height = height, enabled = enabled, onClick = onClick) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun RowScope.IconKey(
    icon: DrawableResource,
    description: String,
    height: Dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Key(
        height = height,
        container = container,
        contentColor = content,
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(painterResource(icon), contentDescription = null)
    }
}

@Composable
private fun RowScope.Key(
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.weight(1f).height(height),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = DISABLED_ALPHA),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** The decimal separator of the app's locale: "," in German, "." in English. */
@Composable
internal fun rememberDecimalSeparator(): Char {
    val numbers = rememberNumberFormatter()
    return numbers.format(1.5, maxFractionDigits = 1).firstOrNull { !it.isDigit() } ?: '.'
}

internal val KeyHeight = 52.dp
private val KeyGap = 6.dp
private const val DISABLED_ALPHA = 0.38f
