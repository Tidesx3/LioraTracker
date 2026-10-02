package app.liora.feature.logger

import app.liora.core.domain.GymProfiles
import app.liora.core.domain.SetField
import app.liora.core.model.DistanceUnit
import app.liora.core.model.Equipment
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.TrackingType
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CellInputTest {
    private val metric = Units()
    private val imperial = Units(weight = WeightUnit.Pound, distance = DistanceUnit.Mile)
    private val metricGym = GymProfiles.standard(WeightUnit.Kilogram)
    private val imperialGym = GymProfiles.standard(WeightUnit.Pound)

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
        assertEquals(Mass(82.5), typed(SetField.Weight, "82.5", TrackingType.WeightReps).weight)
        // Halfway through typing 82.5, the set already holds 82.
        assertEquals(Mass(82.0), typed(SetField.Weight, "82.", TrackingType.WeightReps).weight)
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
    fun poundsAreTypedInPoundsAndKeptInKilograms() {
        val set = typed(SetField.Weight, "225", TrackingType.WeightReps, imperial)
        assertClose(102.05828325, set.weight!!.kilograms)
        assertClose(225.0, set.weight!!.inUnit(WeightUnit.Pound))
        // A step is 5 lb, from the value as shown.
        assertClose(
            230.0,
            set
                .stepped(
                    SetField.Weight,
                    up = true,
                    TrackingType.WeightReps,
                    null,
                    imperial,
                    Equipment.Other,
                    imperialGym,
                ).weight!!
                .inUnit(
                    WeightUnit.Pound,
                ),
        )
    }

    @Test
    fun timesFillInLikeAKitchenTimer() {
        val kind = SetField.Duration.inputKind(TrackingType.Duration)
        val typed = listOf(1, 3, 0).fold<Int, String?>(null) { text, digit -> kind.append(text, PadKey.Digit(digit)) }
        assertEquals("130", typed)
        assertEquals(90.seconds, typed(SetField.Duration, "130", TrackingType.Duration).duration)
        assertNull(
            LoggedSet(
                "s",
                duration = 1.minutes,
            ).withTyped(SetField.Duration, "", TrackingType.Duration, metric).duration,
        )
    }

    @Test
    fun runsAreTypedInKilometersAndCarriesInMeters() {
        assertEquals(5_250.0, typed(SetField.Distance, "5.25", TrackingType.DistanceDuration).distanceMeters)
        assertEquals(40.0, typed(SetField.Distance, "40", TrackingType.WeightDistance).distanceMeters)
        assertNull(SetField.Distance.inputKind(TrackingType.WeightDistance).append("40", PadKey.Decimal))
    }

    @Test
    fun runsAreTypedInMilesAndCarriesInYardsWhenChosen() {
        assertClose(
            5_000.0,
            typed(SetField.Distance, "3.10686", TrackingType.DistanceDuration, imperial).distanceMeters!!,
            tolerance = 0.01,
        )
        assertClose(36.576, typed(SetField.Distance, "40", TrackingType.WeightDistance, imperial).distanceMeters!!)
        // A step on a run is a tenth of a mile; on a carry, ten yards.
        assertClose(
            1_770.2784,
            LoggedSet("s", distanceMeters = 1_609.344)
                .stepped(
                    SetField.Distance,
                    up = true,
                    TrackingType.DistanceDuration,
                    null,
                    imperial,
                    Equipment.Other,
                    imperialGym,
                ).distanceMeters!!,
        )
        assertClose(
            9.144,
            LoggedSet("s", distanceMeters = 18.288)
                .stepped(
                    SetField.Distance,
                    up = false,
                    TrackingType.WeightDistance,
                    null,
                    imperial,
                    Equipment.Other,
                    imperialGym,
                ).distanceMeters!!,
        )
    }

    @Test
    fun anRpeGoesUpToTenInHalfPoints() {
        val kind = SetField.Rpe.inputKind(TrackingType.WeightReps)
        val typed =
            listOf(PadKey.Digit(8), PadKey.Decimal, PadKey.Digit(5))
                .fold<PadKey, String?>(null) { text, key -> kind.append(text, key) }
        assertEquals("8.5", typed)
        assertEquals("10", kind.append("1", PadKey.Digit(0)))
        // Past ten, or a second decimal, the key is ignored.
        assertNull(kind.append("8", PadKey.Digit(5)))
        assertNull(kind.append("10.", PadKey.Digit(5)))
        assertNull(kind.append("8.5", PadKey.Digit(5)))
        assertEquals(8.5, typed(SetField.Rpe, "8.5", TrackingType.WeightReps).rpe)
        assertNull(typed(SetField.Rpe, "0", TrackingType.WeightReps).rpe)
        // An empty RPE steps to 8 or 7, then by half points, and stays on the scale.
        assertEquals(8.0, step(LoggedSet("s"), SetField.Rpe, up = true).rpe)
        assertEquals(7.0, step(LoggedSet("s"), SetField.Rpe, up = false).rpe)
        assertEquals(9.5, step(LoggedSet("s", rpe = 9.0), SetField.Rpe, up = true).rpe)
        assertEquals(10.0, step(LoggedSet("s", rpe = 10.0), SetField.Rpe, up = true).rpe)
    }

    @Test
    fun stepsStartFromThePlaceholderAndStopAtZero() {
        val placeholder = LoggedSet("p", weight = Mass(80.0), reps = 8)
        assertEquals(Mass(82.5), step(LoggedSet("s"), SetField.Weight, up = true, placeholder).weight)
        assertEquals(Mass(77.5), step(LoggedSet("s", weight = Mass(80.0)), SetField.Weight, up = false).weight)
        assertEquals(Mass(0.0), step(LoggedSet("s", weight = Mass(1.0)), SetField.Weight, up = false).weight)
        assertEquals(9, step(LoggedSet("s"), SetField.Reps, up = true, placeholder).reps)
        assertNull(step(LoggedSet("s", reps = 1), SetField.Reps, up = false).reps)
        assertEquals(
            75.seconds,
            LoggedSet("s", duration = 1.minutes)
                .stepped(SetField.Duration, up = true, TrackingType.Duration, null, metric, Equipment.Other, metricGym)
                .duration,
        )
        assertClose(
            5_100.0,
            LoggedSet("s", distanceMeters = 5_000.0)
                .stepped(
                    SetField.Distance,
                    up = true,
                    TrackingType.DistanceDuration,
                    null,
                    metric,
                    Equipment.Other,
                    metricGym,
                ).distanceMeters!!,
        )
    }

    @Test
    fun weightStepsGoToWhatTheGymCanLoad() {
        val gymStep = { set: LoggedSet, up: Boolean, equipment: Equipment ->
            set.stepped(SetField.Weight, up, TrackingType.WeightReps, null, metric, equipment, metricGym).weight
        }
        // An empty barbell starts at the bar; plates come in pairs of the smallest.
        assertEquals(Mass(20.0), gymStep(LoggedSet("s"), true, Equipment.Barbell))
        assertEquals(Mass(102.5), gymStep(LoggedSet("s", weight = Mass(101.0)), true, Equipment.Barbell))
        // Dumbbells go along the rack, machines along the stack.
        assertEquals(Mass(42.5), gymStep(LoggedSet("s", weight = Mass(40.0)), true, Equipment.Dumbbell))
        assertEquals(Mass(35.0), gymStep(LoggedSet("s", weight = Mass(37.0)), false, Equipment.Machine))
        // A gym with 7 kg steps on its stacks.
        val sevens = metricGym.copy(stackStep = Mass(7.0))
        assertEquals(
            Mass(21.0),
            LoggedSet("s", weight = Mass(14.0))
                .stepped(SetField.Weight, up = true, TrackingType.WeightReps, null, metric, Equipment.Cable, sevens)
                .weight,
        )
    }

    private fun typed(
        field: SetField,
        text: String,
        trackingType: TrackingType,
        units: Units = metric,
    ): LoggedSet = LoggedSet("s").withTyped(field, text, trackingType, units)

    private fun step(
        set: LoggedSet,
        field: SetField,
        up: Boolean,
        placeholder: LoggedSet? = null,
    ): LoggedSet = set.stepped(field, up, TrackingType.WeightReps, placeholder, metric, Equipment.Other, metricGym)

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 1e-6,
    ) {
        assertTrue(abs(expected - actual) < tolerance, "expected $expected but was $actual")
    }
}
