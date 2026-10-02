package app.liora.core.domain

import app.liora.core.model.Equipment
import app.liora.core.model.Mass
import app.liora.core.model.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val kgGym = GymProfiles.standard(WeightUnit.Kilogram)
private val lbGym = GymProfiles.standard(WeightUnit.Pound)

private fun kg(value: Double) = Mass(value)

private fun lb(value: Double) = Mass.of(value, WeightUnit.Pound)

class LoadRoundingTest {
    @Test
    fun barbellsRoundDownToWhatThePlatesMake() {
        assertEquals(kg(100.0), LoadRounding.roundDown(kg(101.0), Equipment.Barbell, kgGym))
        assertEquals(kg(102.5), LoadRounding.roundDown(kg(102.5), Equipment.Barbell, kgGym))
        assertEquals(kg(12.5), LoadRounding.roundDown(kg(13.0), Equipment.EzBar, kgGym))
        // Lighter than the bar: nothing to load.
        assertNull(LoadRounding.roundDown(kg(15.0), Equipment.Barbell, kgGym))
    }

    @Test
    fun dumbbellsRoundDownToTheRack() {
        assertEquals(kg(22.0), LoadRounding.roundDown(kg(23.0), Equipment.Dumbbell, kgGym))
        assertEquals(kg(40.0), LoadRounding.roundDown(kg(41.0), Equipment.Dumbbell, kgGym))
        assertEquals(kg(42.5), LoadRounding.roundDown(kg(44.0), Equipment.Dumbbell, kgGym))
        assertNull(LoadRounding.roundDown(kg(1.0), Equipment.Dumbbell, kgGym))
    }

    @Test
    fun machinesAndCablesRoundDownToTheStack() {
        assertEquals(kg(35.0), LoadRounding.roundDown(kg(37.0), Equipment.Machine, kgGym))
        assertEquals(kg(35.0), LoadRounding.roundDown(kg(39.9), Equipment.Cable, kgGym))
        assertNull(LoadRounding.roundDown(kg(4.0), Equipment.Machine, kgGym))
    }

    @Test
    fun stepsGoToTheNextLoadTheGymCanMake() {
        // From an empty field, the bar; from an odd weight, the loads either side.
        assertEquals(kg(20.0), LoadRounding.step(Mass.Zero, up = true, Equipment.Barbell, kgGym))
        assertEquals(kg(102.5), LoadRounding.step(kg(101.0), up = true, Equipment.Barbell, kgGym))
        assertEquals(kg(100.0), LoadRounding.step(kg(101.0), up = false, Equipment.Barbell, kgGym))
        assertEquals(kg(20.0), LoadRounding.step(kg(20.0), up = false, Equipment.Barbell, kgGym))

        assertEquals(kg(42.5), LoadRounding.step(kg(40.0), up = true, Equipment.Dumbbell, kgGym))
        assertEquals(kg(2.0), LoadRounding.step(kg(2.0), up = false, Equipment.Dumbbell, kgGym))

        assertEquals(kg(40.0), LoadRounding.step(kg(37.0), up = true, Equipment.Machine, kgGym))
        assertEquals(kg(35.0), LoadRounding.step(kg(37.0), up = false, Equipment.Machine, kgGym))
        assertEquals(Mass.Zero, LoadRounding.step(kg(5.0), up = false, Equipment.Machine, kgGym))
    }

    @Test
    fun otherEquipmentMovesInTwoOfTheSmallestPlate() {
        assertEquals(kg(14.5), LoadRounding.step(kg(12.0), up = true, Equipment.Kettlebell, kgGym))
        assertEquals(lb(15.0), LoadRounding.step(lb(10.0), up = true, Equipment.Other, lbGym))
    }

    @Test
    fun poundPlatesAddUpExactly() {
        val rounded = LoadRounding.roundDown(lb(227.0), Equipment.Barbell, lbGym)!!
        assertEquals(225.0, rounded.inUnit(WeightUnit.Pound), 1e-9)

        val plates = LoadRounding.plates(lb(225.0), Equipment.Barbell, lbGym)!!
        assertEquals(listOf(45.0, 45.0), plates.perSide)
        assertTrue(plates.isExact)
    }

    @Test
    fun platesPerSideOnlyForBars() {
        assertEquals(listOf(25.0, 15.0, 1.25), LoadRounding.plates(kg(102.5), Equipment.Barbell, kgGym)!!.perSide)
        assertNull(LoadRounding.plates(kg(30.0), Equipment.Machine, kgGym))
    }
}

class WarmupGeneratorTest {
    @Test
    fun rampsFromTheBarTowardTheWorkingWeight() {
        assertEquals(
            listOf(
                WarmupSet(kg(20.0), 10),
                WarmupSet(kg(40.0), 8),
                WarmupSet(kg(60.0), 5),
                WarmupSet(kg(80.0), 3),
                WarmupSet(kg(90.0), 1),
            ),
            WarmupGenerator.generate(kg(100.0), Equipment.Barbell, kgGym),
        )
    }

    @Test
    fun lightWorkingWeightsGetAShortRampWithoutDuplicates() {
        assertEquals(
            listOf(WarmupSet(kg(20.0), 10), WarmupSet(kg(22.5), 5), WarmupSet(kg(30.0), 3)),
            WarmupGenerator.generate(kg(40.0), Equipment.Barbell, kgGym),
        )
    }

    @Test
    fun nothingToWarmUpForAtBarWeight() {
        assertTrue(WarmupGenerator.generate(kg(20.0), Equipment.Barbell, kgGym).isEmpty())
    }

    @Test
    fun dumbbellsAndMachinesSkipTheBar() {
        assertEquals(
            listOf(WarmupSet(kg(12.0), 8), WarmupSet(kg(18.0), 5), WarmupSet(kg(24.0), 3)),
            WarmupGenerator.generate(kg(30.0), Equipment.Dumbbell, kgGym),
        )
        assertEquals(
            listOf(WarmupSet(kg(20.0), 8), WarmupSet(kg(30.0), 5), WarmupSet(kg(40.0), 3)),
            WarmupGenerator.generate(kg(52.0), Equipment.Machine, kgGym),
        )
    }

    @Test
    fun aPoundGymRampsInWhatItsPlatesMake() {
        assertEquals(
            listOf(45.0 to 10, 90.0 to 8, 135.0 to 5, 180.0 to 3, 200.0 to 1),
            WarmupGenerator.generate(lb(225.0), Equipment.Barbell, lbGym).map {
                it.weight.inUnit(WeightUnit.Pound) to
                    it.reps
            },
        )
    }
}
