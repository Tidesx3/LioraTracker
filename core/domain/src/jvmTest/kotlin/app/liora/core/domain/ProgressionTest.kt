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
import kotlin.test.assertNull
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
    fun oneSetShortOfTheTopKeepsTheWeightAndAimsForARepMoreOnIt() {
        val target = Progression.next(plan, listOf(session(80.0, 12, 12, 11)), machine)

        assertEquals(listOf(SetTarget(kg(80.0), 12), SetTarget(kg(80.0), 12), SetTarget(kg(80.0), 12)), target.sets)
        assertEquals(ProgressionReason.KeepGoing(kg(80.0), listOf(12, 12, 11), set = 3), target.reason)
    }

    @Test
    fun aRepMoreInAllOnTheWeakestSet() {
        // The later sets tire first: the one that did fewest gets the extra rep.
        val tiring = Progression.next(plan, listOf(session(80.0, 10, 9, 8)), machine)
        assertEquals(listOf(10, 9, 9), tiring.sets.map { it?.reps })
        // On a tie, the first: 8, 8, 8 becomes 9, 8, 8, then 9, 9, 8.
        val even = Progression.next(plan, listOf(session(80.0, 8, 8, 8)), machine)
        assertEquals(listOf(9, 8, 8), even.sets.map { it?.reps })
        assertEquals(ProgressionReason.KeepGoing(kg(80.0), listOf(8, 8, 8), set = 1), even.reason)
        assertEquals(
            listOf(9, 9, 8),
            Progression.next(plan, listOf(session(80.0, 9, 8, 8)), machine).sets.map { it?.reps },
        )
        // A set at the top stays there while another catches up.
        assertEquals(
            listOf(12, 11, 9),
            Progression.next(plan, listOf(session(80.0, 12, 11, 8)), machine).sets.map { it?.reps },
        )
    }

    @Test
    fun theWeakestSetsNumberIsTheOneOnItsBadge() {
        // Warm-ups have no number, so the third set here is set 2.
        val planned = listOf(RoutineSet("w", type = SetType.Warmup, reps = RepRange(10))) + plan.take(2)
        val done = listOf(set(40.0, 10, SetType.Warmup), set(80.0, 10), set(80.0, 9))
        assertEquals(
            ProgressionReason.KeepGoing(kg(80.0), listOf(10, 9), set = 2),
            Progression.next(planned, listOf(done), machine).reason,
        )
        // A failure set has none.
        val failing = plan.take(1) + RoutineSet("f", type = SetType.Failure, reps = RepRange(8, 12))
        val tried = listOf(set(80.0, 10), set(80.0, 8, SetType.Failure))
        assertEquals(
            ProgressionReason.KeepGoing(kg(80.0), listOf(10, 8), set = null),
            Progression.next(failing, listOf(tried), machine).reason,
        )
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
        val holding = Progression.next(plan, once, machine)
        assertEquals(ProgressionReason.KeepGoing(kg(85.0), listOf(9, 7, 6), set = 3), holding.reason)
        // Sets below the range aim for its bottom.
        assertEquals(listOf(9, 8, 8), holding.sets.map { it?.reps })
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
    fun repAimsAsChosenInSettings() {
        fun aims(
            aim: RepAims,
            vararg reps: Int,
        ) = Progression
            .next(plan, listOf(session(80.0, *reps)), machine, ProgressionSettings(repAims = aim))
            .sets
            .map { it?.reps }

        assertEquals(listOf(11, 9, 8), aims(RepAims.FirstBelowTop, 10, 9, 8))
        assertEquals(listOf(12, 10, 8), aims(RepAims.FirstBelowTop, 12, 9, 8))
        assertEquals(listOf(10, 9, 8), aims(RepAims.SameAsLastTime, 10, 9, 8))
        assertEquals(listOf(11, 10, 9), aims(RepAims.EverySet, 10, 9, 8))
        val first =
            Progression.next(
                plan,
                listOf(session(80.0, 12, 9, 8)),
                machine,
                ProgressionSettings(repAims = RepAims.FirstBelowTop),
            )
        assertEquals(
            ProgressionReason.KeepGoing(kg(80.0), listOf(12, 9, 8), set = 2, aim = RepAims.FirstBelowTop),
            first.reason,
        )
    }

    @Test
    fun stepUpAsChosenInSettings() {
        val total = ProgressionSettings(stepUp = StepUpWhen.TotalReps)
        // 13, 12, 11 adds up to 36, the top of 3 × 8–12.
        val enough = Progression.next(plan, listOf(session(80.0, 13, 12, 11)), machine, total)
        assertEquals(ProgressionReason.TotalReached(36, kg(80.0), kg(85.0)), enough.reason)
        assertTrue(
            Progression
                .next(
                    plan,
                    listOf(session(80.0, 12, 12, 11)),
                    machine,
                    total,
                ).reason is ProgressionReason.KeepGoing,
        )
        // Every set at the top isn't needed when the first one is.
        val first = ProgressionSettings(stepUp = StepUpWhen.FirstSetAtTop)
        val firstUp = Progression.next(plan, listOf(session(80.0, 12, 10, 9)), machine, first)
        assertEquals(ProgressionReason.FirstSetReached(12, kg(80.0), kg(85.0)), firstUp.reason)
        assertEquals(List(3) { SetTarget(kg(85.0), 8) }, firstUp.sets)
    }

    @Test
    fun belowTheRangeAsChosenInSettings() {
        val once = listOf(session(85.0, 9, 7, 6), session(85.0, 10, 9, 8))
        val rightAway = Progression.next(plan, once, machine, ProgressionSettings(belowRange = BelowRange.RightAway))
        assertEquals(ProgressionReason.BelowOnce(8, kg(85.0), kg(80.0)), rightAway.reason)
        val twice = listOf(session(85.0, 9, 7, 6), session(85.0, 8, 7, 7))
        val never = Progression.next(plan, twice, machine, ProgressionSettings(belowRange = BelowRange.Never))
        assertTrue(never.reason is ProgressionReason.KeepGoing)
        assertEquals(kg(85.0), never.heaviest)
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

    @Test
    fun aStepIsEarnedWhenTheNextStartWouldStepUp() {
        assertEquals(Earned.StepUp(kg(80.0), kg(85.0)), Progression.earned(plan, session(80.0, 12, 12, 12), machine))
        // A rep short, or a set still to go.
        assertNull(Progression.earned(plan, session(80.0, 12, 12, 11), machine))
        assertNull(Progression.earned(plan, session(80.0, 12, 12), machine))
        // Sets not ticked off yet don't count.
        val unfinished = session(80.0, 12, 12) + LoggedSet("s", weight = kg(80.0), reps = 12)
        assertNull(Progression.earned(plan, unfinished, machine))
        // Nothing to step from.
        assertNull(Progression.earned(plan, emptyList(), machine))
    }

    @Test
    fun aStepIsEarnedAsSettingsCountTheTop() {
        val total = ProgressionSettings(stepUp = StepUpWhen.TotalReps)
        assertEquals(
            Earned.StepUp(kg(80.0), kg(85.0)),
            Progression.earned(plan, session(80.0, 13, 12, 11), machine, total),
        )
        // The first set at the top is enough, before the others are done.
        val first = ProgressionSettings(stepUp = StepUpWhen.FirstSetAtTop)
        assertEquals(Earned.StepUp(kg(80.0), kg(85.0)), Progression.earned(plan, session(80.0, 12), machine, first))
    }

    @Test
    fun atTheTopOfTheRackTheTopOfTheStackIsEarned() {
        val rack = { from: Mass, up: Boolean -> LoadRounding.step(from, up, Equipment.Dumbbell, gym) }
        val heaviest = gym.dumbbells.maxOf { it.to.kilograms }

        assertEquals(Earned.TopOfTheStack(kg(heaviest)), Progression.earned(plan, session(heaviest, 12, 12, 12), rack))
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
