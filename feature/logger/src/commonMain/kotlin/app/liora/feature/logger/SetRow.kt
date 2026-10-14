package app.liora.feature.logger

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.motion.SetCheck
import app.liora.core.designsystem.motion.SetCheckSize
import app.liora.core.designsystem.motion.SetFillSpec
import app.liora.core.designsystem.motion.drawSetFill
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.NumberFormatter
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.domain.SetField
import app.liora.core.domain.clockDigitsText
import app.liora.core.model.LoggedSet
import app.liora.core.model.RepRange
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.Units
import app.liora.core.ui.LocalUnits
import app.liora.core.ui.SetTypeMenu
import app.liora.core.ui.distanceFor
import app.liora.core.ui.setSummary
import app.liora.feature.logger.resources.Res
import app.liora.feature.logger.resources.cd_complete_set
import app.liora.feature.logger.resources.cd_personal_record
import app.liora.feature.logger.resources.cd_remove_set
import app.liora.feature.logger.resources.col_previous
import app.liora.feature.logger.resources.col_reps
import app.liora.feature.logger.resources.col_rpe
import app.liora.feature.logger.resources.col_set
import app.liora.feature.logger.resources.col_time
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.hypot

/** Everything a set row needs to draw itself. */
internal class SetRowState(
    val set: LoggedSet,
    val number: Int,
    val columns: SetColumns,
    val hints: SetHints,
    val isCurrent: Boolean,
    val edit: CellEdit?,
    val invalid: SetField?,
)

/** How an exercise is tracked, and the columns its sets have, left to right. */
internal class SetColumns(
    val trackingType: TrackingType,
    val fields: List<SetField>,
)

/** Last session's counterpart of a set, and what its empty fields show and log. */
internal class SetHints(
    val previous: LoggedSet?,
    val placeholder: LoggedSet,
    /** Whether the set broke a personal record. */
    val isRecord: Boolean = false,
    /** Whether the set was done at the top of its rep range. */
    val atTop: Boolean = false,
)

internal class SetRowActions(
    val onCellClick: (CellRef) -> Unit,
    val onToggleComplete: (setId: String) -> Unit,
    val onCopyPrevious: (setId: String) -> Unit,
    val onTypeChange: (setId: String, SetType) -> Unit,
    val onRemove: (setId: String) -> Unit,
)

/** Column titles lined up with [SetRow]. */
@Composable
internal fun SetHeader(
    columns: SetColumns,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
        HeaderText(stringResource(Res.string.col_set), Modifier.width(BadgeWidth))
        HeaderText(stringResource(Res.string.col_previous), Modifier.weight(1f), TextAlign.Start)
        columns.fields.forEach { field ->
            HeaderText(columnTitle(field, columns.trackingType), Modifier.width(field.cellWidth))
        }
        Box(Modifier.width(SetCheckSize))
    }
}

/** A column's title: what it counts, or the unit its values are typed in. */
@Composable
private fun columnTitle(
    field: SetField,
    trackingType: TrackingType,
): String =
    when (field) {
        SetField.Weight -> LocalUnits.current.weight.symbol
        SetField.Reps -> stringResource(Res.string.col_reps)
        SetField.Distance -> LocalUnits.current.distanceFor(trackingType).symbol
        SetField.Duration -> stringResource(Res.string.col_time)
        SetField.Rpe -> stringResource(Res.string.col_rpe)
    }

@Composable
private fun HeaderText(
    text: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Center,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * One set: type badge, last session's values (tap to copy), the value cells, and the tick. Completed
 * sets turn green; the set that's up next is marked on the left; swipe left to delete.
 */
@Composable
internal fun SetRow(
    state: SetRowState,
    actions: SetRowActions,
    numbers: NumberFormatter,
    modifier: Modifier = Modifier,
    /** Given for the set the logger opens at: the row brings itself on screen, then calls it. */
    onReveal: (() -> Unit)? = null,
) {
    val set = state.set
    // Records already set when the logger opens just show their trophy; a new one is celebrated. The same
    // goes for the top of a range.
    val wasRecord = remember { state.hints.isRecord }
    val wasAtTop = remember { state.hints.atTop }
    val dismiss = rememberSwipeToDismissBoxState()
    val revealer = remember { BringIntoViewRequester() }
    val revealing = onReveal != null
    val currentOnRevealed by rememberUpdatedState(onReveal)
    LaunchedEffect(revealing) {
        if (!revealing) return@LaunchedEffect
        revealer.bringIntoView()
        currentOnRevealed?.invoke()
    }
    SwipeToDismissBox(
        state = dismiss,
        // Above the rows around it, so a record's confetti flies over them.
        modifier = modifier.bringIntoViewRequester(revealer).zIndex(if (state.hints.isRecord) 1f else 0f),
        enableDismissFromStartToEnd = false,
        onDismiss = { actions.onRemove(set.id) },
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painterResource(LioraIcons.Delete),
                    contentDescription = stringResource(Res.string.cd_remove_set),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        // Opaque first: the swipe's red delete background sits right behind the row.
        val tint = LioraTheme.colors.completed.copy(alpha = COMPLETED_ALPHA)
        val filled = animateFloatAsState(if (set.isCompleted) 1f else 0f, SetFillSpec)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .drawBehind { drawSetFill(filled.value, tint, endPadding = RowPadding) }
                    .padding(horizontal = RowPadding),
            horizontalArrangement = Arrangement.spacedBy(CellGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                SetTypeMenu(set.type, state.number, onPick = { actions.onTypeChange(set.id, it) })
                if (state.isCurrent && !set.isCompleted) CurrentMarker(Modifier.align(Alignment.CenterStart))
                // On the set number's corner, so last time's values keep their room.
                if (state.hints.isRecord) {
                    RecordMark(
                        celebrate = !wasRecord,
                        seed = state.number,
                        modifier =
                            Modifier
                                .align(
                                    Alignment.TopEnd,
                                ).offset(x = RecordOffset, y = -RecordOffset)
                                .zIndex(1f),
                    )
                }
            }
            Text(
                text = previousText(state, numbers),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(enabled = state.hints.previous != null) { actions.onCopyPrevious(set.id) }
                        .padding(vertical = 8.dp),
            )
            val units = LocalUnits.current
            state.columns.fields.forEach { field ->
                val cell = CellRef(set.id, field)
                val editing = state.edit?.takeIf { it.cell == cell }
                Box {
                    ValueCell(
                        text =
                            editing?.text?.let { typedText(field, it) }
                                ?: valueText(set, field, state.columns.trackingType, units, numbers),
                        placeholder =
                            valueText(state.hints.placeholder, field, state.columns.trackingType, units, numbers),
                        focused = editing != null,
                        invalid = state.invalid == field,
                        completed = set.isCompleted,
                        width = field.cellWidth,
                        onClick = { actions.onCellClick(cell) },
                    )
                    // On the reps' corner, as a record's trophy sits on the set number's.
                    if (field == SetField.Reps && state.hints.atTop) {
                        TopMark(
                            pop = !wasAtTop,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = TopOffset, y = -TopOffset),
                        )
                    }
                }
            }
            SetCheck(completed = set.isCompleted, onClick = { actions.onToggleComplete(set.id) })
        }
    }
}

/** The marker on the set that's up next. */
@Composable
private fun CurrentMarker(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(start = 0.dp)
            .size(width = 3.dp, height = 20.dp)
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
    )
}

@Composable
private fun ValueCell(
    text: String?,
    placeholder: String?,
    focused: Boolean,
    invalid: Boolean,
    completed: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    // Keep the cell being typed into above the number pad.
    LaunchedEffect(focused) { if (focused) requester.bringIntoView() }
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .width(width)
                .height(CellHeight)
                .bringIntoViewRequester(requester)
                .testTag(LoggerTags.CELL)
                .semantics { if (focused) selected = true }
                .then(
                    when {
                        invalid -> Modifier.border(2.dp, colors.error, MaterialTheme.shapes.small)
                        focused -> Modifier.border(2.dp, colors.primary, MaterialTheme.shapes.small)
                        else -> Modifier
                    },
                ),
        shape = MaterialTheme.shapes.small,
        color =
            when {
                focused -> colors.primaryContainer
                completed -> colors.surface.copy(alpha = 0f)
                else -> colors.surfaceContainerHighest
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text ?: placeholder ?: "",
                style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
                color = if (text != null) colors.onSurface else colors.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA),
                maxLines = 1,
            )
        }
    }
}

/** Last session's values, or the routine's rep target when there is no last session. */
@Composable
private fun previousText(
    state: SetRowState,
    numbers: NumberFormatter,
): String {
    val previous = state.hints.previous
    val target = state.set.targetReps
    return when {
        previous != null -> {
            setSummary(
                state.columns.trackingType,
                previous.weight,
                previous.reps?.let(::RepRange),
                previous.duration,
                previous.distanceMeters,
            )
        }

        target?.isRange == true -> {
            "${numbers.format(target.min)}–${numbers.format(target.max)}"
        }

        else -> {
            "–"
        }
    }
}

/** A set's value for [field] as the cell shows it, in [units], or null when empty. */
internal fun valueText(
    set: LoggedSet,
    field: SetField,
    trackingType: TrackingType,
    units: Units,
    numbers: NumberFormatter,
): String? =
    when (field) {
        SetField.Weight -> set.weight?.let { numbers.format(it.inUnit(units.weight), units.weight.fractionDigits) }
        SetField.Reps -> set.reps?.let { numbers.format(it) }
        SetField.Distance -> set.distanceMeters?.let { numbers.format(units.distanceFor(trackingType).fromMeters(it)) }
        SetField.Duration -> set.duration?.formatAsClock()
        SetField.Rpe -> set.rpe?.let { numbers.format(it, maxFractionDigits = 1) }
    }

/** Text as it's being typed, with the locale's separator and times shown as m:ss. */
@Composable
private fun typedText(
    field: SetField,
    text: String,
): String =
    if (field == SetField.Duration) {
        clockDigitsText(text)
    } else {
        text.replace('.', rememberDecimalSeparator())
    }

internal val SetField.cellWidth: Dp
    get() =
        when (this) {
            SetField.Weight, SetField.Distance -> 64.dp
            SetField.Reps -> 52.dp
            SetField.Duration -> 64.dp
            SetField.Rpe -> 44.dp
        }

internal val RowHeight = 52.dp
internal val RowPadding = 12.dp
private val CellHeight = 40.dp
private val CellGap = 6.dp

/** How far a record's trophy sits out from the set number's corner. */
private val RecordOffset = 6.dp

/** How far the top of a range's chevron sits out from the reps cell's corner, inside the gap to the next. */
private val TopOffset = 4.dp
private val BadgeWidth = 32.dp
private const val COMPLETED_ALPHA = 0.14f
private const val PLACEHOLDER_ALPHA = 0.6f
