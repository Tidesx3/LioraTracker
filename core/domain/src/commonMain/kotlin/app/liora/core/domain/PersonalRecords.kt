package app.liora.core.domain

import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.TrackingType
import kotlin.time.Instant

enum class RecordType(
    /** Pace and assistance improve downwards; everything else upwards. */
    val lowerIsBetter: Boolean = false,
) {
    HeaviestWeight,
    LeastAssistance(lowerIsBetter = true),
    EstimatedOneRepMax,
    BestSetVolume,

    /** Heaviest weight lifted for at least [RecordKey.reps] reps. */
    RepMax,
    MostReps,
    LongestDuration,
    LongestDistance,

    /** Seconds per kilometre. */
    BestPace(lowerIsBetter = true),
}

data class RecordKey(
    val type: RecordType,
    /** Only for [RecordType.RepMax]: the rep count, 1..[MAX_REPS_FOR_ESTIMATE]. */
    val reps: Int? = null,
)

/** Value units: kg for weights and volume, reps, seconds, metres, seconds per km for pace. */
data class PersonalRecord(
    val key: RecordKey,
    val value: Double,
    val setId: String,
    val achievedAt: Instant,
)

/**
 * Personal records per exercise. Only completed working sets count (warm-ups never set records), and
 * which records exist depends on how the exercise is tracked.
 */
object PersonalRecords {
    /** Every record value [set] achieves on its own. */
    fun valuesOf(
        trackingType: TrackingType,
        set: LoggedSet,
        formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    ): Map<RecordKey, Double> {
        if (!set.isWorkingSet) return emptyMap()
        val measured = Measured.of(set)
        return buildMap {
            when (trackingType) {
                TrackingType.WeightReps -> {
                    weightRepsRecords(measured, formula)
                }

                TrackingType.WeightedBodyweight -> {
                    mostReps(measured)
                    heaviest(measured, whenAlso = measured.reps)
                }

                TrackingType.AssistedBodyweight -> {
                    mostReps(measured)
                    // No assistance at all is the best possible outcome.
                    if (measured.reps != null) put(RecordKey(RecordType.LeastAssistance), measured.kg ?: 0.0)
                }

                TrackingType.BodyweightReps -> {
                    mostReps(measured)
                }

                TrackingType.Duration -> {
                    longestDuration(measured)
                }

                TrackingType.DurationWeight -> {
                    longestDuration(measured)
                    heaviest(measured, whenAlso = measured.seconds)
                }

                TrackingType.DistanceDuration -> {
                    longestDistance(measured)
                    longestDuration(measured)
                    bestPace(measured)
                }

                TrackingType.WeightDistance -> {
                    longestDistance(measured)
                    heaviest(measured, whenAlso = measured.meters)
                }
            }
        }
    }

    private fun MutableMap<RecordKey, Double>.weightRepsRecords(
        measured: Measured,
        formula: OneRepMaxFormula,
    ) {
        val kg = measured.kg ?: return
        val reps = measured.reps ?: return
        put(RecordKey(RecordType.HeaviestWeight), kg)
        put(RecordKey(RecordType.BestSetVolume), kg * reps)
        estimateOneRepMax(Mass(kg), reps, formula)?.let { put(RecordKey(RecordType.EstimatedOneRepMax), it.kilograms) }
        for (n in 1..minOf(reps, MAX_REPS_FOR_ESTIMATE)) put(RecordKey(RecordType.RepMax, n), kg)
    }

    private fun MutableMap<RecordKey, Double>.mostReps(measured: Measured) {
        measured.reps?.let { put(RecordKey(RecordType.MostReps), it.toDouble()) }
    }

    /** Heaviest weight, only when the set also has its other measure (reps, time or distance). */
    private fun MutableMap<RecordKey, Double>.heaviest(
        measured: Measured,
        whenAlso: Number?,
    ) {
        if (measured.kg != null && whenAlso != null) put(RecordKey(RecordType.HeaviestWeight), measured.kg)
    }

    private fun MutableMap<RecordKey, Double>.longestDuration(measured: Measured) {
        measured.seconds?.let { put(RecordKey(RecordType.LongestDuration), it) }
    }

    private fun MutableMap<RecordKey, Double>.longestDistance(measured: Measured) {
        measured.meters?.let { put(RecordKey(RecordType.LongestDistance), it) }
    }

    private fun MutableMap<RecordKey, Double>.bestPace(measured: Measured) {
        val meters = measured.meters ?: return
        val seconds = measured.seconds ?: return
        if (meters >= MIN_PACE_DISTANCE_M) put(RecordKey(RecordType.BestPace), seconds / (meters / METERS_PER_KM))
    }

    /** A set's measurements, with zero or missing values treated as absent. */
    private class Measured(
        val kg: Double?,
        val reps: Int?,
        val seconds: Double?,
        val meters: Double?,
    ) {
        companion object {
            fun of(set: LoggedSet) =
                Measured(
                    kg = set.weight?.kilograms?.takeIf { it > 0.0 },
                    reps = set.reps?.takeIf { it > 0 },
                    seconds =
                        set.duration
                            ?.inWholeSeconds
                            ?.takeIf { it > 0 }
                            ?.toDouble(),
                    meters = set.distanceMeters?.takeIf { it > 0.0 },
                )
        }
    }

    /**
     * Best records over a history of sets. On ties the earliest set keeps the record. With [into], the
     * sets add to a running best instead, for walking a long history in order without starting over
     * for every set; they must come after the ones already counted.
     */
    fun compute(
        trackingType: TrackingType,
        sets: List<LoggedSet>,
        formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
        into: MutableMap<RecordKey, PersonalRecord> = mutableMapOf(),
    ): Map<RecordKey, PersonalRecord> {
        for (set in sets.filter { it.isWorkingSet }.sortedBy { it.completedAt }) {
            for ((key, value) in valuesOf(trackingType, set, formula)) {
                val current = into[key]
                if (current == null || isBetter(key, value, current.value)) {
                    into[key] = PersonalRecord(key, value, set.id, set.completedAt!!)
                }
            }
        }
        return into
    }

    /**
     * Records [set] would break. First-ever values are a baseline, not a record, so an exercise's
     * first session doesn't celebrate every set.
     */
    fun brokenBy(
        trackingType: TrackingType,
        previous: Map<RecordKey, PersonalRecord>,
        set: LoggedSet,
        formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    ): Set<RecordKey> =
        valuesOf(trackingType, set, formula)
            .filter { (key, value) -> previous[key]?.let { isBetter(key, value, it.value) } == true }
            .keys

    private fun isBetter(
        key: RecordKey,
        candidate: Double,
        current: Double,
    ): Boolean = if (key.type.lowerIsBetter) candidate < current - EPSILON else candidate > current + EPSILON

    private const val EPSILON = 1e-9
    private const val METERS_PER_KM = 1_000.0

    /** Shorter efforts make meaningless paces (a 50 m sprint is not a record "pace"). */
    private const val MIN_PACE_DISTANCE_M = 400.0
}
