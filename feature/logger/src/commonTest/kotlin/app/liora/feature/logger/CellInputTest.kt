package app.liora.feature.logger

import app.liora.core.domain.SetField
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.TrackingType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CellInputTest {
    @Test
    fun typingAWeightKeepsTheSeparatorWhileTyping() {
        val kind = SetField.Weight.inputKind(TrackingType.WeightReps)
        val typed =
            listOf(
                PadKey.Digit(8),
                PadKey.Digit(2),
                PadKey.Decimal,
                PadKey.Digit(5),
            ).fold<PadKey, String?>(null) { text, key -> kind.append(text, key) }
        assertEquals("82.5", typed)
        assertEquals(Mass(82.5), LoggedSet("s").withTyped(SetField.Weight, "82.5", TrackingType.WeightReps).weight)
        // Halfway through typing 82.5, the set already holds 82.
        assertEquals(Mass(82.0), LoggedSet("s").withTyped(SetField.Weight, "82.", TrackingType.WeightReps).weight)
        // A second separator, a third decimal or a separator in reps is ignored.
        assertNull(kind.append("82.5", PadKey.Decimal))
        assertNull(kind.append("82.25", PadKey.Digit(1)))
        assertNull(SetField.Reps.inputKind(TrackingType.WeightReps).append("8", PadKey.Decimal))
        // Starting with the separator means zero point something.
        assertEquals("0.", kind.append(null, PadKey.Decimal))
        // A leading zero gives way to the next digit.
        assertEquals("5", kind.append("0", PadKey.Digit(5)))
    }

    @Test
    fun timesFillInLikeAKitchenTimer() {
        val kind = SetField.Duration.inputKind(TrackingType.Duration)
        val typed = listOf(1, 3, 0).fold<Int, String?>(null) { text, digit -> kind.append(text, PadKey.Digit(digit)) }
        assertEquals("130", typed)
        assertEquals(90.seconds, LoggedSet("s").withTyped(SetField.Duration, "130", TrackingType.Duration).duration)
        assertNull(
            LoggedSet("s", duration = 1.minutes).withTyped(SetField.Duration, "", TrackingType.Duration).duration,
        )
    }

    @Test
    fun runsAreTypedInKilometersAndCarriesInMeters() {
        assertEquals(
            5_250.0,
            LoggedSet("s").withTyped(SetField.Distance, "5.25", TrackingType.DistanceDuration).distanceMeters,
        )
        assertEquals(
            40.0,
            LoggedSet("s").withTyped(SetField.Distance, "40", TrackingType.WeightDistance).distanceMeters,
        )
        assertNull(SetField.Distance.inputKind(TrackingType.WeightDistance).append("40", PadKey.Decimal))
    }

    @Test
    fun stepsStartFromThePlaceholderAndStopAtZero() {
        val placeholder = LoggedSet("p", weight = Mass(80.0), reps = 8)
        assertEquals(
            Mass(82.5),
            LoggedSet("s").stepped(SetField.Weight, up = true, TrackingType.WeightReps, placeholder).weight,
        )
        assertEquals(
            Mass(77.5),
            LoggedSet(
                "s",
                weight = Mass(80.0),
            ).stepped(SetField.Weight, up = false, TrackingType.WeightReps, null).weight,
        )
        assertEquals(
            Mass(0.0),
            LoggedSet(
                "s",
                weight = Mass(1.0),
            ).stepped(SetField.Weight, up = false, TrackingType.WeightReps, null).weight,
        )
        assertEquals(9, LoggedSet("s").stepped(SetField.Reps, up = true, TrackingType.WeightReps, placeholder).reps)
        assertNull(LoggedSet("s", reps = 1).stepped(SetField.Reps, up = false, TrackingType.WeightReps, null).reps)
        assertEquals(
            75.seconds,
            LoggedSet(
                "s",
                duration = 1.minutes,
            ).stepped(SetField.Duration, up = true, TrackingType.Duration, null).duration,
        )
        assertEquals(
            5_100.0,
            LoggedSet(
                "s",
                distanceMeters = 5_000.0,
            ).stepped(SetField.Distance, up = true, TrackingType.DistanceDuration, null).distanceMeters,
        )
    }
}
