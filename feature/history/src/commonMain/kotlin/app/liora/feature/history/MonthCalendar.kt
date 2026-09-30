package app.liora.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.domain.TrainingCalendar
import app.liora.feature.history.resources.Res
import app.liora.feature.history.resources.cd_next_month
import app.liora.feature.history.resources.cd_previous_month
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource

/** What the calendar shows and where it can go. */
internal class CalendarMonth(
    val month: YearMonth,
    val trainingDays: Set<LocalDate>,
    val today: LocalDate,
    val selectedDay: LocalDate?,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
)

internal class CalendarActions(
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    /** A training day was tapped. */
    val onSelectDay: (LocalDate) -> Unit,
)

/**
 * One month at a time: training days are filled, today is ringed. Weeks start where the locale starts
 * them, and only training days can be tapped, since only they have something to show.
 */
@Composable
internal fun MonthCalendar(
    state: CalendarMonth,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(state.canGoBack) {
                LioraIconButton(
                    LioraIcons.ChevronLeft,
                    stringResource(Res.string.cd_previous_month),
                    actions.onPrevious,
                )
            }
            Text(
                text = dates.month(state.month),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            MonthArrow(state.canGoForward) {
                LioraIconButton(LioraIcons.ChevronRight, stringResource(Res.string.cd_next_month), actions.onNext)
            }
        }
        Row {
            TrainingCalendar.weekdays(dates.firstDayOfWeek).forEach { day ->
                Text(
                    text = dates.weekdayInitial(day),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        TrainingCalendar.weeks(state.month, dates.firstDayOfWeek).forEach { week ->
            Row {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            Day(
                                day = day,
                                trained = day in state.trainingDays,
                                today = day == state.today,
                                selected = day == state.selectedDay,
                                onClick = { actions.onSelectDay(day) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Keeps the header's layout steady when an arrow has nowhere to go. */
@Composable
private fun MonthArrow(
    enabled: Boolean,
    arrow: @Composable () -> Unit,
) {
    if (enabled) arrow() else Spacer(Modifier.size(48.dp))
}

@Composable
private fun Day(
    day: LocalDate,
    trained: Boolean,
    today: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color =
            when {
                selected -> colors.primary
                trained -> colors.primaryContainer
                else -> colors.surface
            },
        contentColor =
            when {
                selected -> colors.onPrimary
                trained -> colors.onPrimaryContainer
                else -> colors.onSurface
            },
        border = if (today) BorderStroke(2.dp, colors.primary) else null,
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics { if (selected) this.selected = true }
                .clip(CircleShape)
                .then(if (trained) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = rememberNumberFormatter().format(day.day),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            )
        }
    }
}
