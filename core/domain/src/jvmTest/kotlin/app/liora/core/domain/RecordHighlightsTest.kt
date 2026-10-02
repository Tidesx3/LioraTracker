package app.liora.core.domain

import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.TrackingType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** The records a summary names, and the values it names them with. */
class RecordHighlightsTest {
    private val heaviest = RecordKey(RecordType.HeaviestWeight)
    private val volume = RecordKey(RecordType.BestSetVolume)

    @Test
    fun theBestValueAmongTheSetsThatBrokeEachRecord() {
        val sets = listOf(set("a", 80.0, 10), set("b", 85.0, 6), set("c", 70.0, 12))
        val records =
            mapOf(
                "a" to setOf(volume),
                "b" to setOf(heaviest, RecordKey(RecordType.RepMax, 6)),
                "c" to setOf(volume),
            )
        assertEquals(
            listOf(RecordHighlight(heaviest, 85.0), RecordHighlight(volume, 840.0)),
            RecordHighlights.of(sets, records, TrackingType.WeightReps, OneRepMaxFormula.Epley),
        )
    }

    @Test
    fun repMaxesOnlyWhenNothingElseWasBroken() {
        val keys =
            listOf(RecordKey(RecordType.RepMax, 3), RecordKey(RecordType.RepMax, 8), RecordKey(RecordType.RepMax, 5))
        assertEquals(listOf(RecordKey(RecordType.RepMax, 8)), RecordHighlights.headline(keys))
        assertEquals(listOf(heaviest, volume), RecordHighlights.headline(keys + volume + heaviest))
    }

    @Test
    fun lessAssistanceIsBetter() {
        val sets = listOf(set("a", 30.0, 8), set("b", 25.0, 8))
        val assistance = RecordKey(RecordType.LeastAssistance)
        val records = mapOf("a" to setOf(assistance), "b" to setOf(assistance))
        assertEquals(
            listOf(RecordHighlight(assistance, 25.0)),
            RecordHighlights.of(sets, records, TrackingType.AssistedBodyweight, OneRepMaxFormula.Epley),
        )
    }

    private fun set(
        id: String,
        kg: Double,
        reps: Int,
    ) = LoggedSet(id, weight = Mass(kg), reps = reps, completedAt = Instant.parse("2026-10-01T18:00:00Z"))
}
