package app.liora.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.component.LioraAlertDialog
import app.liora.core.designsystem.component.LioraCard
import app.liora.core.designsystem.component.RollingText
import app.liora.core.designsystem.glass.glassPane
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.domain.WeekProgress
import app.liora.core.domain.WeekResult
import app.liora.core.domain.WeekStreaks
import app.liora.core.domain.WeeklyGoals
import app.liora.core.ui.resources.Res
import app.liora.core.ui.resources.dialog_cancel
import app.liora.core.ui.resources.week_any
import app.liora.core.ui.resources.week_days_a_week
import app.liora.core.ui.resources.week_described
import app.liora.core.ui.resources.week_fresh_start
import app.liora.core.ui.resources.week_goal_note
import app.liora.core.ui.resources.week_goal_reached
import app.liora.core.ui.resources.week_goal_title
import app.liora.core.ui.resources.week_more_to_go
import app.liora.core.ui.resources.week_rest_earned
import app.liora.core.ui.resources.week_rest_weeks
import app.liora.core.ui.resources.week_rested
import app.liora.core.ui.resources.week_set_goal
import app.liora.core.ui.resources.week_streak
import app.liora.core.ui.resources.week_this_week
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// The weekly goal, wherever it shows: a ring filling toward the goal, a bead for each day trained, and the
// streak. Emerald once the goal is reached, like a set done; violet on the way.

/**
 * This week against the goal: the ring and the days, what it comes to, and the streak. On the Train tab a tap
 * changes the goal ([onClick]); [afterWorkout] words it for the summary of a workout just finished.
 */
@Composable
fun WeekCard(
    week: WeekProgress,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    afterWorkout: Boolean = false,
) {
    val reached = week.met && week.goalSet
    LioraCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeekRing(week)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text =
                        when {
                            reached -> {
                                stringResource(Res.string.week_goal_reached)
                            }

                            afterWorkout && week.goalSet -> {
                                val left = week.goal - week.done
                                pluralStringResource(Res.plurals.week_more_to_go, left, left)
                            }

                            else -> {
                                stringResource(Res.string.week_this_week)
                            }
                        },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (reached) LioraTheme.colors.completed else MaterialTheme.colorScheme.onSurface,
                )
                WeekDays(week)
                WeekLines(week, afterWorkout, setGoal = onClick != null && !week.goalSet)
            }
        }
    }
}

/** What's worth saying under the days: a rest week earned or used, and the streak; or the way to a goal. */
@Composable
private fun WeekLines(
    week: WeekProgress,
    afterWorkout: Boolean,
    setGoal: Boolean,
) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.bodySmall
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when {
            afterWorkout && week.earnedRestWeek -> {
                Text(
                    stringResource(Res.string.week_rest_earned),
                    style = small,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            !afterWorkout && week.lastWeek == WeekResult.Rested -> {
                Text(stringResource(Res.string.week_rested), style = small, color = quiet)
            }
        }
        when {
            week.streak > 0 -> {
                Text(
                    pluralStringResource(Res.plurals.week_streak, week.streak, week.streak),
                    style = small,
                    color = quiet,
                )
            }

            week.lastWeek == WeekResult.Missed -> {
                Text(stringResource(Res.string.week_fresh_start), style = small, color = quiet)
            }
        }
        if (week.restWeeks > 0) {
            Text(
                text = pluralStringResource(Res.plurals.week_rest_weeks, week.restWeeks, week.restWeeks),
                style = small,
                color = quiet,
            )
        }
        if (setGoal) {
            Text(
                text = stringResource(Res.string.week_set_goal),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * A ring filling toward the goal, the days trained and the goal inside it ("3/4"), rolling as they change;
 * emerald once reached. Before a goal is chosen, just the days.
 */
@Composable
fun WeekRing(
    week: WeekProgress,
    modifier: Modifier = Modifier,
    size: Dp = RingSize,
) {
    val reached = week.met
    val share by animateFloatAsState((week.done.toFloat() / week.goal).coerceIn(0f, 1f), tween(RING_MILLIS))
    val arc = if (reached) LioraTheme.colors.completed else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA)
    val described = pluralStringResource(Res.plurals.week_described, week.goal, week.done, week.goal)
    Box(
        modifier.size(size).clearAndSetSemantics { contentDescription = described },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val width = RING_WIDTH.dp.toPx()
            val inset = width / 2
            val box = Size(this.size.width - width, this.size.height - width)
            drawArc(
                track,
                0f,
                FULL_TURN,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = box,
                style = Stroke(width),
            )
            if (share > 0f) {
                drawArc(
                    color = arc,
                    startAngle = TOP,
                    sweepAngle = FULL_TURN * share,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = box,
                    style = Stroke(width, cap = StrokeCap.Round),
                )
            }
        }
        RollingText(
            text = AnnotatedString(if (week.goalSet) "${week.done}/${week.goal}" else "${week.done}"),
            style =
                MaterialTheme.typography.titleMedium
                    .tabularNumbers()
                    .copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
        )
    }
}

/**
 * The week's days, from its first: a bead of the accent's glass for each one trained, an outline for the rest,
 * today's in the accent, the days to come faint.
 */
@Composable
fun WeekDays(
    week: WeekProgress,
    modifier: Modifier = Modifier,
) {
    val today = week.today
    val dates = rememberDateFormatter()
    val glass = LioraTheme.glass
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(DAYS_PER_WEEK) { index ->
            val day = week.weekStart.plus(index, DateTimeUnit.DAY)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val bead = Modifier.size(DaySize)
                Box(
                    when {
                        day in week.days && glass.enabled -> {
                            bead.glassPane(
                                CircleShape,
                                fill = glass.accentTint.copy(alpha = BEAD_FILL),
                                rim = glass.rim,
                            )
                        }

                        day in week.days -> {
                            bead.background(colors.primary, CircleShape)
                        }

                        day == today -> {
                            bead.border(TODAY_RING, colors.primary, CircleShape)
                        }

                        else -> {
                            bead.border(
                                DAY_RING,
                                colors.onSurfaceVariant.copy(alpha = if (day > today) LATER_ALPHA else PAST_ALPHA),
                                CircleShape,
                            )
                        }
                    },
                )
                Text(
                    text = dates.weekdayInitial(day.dayOfWeek),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day == today) colors.primary else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Picks the weekly goal: how many days a week to train. Picking closes it. A week short of it now and then
 * doesn't end the streak, as reaching it earns rest weeks; the note says so.
 */
@Composable
fun WeeklyGoalDialog(
    current: Int?,
    onChoose: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    LioraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.week_goal_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(
                    text = stringResource(Res.string.week_goal_note, WeekStreaks.EARN_EVERY),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                WeeklyGoals.CHOICES.forEach { days ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onChoose(days) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = days == current, onClick = { onChoose(days) })
                        Column {
                            Text(weeklyGoalLabel(days), style = MaterialTheme.typography.bodyLarge)
                            if (days == WeeklyGoals.ANY) {
                                Text(
                                    text = stringResource(Res.string.week_any),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

/** "3 days a week". */
@Composable
fun weeklyGoalLabel(days: Int): String = pluralStringResource(Res.plurals.week_days_a_week, days, days)

private val RingSize = 64.dp
private val DaySize = 14.dp
private val TODAY_RING = 2.dp
private val DAY_RING = 1.5.dp
private const val RING_WIDTH = 6
private const val RING_MILLIS = 600
private const val TRACK_ALPHA = 0.1f
private const val FULL_TURN = 360f
private const val TOP = -90f
private const val BEAD_FILL = 0.96f
private const val PAST_ALPHA = 0.45f
private const val LATER_ALPHA = 0.2f
private const val DAYS_PER_WEEK = 7
