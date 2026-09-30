package app.liora.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.layout.currentHingeBounds

/**
 * Tabletop posture: the Fold half open on a bench, like a little laptop. The upper half stands up and
 * shows what matters from a distance: the rest countdown and the set that's up next. The lower half
 * lies flat within reach: the workout and the number pad. The split follows the hinge.
 */
@Composable
internal fun TabletopLogger(
    state: LoggerUiState.Active,
    actions: LoggerActions,
    modifier: Modifier = Modifier,
) {
    val hinge = currentHingeBounds()
    val density = LocalDensity.current
    var top by remember { mutableFloatStateOf(0f) }
    Column(modifier.testTag(LoggerTags.TABLETOP).onGloballyPositioned { top = it.positionInWindow().y }) {
        val upper = Modifier.fillMaxWidth()
        // Down to the hinge when the device says where it is, else half and half.
        val upperModifier =
            if (hinge != null &&
                hinge.top > top
            ) {
                upper.height(with(density) { (hinge.top - top).toDp() })
            } else {
                upper.weight(1f)
            }
        Row(
            modifier = upperModifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            rememberRestProgress(state.restTimer)?.let { rest ->
                RestTimerRing(rest, onAdjust = actions.rest.onAdjust, onSkip = actions.rest.onSkip)
            }
            UpNext(
                state = state,
                onLog = actions.pad.onLogCurrent,
                onToggle = actions.set.onToggleComplete,
                onOpenExercise = actions.navigation.onOpenExercise,
                modifier = Modifier.widthIn(max = UpNextWidth),
            )
        }
        hinge?.let { Spacer(Modifier.height(with(density) { it.height.toDp() })) }
        Row(Modifier.weight(1f)) {
            WorkoutList(state, actions, Modifier.weight(1f).fillMaxHeight())
            VerticalDivider()
            PadFor(state, actions, Modifier.width(PadWidth), canHide = false)
        }
    }
}

private val UpNextWidth = 360.dp
private val PadWidth = 360.dp
