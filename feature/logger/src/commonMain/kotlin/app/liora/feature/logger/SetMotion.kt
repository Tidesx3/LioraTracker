package app.liora.feature.logger

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.motion.ConfettiBurst
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.cd_personal_record
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// Ticking a set off pops its check and fills the row from it (`SetCheck` and `drawSetFill` in
// designsystem); a set that breaks a record celebrates here too.

/**
 * The trophy on a set that broke a personal record. With [celebrate], as when the set has just been
 * logged, it pops in from nothing and a burst of confetti flies from it; [seed] picks the confetti.
 */
@Composable
internal fun RecordMark(
    celebrate: Boolean,
    seed: Int,
    modifier: Modifier = Modifier,
) {
    val pop = remember { Animatable(if (celebrate) 0f else 1f) }
    LaunchedEffect(Unit) { if (celebrate) pop.animateTo(1f, PopSpec) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(LioraIcons.Record),
            contentDescription = stringResource(Res.string.cd_personal_record),
            tint = LioraTheme.colors.personalRecord,
            modifier =
                Modifier.size(18.dp).graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
        if (celebrate) ConfettiBurst(Modifier.matchParentSize(), seed = seed)
    }
}

/** It overshoots as it settles, like the set's check. */
private val PopSpec = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)
