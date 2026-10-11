package app.liora.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.glass.GlassDefaults
import app.liora.core.designsystem.glass.LocalGlassBackdrop
import app.liora.core.designsystem.glass.glass
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.resources.Res
import app.liora.core.designsystem.resources.cd_open_workout
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.designsystem.util.rememberElapsedTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Mini player for the workout in progress. Sits above the bottom navigation on every tab so the
 * workout is always one tap away; tapping it reopens the full logger. With Liquid Glass it's a pill of
 * the accent's glass, like the tabs beneath it.
 */
@Composable
fun ActiveWorkoutBar(
    title: String,
    startedAt: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elapsed = rememberElapsedTime(startedAt)
    val glass = LioraTheme.glass
    if (glass.enabled) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .dropShadow(
                        CircleShape,
                        Shadow(radius = 20.dp, color = glass.shadow, offset = DpOffset(0.dp, 6.dp)),
                    ).glass(LocalGlassBackdrop.current, CircleShape, GlassDefaults.accent())
                    .clip(CircleShape)
                    .clickable(onClick = onClick, role = Role.Button)
                    .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides glass.onAccent) {
                WorkoutBarContent(title, elapsed.formatAsClock(), dot = glass.onAccent)
            }
        }
        return
    }
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkoutBarContent(title, elapsed.formatAsClock(), dot = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RowScope.WorkoutBarContent(
    title: String,
    elapsed: String,
    dot: Color,
) {
    LiveDot(color = dot)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = elapsed,
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
        )
    }
    Icon(
        painter = painterResource(LioraIcons.ExpandUp),
        contentDescription = stringResource(Res.string.cd_open_workout),
    )
}

/**
 * The workout in progress on wide screens, where it heads the navigation rail instead of sitting above
 * the bottom bar: a FAB-sized button with a live dot and the elapsed time. Tapping it reopens the logger.
 */
@Composable
fun ActiveWorkoutRailButton(
    startedAt: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elapsed = rememberElapsedTime(startedAt)
    val description = stringResource(Res.string.cd_open_workout)
    val glass = LioraTheme.glass
    val shape = MaterialTheme.shapes.large
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .semantics { contentDescription = description }
                .then(if (glass.enabled) Modifier.glass(null, shape, GlassDefaults.accent()) else Modifier),
        shape = shape,
        color = if (glass.enabled) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (glass.enabled) glass.onAccent else MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.width(64.dp).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                Icon(painter = painterResource(LioraIcons.Train), contentDescription = null)
                LiveDot(
                    color = if (glass.enabled) glass.onAccent else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp),
                )
            }
            Text(
                text = elapsed.formatAsClock(),
                style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LiveDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "live-dot")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "live-dot-alpha",
    )
    Box(
        modifier =
            modifier
                .size(10.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(color),
    )
}
