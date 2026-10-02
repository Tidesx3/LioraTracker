package app.liora.feature.logger

import app.liora.core.domain.SetField
import app.liora.core.domain.SetPlaceholders
import app.liora.core.domain.parseClockDigits
import app.liora.core.domain.parseDecimalInput
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.TrackingType
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import app.liora.core.ui.distanceFor
import app.liora.core.ui.longDistance
import kotlin.time.Duration.Companion.seconds

// Typing on the logger's number pad. Text is kept as typed ("82." on the way to 82.5), with '.' as
// the decimal separator whatever the locale; the pad shows the locale's own. Every change is also
// written to the set straight away, so nothing typed is lost if the app dies mid-set. Values are typed in
// the units chosen in Settings and stored in kilograms and metres.

/** One key of the number pad. */
sealed interface PadKey {
    data class Digit(
        val value: Int,
    ) : PadKey

    data object Decimal : PadKey

    data object Backspace : PadKey

    data object Increase : PadKey

    data object Decrease : PadKey

    /** The next field, or on a set's last field, ticking the set off. */
    data object Next : PadKey

    data object Hide : PadKey
}

/**
 * How a field is typed: weights and long distances take decimals, times fill in like a kitchen timer,
 * and an RPE takes a half point at most and goes no higher than 10.
 */
internal enum class InputKind(
    val maxLength: Int,
    val maxDecimals: Int = 0,
    val maxValue: Double? = null,
) {
    Decimal(maxLength = 7, maxDecimals = 2),
    Whole(maxLength = 5),
    Clock(maxLength = 5),
    Rating(maxLength = 4, maxDecimals = 1, maxValue = SetRpe.MAX),
    ;

    val takesDecimals: Boolean get() = maxDecimals > 0
}

internal fun SetField.inputKind(trackingType: TrackingType): InputKind =
    when (this) {
        SetField.Weight -> InputKind.Decimal
        SetField.Reps -> InputKind.Whole
        SetField.Distance -> if (trackingType.longDistance) InputKind.Decimal else InputKind.Whole
        SetField.Duration -> InputKind.Clock
        SetField.Rpe -> InputKind.Rating
    }

/** [text] with [key] typed at the end, or null when the key doesn't fit (a second separator, too long). */
internal fun InputKind.append(
    text: String?,
    key: PadKey,
): String? {
    val current = text.orEmpty()
    val next =
        when (key) {
            is PadKey.Digit -> {
                when {
                    this == InputKind.Clock -> (current + key.value).trimStart('0')
                    current == "0" -> key.value.toString()
                    else -> current + key.value
                }
            }

            PadKey.Decimal -> {
                if (!takesDecimals || '.' in current) return null
                current.ifEmpty { "0" } + "."
            }

            else -> {
                return null
            }
        }
    val decimals = next.substringAfter('.', missingDelimiterValue = "").length
    val fits = maxValue == null || (parseDecimalInput(next) ?: 0.0) <= maxValue
    return next.takeIf { it.length <= maxLength && decimals <= maxDecimals && fits }
}

/** The set with [text], typed in [units], as [field]'s value; empty or unreadable text clears the field. */
internal fun LoggedSet.withTyped(
    field: SetField,
    text: String,
    trackingType: TrackingType,
    units: Units,
): LoggedSet =
    when (field) {
        SetField.Weight -> {
            copy(weight = parseDecimalInput(text)?.let { Mass.of(it, units.weight) })
        }

        SetField.Reps -> {
            copy(reps = text.toIntOrNull()?.takeIf { it > 0 })
        }

        SetField.Distance -> {
            copy(
                distanceMeters = parseDecimalInput(text)?.let(units.distanceFor(trackingType)::toMeters),
            )
        }

        SetField.Duration -> {
            copy(duration = parseClockDigits(text)?.takeIf { it > 0 }?.seconds)
        }

        SetField.Rpe -> {
            copy(rpe = parseDecimalInput(text)?.takeIf { it >= SetRpe.MIN })
        }
    }

/**
 * The set with [field] one step up or down in [units], starting from its value or else its placeholder:
 * 2.5 kg or 5 lb, one rep, 15 seconds, a tenth of a kilometre or mile on a run, 10 metres or yards on a
 * carry, half an RPE point. Never below zero.
 */
internal fun LoggedSet.stepped(
    field: SetField,
    up: Boolean,
    trackingType: TrackingType,
    placeholder: LoggedSet?,
    units: Units,
): LoggedSet {
    val from = SetPlaceholders.fill(this, placeholder)
    val sign = if (up) 1 else -1
    return when (field) {
        SetField.Weight -> {
            val shown = (from.weight?.inUnit(units.weight) ?: 0.0) + sign * weightStep(units.weight)
            copy(weight = Mass.of(shown.coerceAtLeast(0.0), units.weight))
        }

        SetField.Reps -> {
            copy(reps = positive((from.reps ?: 0) + sign))
        }

        SetField.Distance -> {
            val unit = units.distanceFor(trackingType)
            val step = if (trackingType.longDistance) LONG_DISTANCE_STEP else SHORT_DISTANCE_STEP
            val shown = (from.distanceMeters?.let(unit::fromMeters) ?: 0.0) + sign * step
            copy(distanceMeters = shown.takeIf { it > 0 }?.let(unit::toMeters))
        }

        SetField.Duration -> {
            copy(
                duration =
                    positive((from.duration?.inWholeSeconds?.toInt() ?: 0) + sign * DURATION_STEP_S)?.seconds,
            )
        }

        // Nothing to start from: an RPE is never carried over from last time.
        SetField.Rpe -> {
            copy(rpe = ((rpe ?: SetRpe.START) + sign * SetRpe.STEP).coerceIn(SetRpe.MIN, SetRpe.MAX))
        }
    }
}

private fun weightStep(unit: WeightUnit): Double =
    when (unit) {
        WeightUnit.Kilogram -> WEIGHT_STEP_KG
        WeightUnit.Pound -> WEIGHT_STEP_LB
    }

private fun positive(value: Int): Int? = value.takeIf { it > 0 }

/** The RPE scale as the logger takes it: 1 to 10 in half points. */
internal object SetRpe {
    const val MIN = 1.0
    const val MAX = 10.0
    const val STEP = 0.5

    /** An empty RPE steps from here, so the first tap lands on 8 or 7. */
    const val START = 7.5
}

private const val WEIGHT_STEP_KG = 2.5
private const val WEIGHT_STEP_LB = 5.0
private const val LONG_DISTANCE_STEP = 0.1
private const val SHORT_DISTANCE_STEP = 10.0
private const val DURATION_STEP_S = 15
