package app.liora.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * [text] that rolls like an odometer when it changes: each character that changes slides out and its
 * successor slides in, upwards as the number grows and downwards as it shrinks. Characters line up
 * from the right, so units stay over units as a number gains a digit. With [rollIn] the text also
 * rolls in from blank when it first shows. Wider than its room, it shrinks to fit rather than wrap.
 *
 * Only drawing moves: a change shows at once to everything else. With the system's animations off,
 * Compose finishes the roll at once.
 */
@Composable
fun RollingText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    rollIn: Boolean = false,
) {
    val roll = remember { RollDirection(text.text) }
    // Each character on a line of its own height: a fixed line height would give the small unit letters
    // as tall a line as the digits, lowering the baseline row they share.
    val charStyle = remember(style) { style.copy(lineHeight = TextUnit.Unspecified) }
    val up = roll.upTo(text.text)
    // Characters that appear later, as a number gains a digit, roll in like the rest.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    // One text to screen readers and tests, however it is drawn.
    ShrinkToFit(modifier.clearAndSetSemantics { this.text = text }) {
        Row {
            for (index in text.indices) {
                key(text.length - index) {
                    RollingChar(
                        char = text.subSequence(index, index + 1),
                        up = up,
                        style = charStyle,
                        rollIn = rollIn || shown,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
    }
}

/** Remembers the last text, and whether the latest change made its number bigger. */
private class RollDirection(
    private var last: String,
) {
    private var up = true

    fun upTo(text: String): Boolean {
        if (text != last) {
            up = rollsUp(last, text)
            last = text
        }
        return up
    }
}

/**
 * Whether going from [old] to [new] counts up: compares the digits as one number, so "0:59" to "1:00"
 * and "825 kg" to "1,650 kg" roll up and a countdown rolls down. Anything without digits rolls up.
 */
internal fun rollsUp(
    old: String,
    new: String,
): Boolean {
    if (old.none(Char::isDigit) || new.none(Char::isDigit)) return true
    // Digit strings without leading zeros compare as numbers: by length, then digit by digit.
    val before = old.filter(Char::isDigit).trimStart('0')
    val after = new.filter(Char::isDigit).trimStart('0')
    return if (after.length != before.length) after.length > before.length else after >= before
}

/** One character: when it changes, the old one slides out and the new one in, clipped to its line. */
@Composable
private fun RollingChar(
    char: AnnotatedString,
    up: Boolean,
    style: TextStyle,
    rollIn: Boolean,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf(if (rollIn) BLANK else char) }
    var leaving by remember { mutableStateOf<AnnotatedString?>(null) }
    var upwards by remember { mutableStateOf(up) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(char) {
        if (char == current) return@LaunchedEffect
        leaving = current
        current = char
        upwards = up
        progress.snapTo(0f)
        progress.animateTo(1f, RollSpec)
        leaving = null
    }
    Layout(
        content = {
            BasicText(current, style = style, maxLines = 1, softWrap = false)
            leaving?.let { BasicText(it, style = style, maxLines = 1, softWrap = false) }
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val incoming = measurables[0].measure(loose)
        val outgoing = measurables.getOrNull(1)?.measure(loose)
        val width = max(incoming.width, outgoing?.width ?: 0)
        val height = max(incoming.height, outgoing?.height ?: 0)
        // Read while placing, so a frame of the roll only places the two again.
        layout(width, height, mapOf(FirstBaseline to incoming[FirstBaseline])) {
            val done = progress.value
            val direction = if (upwards) 1 else -1
            incoming.place((width - incoming.width) / 2, (direction * (1 - done) * height).roundToInt())
            outgoing?.place((width - outgoing.width) / 2, (-direction * done * height).roundToInt())
        }
    }
}

/** [content] at its own width, scaled down as a whole when that's wider than the room there is. */
@Composable
internal fun ShrinkToFit(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints(maxHeight = constraints.maxHeight))
        val scale =
            if (constraints.hasBoundedWidth && placeable.width > constraints.maxWidth) {
                constraints.maxWidth.toFloat() / placeable.width
            } else {
                1f
            }
        val width = (placeable.width * scale).roundToInt()
        val height = (placeable.height * scale).roundToInt()
        layout(width.coerceAtLeast(constraints.minWidth), height.coerceAtLeast(constraints.minHeight)) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

private val BLANK = AnnotatedString("")

/** Quick and settled, with no bounce: digits that overshoot read as the wrong number. */
private val RollSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
