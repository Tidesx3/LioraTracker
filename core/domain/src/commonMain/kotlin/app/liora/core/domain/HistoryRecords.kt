package app.liora.core.domain

import app.liora.core.model.FinishedWorkout
import app.liora.core.model.LoggedSet
import app.liora.core.model.TrackingType

/**
 * The personal records each set in history broke when it was logged, the way the logger awarded its
 * trophies: measured against every earlier workout and the sets before it on the day. An exercise's
 * first session is its baseline and breaks none (see [SessionRecords]).
 */
object HistoryRecords {
    /** Record keys by set id, for the sets that broke any. */
    fun of(
        workouts: List<FinishedWorkout>,
        formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
        trackingTypeOf: (exerciseId: String) -> TrackingType,
    ): Map<String, Set<RecordKey>> {
        val broken = mutableMapOf<String, Set<RecordKey>>()
        // One running best per exercise, grown workout by workout: a single pass over history.
        val bests = mutableMapOf<String, MutableMap<RecordKey, PersonalRecord>>()
        for (workout in workouts.sortedBy { it.startedAt }) {
            for ((exerciseId, instances) in workout.exercises.groupBy { it.exerciseId }) {
                val session =
                    Session(trackingTypeOf(exerciseId), formula, isBaseline = exerciseId !in bests)
                val done = instances.flatMap { it.sets }.filter { it.isWorkingSet }.sortedBy { it.completedAt }
                if (done.isNotEmpty()) broken += session.log(done, bests.getOrPut(exerciseId) { mutableMapOf() })
            }
        }
        return broken
    }

    /** One exercise on one day. */
    private class Session(
        val trackingType: TrackingType,
        val formula: OneRepMaxFormula,
        val isBaseline: Boolean,
    ) {
        /** The records [done] broke, set by set, counting each set into [best] as it goes. */
        fun log(
            done: List<LoggedSet>,
            best: MutableMap<RecordKey, PersonalRecord>,
        ): Map<String, Set<RecordKey>> =
            done
                .mapNotNull { set ->
                    val keys =
                        if (isBaseline) {
                            emptySet()
                        } else {
                            PersonalRecords.brokenBy(
                                trackingType,
                                best,
                                set,
                                formula,
                            )
                        }
                    PersonalRecords.compute(trackingType, listOf(set), formula, into = best)
                    keys.takeIf { it.isNotEmpty() }?.let { set.id to it }
                }.toMap()
    }
}
