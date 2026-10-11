package app.liora.feature.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.glass.GlassDefaults
import app.liora.core.designsystem.glass.LocalGlassBackdrop
import app.liora.core.designsystem.glass.glass
import app.liora.core.designsystem.glass.glassPane
import app.liora.core.designsystem.theme.LioraGlass

// The rest timer's Liquid Glass: the pill on phones, and the ring's disc and buttons on wide screens.

/** The rest pill as the accent's glass, floating over the end of the list on a soft shadow. */
@Composable
internal fun Modifier.accentGlass(glass: LioraGlass): Modifier =
    dropShadow(CircleShape, Shadow(radius = 20.dp, color = glass.shadow, offset = DpOffset(0.dp, 6.dp)))
        .glass(LocalGlassBackdrop.current, CircleShape, GlassDefaults.accent())

/** The ring's disc: see-through glass over the aurora, the light along its rim, floating on a soft shadow. */
internal fun Modifier.glassDisc(glass: LioraGlass): Modifier =
    glassPane(CircleShape, fill = glass.card, rim = glass.rim, shadow = glass.shadow.takeUnless { glass.dark })

/** A button of glass beside the ring; [accent] for the one that matters most, in the accent's glass. */
@Composable
internal fun GlassPill(
    glass: LioraGlass,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .height(PILL_HEIGHT)
                .glassPane(CircleShape, fill = if (accent) glass.accentTint else glass.card, rim = glass.cardRim)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (accent) glass.onAccent else MaterialTheme.colorScheme.onSurface,
            LocalTextStyle provides MaterialTheme.typography.labelLarge,
        ) { content() }
    }
}

private val PILL_HEIGHT = 48.dp
