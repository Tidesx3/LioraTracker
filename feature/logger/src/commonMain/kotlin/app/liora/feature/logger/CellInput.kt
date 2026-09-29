package app.liora.feature.logger

import app.liora.core.domain.SetField
import app.liora.core.domain.SetPlaceholders
import app.liora.core.domain.parseClockDigits
import app.liora.core.domain.parseDecimalInput
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.TrackingType
import app.liora.core.ui.distanceInKilometers
import kotlin.time.Duration.Companion.seconds

// Typing on the logger's number pad. Text is kept as typed ("82." on the way to 82.5), with '.' as
// the decimal separator whatever the locale; the pad shows the locale's own. Every change is also
// written to the set straight away, so nothing typed is lost if the app dies mid-set.

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

/** How a field is typed: weights and kilometres take decimals, times fill in like a kitchen timer. */
internal enum class InputKind(
    val maxLength: Int,
) {
    Decimal(maxLength = 7),
    Whole(maxLength = 5),
    Clock(maxLength = 5),
}

internal fun SetField.inputKind(trackingType: TrackingType): InputKind =
    when (this) {
        SetField.Weight -> InputKind.Decimal
        SetField.Reps -> InputKind.Whole
        SetField.Distance -> if (trackingType.distanceInKilometers) InputKind.Decimal else InputKind.Whole
        SetField.Duration -> InputKind.Clock
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
                if (this != InputKind.Decimal || '.' in current) return null
                current.ifEmpty { "0" } + "."
            }

            else -> {
                return null
            }
        }
    val decimals = next.substringAfter('.', missingDelimiterValue = "").length
    return next.takeIf { it.length <= maxLength && decimals <= MAX_DECIMALS }
}

/** The set with [text] as [field]'s value; empty or unreadable text clears the field. */
internal fun LoggedSet.withTyped(
    field: SetField,
    text: String,
    trackingType: TrackingType,
): LoggedSet =
    when (field) {
        SetField.Weight -> copy(weight = parseDecimalInput(text)?.let(::Mass))
        SetField.Reps -> copy(reps = text.toIntOrNull()?.takeIf { it > 0 })
        SetField.Distance -> copy(distanceMeters = parseDecimalInput(text)?.let { it * trackingType.metersPerUnit })
        SetField.Duration -> copy(duration = parseClockDigits(text)?.takeIf { it > 0 }?.seconds)
    }

/**
 * The set with [field] one step up or down, starting from its value or else its placeholder: 2.5 kg,
 * one rep, 15 seconds, 100 m on a run or 10 m on a carry. Never below zero.
 */
internal fun LoggedSet.stepped(
    field: SetField,
    up: Boolean,
    trackingType: TrackingType,
    placeholder: LoggedSet?,
): LoggedSet {
    val from = SetPlaceholders.fill(this, placeholder)
    val sign = if (up) 1 else -1
    return when (field) {
        SetField.Weight -> {
            copy(weight = Mass(((from.weight?.kilograms ?: 0.0) + sign * WEIGHT_STEP_KG).coerceAtLeast(0.0)))
        }

        SetField.Reps -> {
            copy(reps = positive((from.reps ?: 0) + sign))
        }

        SetField.Distance -> {
            copy(distanceMeters = steppedDistance(from.distanceMeters, sign, trackingType))
        }

        SetField.Duration -> {
            copy(
                duration =
                    positive((from.duration?.inWholeSeconds?.toInt() ?: 0) + sign * DURATION_STEP_S)?.seconds,
            )
        }
    }
}

private fun steppedDistance(
    meters: Double?,
    sign: Int,
    trackingType: TrackingType,
): Double? {
    val step = if (trackingType.distanceInKilometers) KM_STEP_M else M_STEP_M
    return ((meters ?: 0.0) + sign * step).takeIf { it > 0 }
}

private fun positive(value: Int): Int? = value.takeIf { it > 0 }

/** Metres per unit the distance is typed in. */
internal val TrackingType.metersPerUnit: Double get() = if (distanceInKilometers) METERS_PER_KM else 1.0

private const val MAX_DECIMALS = 2
private const val WEIGHT_STEP_KG = 2.5
private const val KM_STEP_M = 100.0
private const val M_STEP_M = 10.0
private const val DURATION_STEP_S = 15
private const val METERS_PER_KM = 1_000.0
