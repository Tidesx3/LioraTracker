package app.liora.core.domain

import app.liora.core.model.Equipment
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.RoutineSet
import app.liora.core.model.SetType
import app.liora.core.model.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Double progression: up a step at the top of every range, down after two sessions below it. */
class ProgressionTest {
    private val gym = GymProfiles.standard(WeightUnit.Kilogram)
    private val machine = { from: Mass, up: Boolean -> LoadRounding.step(from, up, Equipment.Machine, gym) }
    private val plan = List(3) { RoutineSet("p$it", reps = RepRange(8, 12)) }

    @Test
    fun everySetAtTheTopStepsUpAndStartsAgainAtTheBottom() {
        val target = Progression.next(plan, listOf(session(80.0, 12, 12, 12)), machine)

        assertEquals(List(3) { SetTarget(kg(85.0), 8) }, target.sets)
        assertEquals(ProgressionReason.TopReached(sets = 3, reps = 12, from = kg(80.0), to = kg(85.0)), target.reason)
    }

    @Test
    fun oneSetShortOfTheTopKeepsTheWeightAndAimsForARepMore() {
        val target = Progression.next(plan, listOf(session(80.0, 12, 12, 11)), machine)

        assertEquals(listOf(SetTarget(kg(80.0), 12), SetTarget(kg(80.0), 12), SetTarget(kg(80.0), 12)), target.sets)
        assertEquals(ProgressionReason.KeepGoing(kg(80.0), listOf(12, 12, 11)), target.reason)
        // Lower down the range, each set aims a rep past its own.
        val earlier = Progression.next(plan, listOf(session(80.0, 10, 9, 8)), machine)
        assertEquals(listOf(11, 10, 9), earlier.sets.map { it?.reps })
    }

    @Test
    fun aSetSkippedLastTimeHoldsTheStepBack() {
        val target = Progression.next(plan, listOf(session(80.0, 12, 12)), machine)

        assertEquals(listOf(SetTarget(kg(80.0), 12), SetTarget(kg(80.0), 12), SetTarget(kg(80.0), 8)), target.sets)
        assertTrue(target.reason is ProgressionReason.KeepGoing)
    }

    @Test
    fun belowTheRangeTwiceInARowStepsDown() {
        val twice = listOf(session(85.0, 9, 7, 6), session(85.0, 8, 7, 7))
        val target = Progression.next(plan, twice, machine)

        assertEquals(List(3) { SetTarget(kg(80.0), 8) }, target.sets)
        assertEquals(ProgressionReason.BelowTwice(reps = 8, from = kg(85.0), to = kg(80.0)), target.reason)
        // Once is just a bad day.
        val once = listOf(session(85.0, 9, 7, 6), session(85.0, 9, 8, 8))
        assertEquals(
            ProgressionReason.KeepGoing(kg(85.0), listOf(9, 7, 6)),
            Progression.next(plan, once, machine).reason,
        )
    }

    @Test
    fun withNothingToGoOnTheRoutinesOwnWeightsFromTheBottom() {
        val planned = plan.map { it.copy(weight = kg(60.0)) }
        val target = Progression.next(planned, emptyList(), machine)

        assertEquals(List(3) { SetTarget(kg(60.0), 8) }, target.sets)
        assertEquals(ProgressionReason.FirstTime, target.reason)
    }

    @Test
    fun warmUpsAndDropSetsKeepTheRoutinesValuesAndDontCount() {
        val planned =
            listOf(RoutineSet("w", type = SetType.Warmup, weight = kg(40.0), reps = RepRange(10))) + plan +
                RoutineSet("d", type = SetType.Drop, reps = RepRange(10, 15))
        val done =
            listOf(set(40.0, 10, SetType.Warmup)) + session(80.0, 12, 12, 12) + set(60.0, 9, SetType.Drop)
        val target = Progression.next(planned, listOf(done), machine)

        assertEquals(listOf(null) + List(3) { SetTarget(kg(85.0), 8) } + null, target.sets)
        assertTrue(target.reason is ProgressionReason.TopReached)
    }

    @Test
    fun eachSetStepsFromItsOwnWeight() {
        val dumbbells = { from: Mass, up: Boolean -> LoadRounding.step(from, up, Equipment.Dumbbell, gym) }
        // The standard rack goes in 2 kg here: each set moves to the next dumbbell up from its own.
        val pyramid = listOf(set(20.0, 12), set(22.0, 12), set(24.0, 12))
        val target = Progression.next(plan, listOf(pyramid), dumbbells)

        assertEquals(listOf(kg(22.0), kg(24.0), kg(26.0)), target.sets.map { it?.weight })
        assertEquals(kg(26.0), target.heaviest)
    }

    @Test
    fun aMachineWithItsOwnStepMovesByIt() {
        val ownStep = {
            from: Mass,
            up: Boolean,
            ->
            LoadRounding.step(from, up, Equipment.Machine, gym, ownStep = kg(5.0))
        }
        val target = Progression.next(plan, listOf(session(7.0, 12, 12, 12)), ownStep)

        assertEquals(List(3) { SetTarget(kg(12.0), 8) }, target.sets)
    }

    @Test
    fun aPoundGymStepsInPounds() {
        val poundGym = GymProfiles.standard(WeightUnit.Pound)
        val cable = { from: Mass, up: Boolean -> LoadRounding.step(from, up, Equipment.Cable, poundGym) }
        val lifted = Mass.of(50.0, WeightUnit.Pound).kilograms
        val target = Progression.next(plan, listOf(session(lifted, 12, 12, 12)), cable)

        val next = target.heaviest!!.inUnit(WeightUnit.Pound)
        assertTrue(next > 50.0, "steps up from 50 lb, got $next")
        assertEquals(next, Math.round(next * 10) / 10.0, 1e-9)
    }

    @Test
    fun atTheTopOfTheRackItSaysSo() {
        val rack = { from: Mass, up: Boolean -> LoadRounding.step(from, up, Equipment.Dumbbell, gym) }
        val heaviest = (gym.dumbbells.maxOf { it.to.kilograms })
        val target = Progression.next(plan, listOf(session(heaviest, 12, 12, 12)), rack)

        assertEquals(ProgressionReason.NothingHeavier(kg(heaviest)), target.reason)
        assertEquals(List(3) { SetTarget(kg(heaviest), 12) }, target.sets)
    }

    @Test
    fun weightedBodyweightWithoutAddedWeightStepsUpFromBodyweight() {
        val free = { from: Mass, up: Boolean -> LoadRounding.step(from, up, Equipment.Bodyweight, gym) }
        val pullUps = List(3) { LoggedSet("s$it", reps = 12, completedAt = DONE) }
        val target = Progression.next(plan, listOf(pullUps), free)

        assertTrue(target.sets.all { (it?.weight ?: Mass.Zero) > Mass.Zero })
        assertEquals(8, target.sets.first()?.reps)
    }

    @Test
    fun aBigJumpIsFlaggedButOneStepNeverIs() {
        // One step on a light cable is a big share of it, and still fine.
        assertFalse(Progression.isBigJump(kg(15.0), heaviest = kg(10.0), oneStepUp = kg(15.0), limitPercent = 10))
        // A mistyped step of 25 kg is not.
        assertTrue(Progression.isBigJump(kg(35.0), heaviest = kg(10.0), oneStepUp = kg(15.0), limitPercent = 10))
        // Within the limit, however many steps.
        assertFalse(Progression.isBigJump(kg(105.0), heaviest = kg(100.0), oneStepUp = kg(102.5), limitPercent = 10))
        assertTrue(Progression.isBigJump(kg(112.5), heaviest = kg(100.0), oneStepUp = kg(102.5), limitPercent = 10))
        // Nothing lifted lately: nothing to compare with.
        assertFalse(Progression.isBigJump(kg(100.0), heaviest = null, oneStepUp = kg(102.5), limitPercent = 10))
    }

    private fun session(
        kilograms: Double,
        vararg reps: Int,
    ) = reps.map { set(kilograms, it) }

    private fun set(
        kilograms: Double,
        reps: Int,
        type: SetType = SetType.Normal,
    ) = LoggedSet("s", type = type, weight = kg(kilograms), reps = reps, completedAt = DONE)

    private fun kg(value: Double) = Mass(value)

    private companion object {
        val DONE = Instant.parse("2026-10-01T18:00:00Z")
    }
}
