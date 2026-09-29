package app.liora.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.WeightUnit
import app.liora.core.ui.resources.Res
import app.liora.core.ui.resources.reps_count
import app.liora.core.ui.resources.set_badge_drop
import app.liora.core.ui.resources.set_badge_failure
import app.liora.core.ui.resources.set_badge_warmup
import app.liora.core.ui.resources.set_type_drop
import app.liora.core.ui.resources.set_type_failure
import app.liora.core.ui.resources.set_type_normal
import app.liora.core.ui.resources.set_type_warmup
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

val SetType.label: StringResource
    get() =
        when (this) {
            SetType.Warmup -> Res.string.set_type_warmup
            SetType.Normal -> Res.string.set_type_normal
            SetType.Drop -> Res.string.set_type_drop
            SetType.Failure -> Res.string.set_type_failure
        }

/** Numbers for the badges of a list of sets: normal sets count 1, 2, 3…; the others get 0 (they show a letter). */
fun setNumbers(types: List<SetType>): List<Int> {
    var count = 0
    return types.map { if (it == SetType.Normal) ++count else 0 }
}

/**
 * A set's badge: its number for normal sets, W, D or F otherwise, colored by type so warm-ups and
 * drop sets stand out in a long list.
 */
@Composable
fun SetTypeBadge(
    type: SetType,
    number: Int,
    modifier: Modifier = Modifier,
) {
    val color =
        when (type) {
            SetType.Warmup -> LioraTheme.colors.warmupSet
            SetType.Drop -> LioraTheme.colors.dropSet
            SetType.Failure -> LioraTheme.colors.failureSet
            SetType.Normal -> MaterialTheme.colorScheme.onSurface
        }
    val text =
        when (type) {
            SetType.Normal -> number.toString()
            SetType.Warmup -> stringResource(Res.string.set_badge_warmup)
            SetType.Drop -> stringResource(Res.string.set_badge_drop)
            SetType.Failure -> stringResource(Res.string.set_badge_failure)
        }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color =
            if (type ==
                SetType.Normal
            ) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                color.copy(alpha = 0.16f)
            },
        contentColor = color,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A [SetTypeBadge] that opens a menu to change the set's type. */
@Composable
fun SetTypeMenu(
    type: SetType,
    number: Int,
    onPick: (SetType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        SetTypeBadge(type, number, Modifier.clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SetType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        expanded = false
                        onPick(option)
                    },
                )
            }
        }
    }
}

/** Long distances read in kilometers (a 5 km run), short ones in meters (a 20 m farmer's walk). */
val TrackingType.distanceInKilometers: Boolean get() = this == TrackingType.DistanceDuration

/**
 * A set's targets or values in the columns its tracking type uses: "80 kg × 8–12", "12 reps",
 * "2,5 km · 10:00". Numbers follow the locale.
 */
@Composable
fun setSummary(
    trackingType: TrackingType,
    weight: Mass?,
    reps: RepRange?,
    duration: Duration?,
    distanceMeters: Double?,
): String {
    val numbers = rememberNumberFormatter()
    val weightText =
        weight?.takeIf { trackingType.usesWeight }?.let {
            "${numbers.format(
                it.kilograms,
            )} ${WeightUnit.Kilogram.symbol}"
        }
    val repsText =
        reps?.takeIf { trackingType.usesReps }?.let {
            if (it.isRange) "${numbers.format(it.min)}–${numbers.format(it.max)}" else numbers.format(it.min)
        }
    val distanceText =
        distanceMeters?.takeIf { trackingType.usesDistance }?.let {
            if (trackingType.distanceInKilometers) {
                "${numbers.format(
                    it / METERS_PER_KM,
                )} km"
            } else {
                "${numbers.format(it)} m"
            }
        }
    val durationText = duration?.takeIf { trackingType.usesDuration }?.formatAsClock()
    return when {
        weightText != null && repsText != null -> "$weightText × $repsText"
        repsText != null && reps != null -> pluralStringResource(Res.plurals.reps_count, reps.max, repsText)
        else -> listOfNotNull(weightText, distanceText, durationText).joinToString(" · ").ifEmpty { "–" }
    }
}

private const val METERS_PER_KM = 1_000.0
