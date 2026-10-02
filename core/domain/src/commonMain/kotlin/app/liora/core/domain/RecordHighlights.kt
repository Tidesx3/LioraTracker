package app.liora.core.domain

import app.liora.core.model.LoggedSet
import app.liora.core.model.TrackingType

/** A record named in a summary, with the value it was set at. */
data class RecordHighlight(
    val key: RecordKey,
    /** In the record's unit (see [PersonalRecord]). */
    val value: Double,
)

/** An exercise's records in a summary: a workout's, the finish sheet's, a month's. */
data class ExerciseRecords(
    val exerciseId: String,
    val records: List<RecordHighlight>,
)

/** Which records a summary names, and at what values. */
object RecordHighlights {
    /**
     * The records worth naming out of [keys], most telling first. A heavier set breaks a whole row of
     * rep maxes at once; they're only named when nothing else was broken, and then just the best one.
     */
    fun headline(keys: Collection<RecordKey>): List<RecordKey> {
        val main = keys.filter { it.type != RecordType.RepMax }.distinctBy { it.type }.sortedBy { it.type.ordinal }
        return main.ifEmpty { listOfNotNull(keys.maxByOrNull { it.reps ?: 0 }) }
    }

    /**
     * The records worth naming that [sets] of one exercise broke, [records] being what each set broke
     * by its id, each at the best value among the sets that broke it.
     */
    fun of(
        sets: List<LoggedSet>,
        records: Map<String, Set<RecordKey>>,
        trackingType: TrackingType,
        formula: OneRepMaxFormula,
    ): List<RecordHighlight> {
        val broken = sets.filter { records[it.id].orEmpty().isNotEmpty() }
        return headline(broken.flatMap { records.getValue(it.id) }).mapNotNull { key ->
            val values =
                broken
                    .filter { key in records.getValue(it.id) }
                    .mapNotNull { PersonalRecords.valuesOf(trackingType, it, formula)[key] }
            val best = if (key.type.lowerIsBetter) values.minOrNull() else values.maxOrNull()
            best?.let { RecordHighlight(key, it) }
        }
    }
}
