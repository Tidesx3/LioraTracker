package app.liora.feature.settings.gym

import androidx.compose.runtime.Composable
import app.liora.core.designsystem.util.NumberFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.domain.GymProfiles
import app.liora.core.model.GymProfile
import app.liora.core.model.Mass
import app.liora.core.model.WeightUnit
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.gym_bar_summary
import app.liora.feature.settings.resources.gym_plate_sizes
import app.liora.feature.settings.resources.gym_standard
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

// A gym's equipment reads in the unit it's labelled in (a 45 lb bar stays 45 lb), whatever the
// display unit chosen in Settings.

/** A gym's name, or "Standard equipment" until one is set up. */
@Composable
internal fun gymName(gym: GymProfile): String =
    if (gym.id == GymProfiles.STANDARD_ID) stringResource(Res.string.gym_standard) else gym.name

/** "20 kg bar · 7 plate sizes". */
@Composable
internal fun gymSummary(gym: GymProfile): String {
    val bar = stringResource(Res.string.gym_bar_summary, "${gymNumber(gym.barbell, gym.unit)} ${gym.unit.symbol}")
    return "$bar · " + pluralStringResource(Res.plurals.gym_plate_sizes, gym.plates.size, gym.plates.size)
}

/** A weight in [unit]'s numbers, as it goes into a text field: "20", "1,25". */
@Composable
internal fun gymNumber(
    weight: Mass,
    unit: WeightUnit,
): String = gymNumber(weight, unit, rememberNumberFormatter())

internal fun gymNumber(
    weight: Mass,
    unit: WeightUnit,
    numbers: NumberFormatter,
): String = numbers.format((weight.inUnit(unit) * THOUSANDTHS).roundToLong() / THOUSANDTHS, maxFractionDigits = 3)

private const val THOUSANDTHS = 1_000.0
