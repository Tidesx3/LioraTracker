package app.liora.core.ui

import androidx.compose.runtime.Composable
import app.liora.core.designsystem.util.NumberFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.model.Mass
import app.liora.core.model.MeasurementKind
import app.liora.core.model.MeasurementType
import app.liora.core.model.PhotoPose
import app.liora.core.model.Units
import app.liora.core.ui.resources.Res
import app.liora.core.ui.resources.measurement_abdomen
import app.liora.core.ui.resources.measurement_biceps_left
import app.liora.core.ui.resources.measurement_biceps_right
import app.liora.core.ui.resources.measurement_body_fat
import app.liora.core.ui.resources.measurement_bodyweight
import app.liora.core.ui.resources.measurement_calf_left
import app.liora.core.ui.resources.measurement_calf_right
import app.liora.core.ui.resources.measurement_chest
import app.liora.core.ui.resources.measurement_forearm_left
import app.liora.core.ui.resources.measurement_forearm_right
import app.liora.core.ui.resources.measurement_hips
import app.liora.core.ui.resources.measurement_neck
import app.liora.core.ui.resources.measurement_shoulders
import app.liora.core.ui.resources.measurement_thigh_left
import app.liora.core.ui.resources.measurement_thigh_right
import app.liora.core.ui.resources.measurement_waist
import app.liora.core.ui.resources.pose_back
import app.liora.core.ui.resources.pose_front
import app.liora.core.ui.resources.pose_side
import app.liora.core.ui.resources.value_percent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.round

// Body measurements as people see and type them, in the units chosen in Settings: bodyweight in kilograms
// or pounds, body fat in percent, and circumferences in centimetres or inches (stored in metres).

val MeasurementType.label: StringResource
    get() =
        when (this) {
            MeasurementType.Bodyweight -> Res.string.measurement_bodyweight
            MeasurementType.BodyFat -> Res.string.measurement_body_fat
            MeasurementType.Neck -> Res.string.measurement_neck
            MeasurementType.Shoulders -> Res.string.measurement_shoulders
            MeasurementType.Chest -> Res.string.measurement_chest
            MeasurementType.BicepsLeft -> Res.string.measurement_biceps_left
            MeasurementType.BicepsRight -> Res.string.measurement_biceps_right
            MeasurementType.ForearmLeft -> Res.string.measurement_forearm_left
            MeasurementType.ForearmRight -> Res.string.measurement_forearm_right
            MeasurementType.Abdomen -> Res.string.measurement_abdomen
            MeasurementType.Waist -> Res.string.measurement_waist
            MeasurementType.Hips -> Res.string.measurement_hips
            MeasurementType.ThighLeft -> Res.string.measurement_thigh_left
            MeasurementType.ThighRight -> Res.string.measurement_thigh_right
            MeasurementType.CalfLeft -> Res.string.measurement_calf_left
            MeasurementType.CalfRight -> Res.string.measurement_calf_right
        }

val PhotoPose.label: StringResource
    get() =
        when (this) {
            PhotoPose.Front -> Res.string.pose_front
            PhotoPose.Side -> Res.string.pose_side
            PhotoPose.Back -> Res.string.pose_back
        }

/** The unit a measurement is shown and typed in: "kg", "lb", "%", "cm", "in". */
fun MeasurementType.unitSymbol(units: Units): String =
    when (kind) {
        MeasurementKind.Mass -> units.weight.symbol
        MeasurementKind.Percent -> "%"
        MeasurementKind.Length -> units.bodyLength.symbol
    }

/** A stored value in the unit it's shown in. */
fun MeasurementType.toShown(
    stored: Double,
    units: Units,
): Double =
    when (kind) {
        MeasurementKind.Mass -> Mass(stored).inUnit(units.weight)
        MeasurementKind.Percent -> stored
        MeasurementKind.Length -> units.bodyLength.fromMeters(stored)
    }

/** A value typed in the shown unit, as it's stored. */
fun MeasurementType.fromShown(
    shown: Double,
    units: Units,
): Double =
    when (kind) {
        MeasurementKind.Mass -> Mass.of(shown, units.weight).kilograms
        MeasurementKind.Percent -> shown
        MeasurementKind.Length -> units.bodyLength.toMeters(shown)
    }

/** A measurement with its unit, the way the locale writes it: "82,5 kg", "18,5 %", "85 cm". */
@Composable
fun measurementText(
    type: MeasurementType,
    value: Double,
): String {
    val units = LocalUnits.current
    return withUnit(type, units, measurementAxisText(type.toShown(value, units), rememberNumberFormatter()))
}

/** How far a measurement moved, signed: "−1,5 kg", "+0,5 cm", "±0 %". */
@Composable
fun measurementChangeText(
    type: MeasurementType,
    amount: Double,
): String {
    val units = LocalUnits.current
    val shown = round(type.toShown(amount, units) * TENTHS) / TENTHS
    val sign =
        when {
            shown > 0 -> "+"
            shown < 0 -> "−"
            else -> "±"
        }
    return withUnit(type, units, sign + rememberNumberFormatter().format(abs(shown), maxFractionDigits = 1))
}

/** A measurement already in the unit it's shown in, to one decimal, for a chart axis whose title names it: "82,5". */
fun measurementAxisText(
    shown: Double,
    numbers: NumberFormatter,
): String = numbers.format(shown, maxFractionDigits = 1)

/** A measurement as it goes back into a text field to be corrected, with every decimal it was typed with. */
fun measurementInputText(
    type: MeasurementType,
    value: Double,
    units: Units,
    numbers: NumberFormatter,
): String = numbers.format(type.toShown(value, units))

@Composable
private fun withUnit(
    type: MeasurementType,
    units: Units,
    number: String,
): String =
    when (type.kind) {
        MeasurementKind.Percent -> stringResource(Res.string.value_percent, number)
        MeasurementKind.Mass, MeasurementKind.Length -> "$number ${type.unitSymbol(units)}"
    }

private const val TENTHS = 10.0
