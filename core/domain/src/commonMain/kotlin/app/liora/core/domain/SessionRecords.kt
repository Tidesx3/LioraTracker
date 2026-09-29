package app.liora.core.domain

import app.liora.core.model.LoggedSet
import app.liora.core.model.TrackingType

/** The personal records set during one session, for the trophies in the logger and the finish summary. */
object SessionRecords {
    /**
     * The records each of [today]'s sets broke, by set id: against [history] and against the sets
     * before it today. An exercise's very first session sets no records; it is the baseline.
     */
    fun of(
        trackingType: TrackingType,
        history: List<LoggedSet>,
        today: List<LoggedSet>,
        formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    ): Map<String, Set<RecordKey>> {
        if (history.none { it.isWorkingSet }) return emptyMap()
        val done = today.filter { it.isWorkingSet }.sortedBy { it.completedAt }
        return done
            .withIndex()
            .mapNotNull { (index, set) ->
                val before = PersonalRecords.compute(trackingType, history + done.take(index), formula)
                PersonalRecords.brokenBy(trackingType, before, set, formula).takeIf { it.isNotEmpty() }?.let {
                    set.id to
                        it
                }
            }.toMap()
    }
}
