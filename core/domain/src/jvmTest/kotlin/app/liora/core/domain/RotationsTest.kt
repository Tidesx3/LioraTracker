package app.liora.core.domain

import app.liora.core.model.Routine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** A folder's routines taking turns: next up is the one after the routine trained last. */
class RotationsTest {
    private val ppl = listOf(Routine("push", "Push Day"), Routine("pull", "Pull Day"), Routine("legs", "Leg Day"))

    @Test
    fun nextUpIsTheRoutineAfterTheOneTrainedLastRoundToTheFirst() {
        assertEquals("pull", Rotations.turn("ppl", ppl, listOf(fromRoutine("push", day = 3)))?.nextRoutineId)
        assertEquals("push", Rotations.turn("ppl", ppl, listOf(fromRoutine("legs", day = 5)))?.nextRoutineId)
        // Newest first: the latest one decides.
        val week = listOf(fromRoutine("pull", day = 4), fromRoutine("push", day = 2))
        assertEquals(RotationTurn("ppl", "legs", at(4)), Rotations.turn("ppl", ppl, week))
    }

    @Test
    fun skippingADayIsJustStartingAnother() {
        // Push, then Legs instead of Pull: the rotation moves on from Legs.
        val skipped = listOf(fromRoutine("legs", day = 2), fromRoutine("push", day = 1))
        assertEquals("push", Rotations.turn("ppl", ppl, skipped)?.nextRoutineId)
    }

    @Test
    fun beforeAnyTurnItStartsWithTheFirst() {
        val other = listOf(fromRoutine("arms", day = 1), PastWorkout(null, "Upper Pump", at(2)))
        assertEquals(RotationTurn("ppl", "push", null), Rotations.turn("ppl", ppl, other))
        assertNull(Rotations.turn("empty", emptyList(), other))
    }

    @Test
    fun importedHistoryCountsByName() {
        // Hevy's workouts aren't tied to routines; their names say which day it was.
        val imported = listOf(PastWorkout(null, "pull day ", at(6)), fromRoutine("push", day = 5))
        assertEquals("legs", Rotations.turn("ppl", ppl, imported)?.nextRoutineId)
        // A workout started from a routine goes by the routine, whatever it was renamed to.
        val renamed = listOf(PastWorkout("push", "Leg Day", at(7)))
        assertEquals("pull", Rotations.turn("ppl", ppl, renamed)?.nextRoutineId)
    }

    @Test
    fun theRotationTrainedLastComesFirst() {
        val home = RotationTurn("home", "pull", at(3))
        val away = RotationTurn("away", "legs", at(5))
        val new = RotationTurn("new", "a", null)
        assertEquals(listOf(away, home, new), Rotations.order(listOf(new, home, away)))
    }

    private fun fromRoutine(
        id: String,
        day: Int,
    ) = PastWorkout(id, null, at(day))

    private fun at(day: Int) = Instant.parse("2026-10-0${day}T18:00:00Z")
}
