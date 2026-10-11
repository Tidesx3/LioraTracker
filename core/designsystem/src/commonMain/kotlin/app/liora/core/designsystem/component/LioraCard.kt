package app.liora.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.glass.glassPane
import app.liora.core.designsystem.theme.LioraTheme

/**
 * A card: in light themes a white tile floating on a soft shadow, in dark ones a lighter shade, where
 * shadows don't show. With Liquid Glass it's a see-through pane over the aurora, light catching its
 * rim. [border] highlights it, e.g. as a superset or the selection beside a list; [containerColor]
 * replaces its color, e.g. for a selected card; [elevation] lifts it, e.g. while it's dragged.
 */
@Composable
fun LioraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = LioraCardDefaults.containerColor(),
    border: BorderStroke? = null,
    elevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val glass = LioraTheme.glass
    // A wide, faint shadow rather than Material's tight one: the card floats instead of standing out.
    // Glass keeps its shadow outside the pane, so it doesn't cloud what shows through.
    val floating =
        when {
            glass.enabled -> {
                modifier.glassPane(
                    shape = shape,
                    fill = containerColor,
                    rim = if (border == null) glass.cardRim else Color.Transparent,
                    shadow = glass.shadow.takeUnless { glass.dark },
                )
            }

            isDark() -> {
                modifier
            }

            else -> {
                modifier.shadow(SHADOW_ELEVATION.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            }
        }
    val colors =
        if (glass.enabled) {
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
                contentColor =
                    MaterialTheme.colorScheme.contentColorFor(containerColor).takeOrElse {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        } else {
            CardDefaults.cardColors(containerColor = containerColor)
        }
    val shadow = CardDefaults.cardElevation(defaultElevation = elevation)
    if (onClick != null) {
        Card(onClick, floating, shape = shape, colors = colors, elevation = shadow, border = border, content = content)
    } else {
        Card(floating, shape = shape, colors = colors, elevation = shadow, border = border, content = content)
    }
}

object LioraCardDefaults {
    /** White in light themes; a step up from the screen in dark ones; see-through with Liquid Glass. */
    @Composable
    fun containerColor(): Color =
        with(MaterialTheme.colorScheme) {
            when {
                LioraTheme.glass.enabled -> LioraTheme.glass.card
                isDark() -> surfaceContainerHigh
                else -> surfaceContainerLowest
            }
        }

    /** For a `ListItem` on a card: the card shows through, so its rows sit on it as one group. */
    @Composable
    fun listItemColors(): ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
}

/** Whether the theme is a dark one, from its surface, so wallpaper colors count too. */
@Composable
internal fun isDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < HALF

private const val SHADOW_ELEVATION = 10
private const val HALF = 0.5f
private val ShadowColor = Color(0x1F000000)
