package app.liora.core.domain

import app.liora.core.model.ExerciseSession
import app.liora.core.model.LoggedSet
import app.liora.core.model.TrackingType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** What a chart of one exercise shows, one value per session. Only working sets count. */
enum class ProgressMetric(
    /** Pace and assistance improve downwards; everything else upwards. */
    val lowerIsBetter: Boolean = false,
) {
    /** The session's best estimated one-rep max, kg. */
    EstimatedOneRepMax,

    /** The heaviest set, kg. */
    HeaviestWeight,

    /** Load moved over the session, kg (see [volumeOf]). */
    Volume,

    /** The most reps in one set. */
    MostReps,

    /** Reps over the session. */
    TotalReps,

    /** The longest set, seconds. */
    LongestDuration,

    /** Time over the session, seconds. */
    TotalDuration,

    /** The longest set, metres. */
    LongestDistance,

    /** The fastest pace, seconds per kilometre. */
    BestPace(lowerIsBetter = true),

    /** The least assistance a set was done with, kg. */
    LeastAssistance(lowerIsBetter = true),
}

/** A metric's value in one session. */
data class ProgressPoint(
    val at: Instant,
    val value: Double,
    val workoutId: String,
)

/** An exercise's progress over its sessions: the values its charts plot. */
object ExerciseProgress {
    /**
     * The metrics worth charting for [trackingType], the main one first: e1RM for weights and reps,
     * otherwise the record that shows progress best (most reps, longest duration, …).
     */
    fun metricsFor(trackingType: TrackingType): List<ProgressMetric> =
        when (trackingType) {
            TrackingType.WeightReps -> {
                listOf(
                    ProgressMetric.EstimatedOneRepMax,
                    ProgressMetric.HeaviestWeight,
                    ProgressMetric.Volume,
                    ProgressMetric.TotalReps,
                )
            }

            TrackingType.WeightedBodyweight -> {
                listOf(
                    ProgressMetric.HeaviestWeight,
                    ProgressMetric.MostReps,
                    ProgressMetric.Volume,
                    ProgressMetric.TotalReps,
                )
            }

            TrackingType.AssistedBodyweight -> {
                listOf(ProgressMetric.MostReps, ProgressMetric.LeastAssistance, ProgressMetric.TotalReps)
            }

            TrackingType.BodyweightReps -> {
                listOf(ProgressMetric.MostReps, ProgressMetric.TotalReps)
            }

            TrackingType.Duration -> {
                listOf(ProgressMetric.LongestDuration, ProgressMetric.TotalDuration)
            }

            TrackingType.DurationWeight -> {
                listOf(ProgressMetric.LongestDuration, ProgressMetric.HeaviestWeight, ProgressMetric.TotalDuration)
            }

            TrackingType.DistanceDuration -> {
                listOf(ProgressMetric.LongestDistance, ProgressMetric.BestPace, ProgressMetric.LongestDuration)
            }

            TrackingType.WeightDistance -> {
                listOf(ProgressMetric.HeaviestWeight, ProgressMetric.LongestDistance)
            }
        }

    /** [metric] for each of [sessions] that has a value for it, oldest first. */
    fun series(
        trackingType: TrackingType,
        sessions: List<ExerciseSession>,
        metric: ProgressMetric,
        formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    ): List<ProgressPoint> =
        sessions
            .sortedBy { it.startedAt }
            .mapNotNull { session ->
                valueOf(trackingType, session.sets, metric, formula)?.let {
                    ProgressPoint(session.startedAt, it, session.workoutId)
                }
            }

    /** [metric] over [sets], one session's worth; null when none of them has it. */
    fun valueOf(
        trackingType: TrackingType,
        sets: List<LoggedSet>,
        metric: ProgressMetric,
        formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    ): Double? {
        val working = sets.filter { it.isWorkingSet }
        return when (metric) {
            ProgressMetric.Volume -> {
                volumeOf(trackingType, working).takeIf { it > 0.0 }
            }

            ProgressMetric.TotalReps -> {
                working.sumOf { it.reps ?: 0 }.takeIf { trackingType.usesReps && it > 0 }?.toDouble()
            }

            ProgressMetric.TotalDuration -> {
                working
                    .sumOf { it.duration?.inWholeSeconds ?: 0 }
                    .takeIf { trackingType.usesDuration && it > 0 }
                    ?.toDouble()
            }

            else -> {
                // The best single set, measured the way its record is.
                val key = RecordKey(metric.recordType)
                val values = working.mapNotNull { PersonalRecords.valuesOf(trackingType, it, formula)[key] }
                if (metric.lowerIsBetter) values.minOrNull() else values.maxOrNull()
            }
        }
    }

    /** Whether [value] beats [best] in [metric]'s direction. */
    fun improves(
        metric: ProgressMetric,
        value: Double,
        best: Double,
    ): Boolean = if (metric.lowerIsBetter) value < best - EPSILON else value > best + EPSILON

    private val ProgressMetric.recordType: RecordType
        get() =
            when (this) {
                ProgressMetric.EstimatedOneRepMax -> {
                    RecordType.EstimatedOneRepMax
                }

                ProgressMetric.HeaviestWeight -> {
                    RecordType.HeaviestWeight
                }

                ProgressMetric.MostReps -> {
                    RecordType.MostReps
                }

                ProgressMetric.LongestDuration -> {
                    RecordType.LongestDuration
                }

                ProgressMetric.LongestDistance -> {
                    RecordType.LongestDistance
                }

                ProgressMetric.BestPace -> {
                    RecordType.BestPace
                }

                ProgressMetric.LeastAssistance -> {
                    RecordType.LeastAssistance
                }

                ProgressMetric.Volume, ProgressMetric.TotalReps, ProgressMetric.TotalDuration -> {
                    error("$this is a session total, not a set record")
                }
            }

    // Rounding noise in e1RM estimates is not progress.
    private const val EPSILON = 1e-6
}

/** An exercise still trained whose main metric hasn't improved for a while. */
data class Stall(
    val metric: ProgressMetric,
    /** The session that set the best still standing. */
    val best: ProgressPoint,
)

/**
 * Stall detection: an exercise is stalled when its main metric (e1RM, or most reps, longest duration,
 * … without one) hasn't improved for [DEFAULT_WINDOW] or longer. Only exercises trained during that
 * time count, so a break doesn't show up as a stall, and a first session is a baseline, not a stall.
 */
object Stalls {
    val DEFAULT_WINDOW: Duration = 21.days

    fun of(
        trackingType: TrackingType,
        sessions: List<ExerciseSession>,
        now: Instant,
        window: Duration = DEFAULT_WINDOW,
        formula: OneRepMaxFormula = OneRepMaxFormula.Epley,
    ): Stall? {
        val metric = ExerciseProgress.metricsFor(trackingType).first()
        val points = ExerciseProgress.series(trackingType, sessions, metric, formula)
        val since = now - window
        val (recent, earlier) = points.partition { it.at >= since }
        if (recent.isEmpty() || earlier.isEmpty()) return null
        // The earliest session to reach the best keeps it, as with records.
        val best =
            earlier.reduce {
                best,
                point,
                ->
                if (ExerciseProgress.improves(metric, point.value, best.value)) point else best
            }
        val improved = recent.any { ExerciseProgress.improves(metric, it.value, best.value) }
        return if (improved) null else Stall(metric, best)
    }
}
