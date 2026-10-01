package app.liora.core.domain

import app.liora.core.model.ExerciseSession
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Per-session progress values for charts, and stall detection on top of them. */
class ExerciseProgressTest {
    private val now = Instant.parse("2026-10-01T18:00:00Z")

    @Test
    fun weightAndRepsChartTheBestEstimateHeaviestSetVolumeAndReps() {
        val sets =
            listOf(
                set(60.0, 10, SetType.Warmup),
                set(100.0, 5),
                set(90.0, 8),
            )
        val value = { metric: ProgressMetric -> ExerciseProgress.valueOf(TrackingType.WeightReps, sets, metric) }

        // Epley: 100 × (1 + 5/30) ≈ 116.7 beats 90 × (1 + 8/30) = 114. Warm-ups never count.
        assertEquals(116.667, value(ProgressMetric.EstimatedOneRepMax)!!, 0.001)
        assertEquals(100.0, value(ProgressMetric.HeaviestWeight))
        assertEquals(1220.0, value(ProgressMetric.Volume))
        assertEquals(13.0, value(ProgressMetric.TotalReps))
        assertEquals(ProgressMetric.EstimatedOneRepMax, ExerciseProgress.metricsFor(TrackingType.WeightReps).first())
    }

    @Test
    fun otherTrackingTypesChartTheirOwnMeasures() {
        val pullups = listOf(set(reps = 12), set(reps = 9))
        assertEquals(12.0, ExerciseProgress.valueOf(TrackingType.BodyweightReps, pullups, ProgressMetric.MostReps))
        assertEquals(21.0, ExerciseProgress.valueOf(TrackingType.BodyweightReps, pullups, ProgressMetric.TotalReps))

        // Less assistance is better; the least wins.
        val assisted = listOf(set(30.0, 8), set(20.0, 6))
        assertEquals(
            20.0,
            ExerciseProgress.valueOf(TrackingType.AssistedBodyweight, assisted, ProgressMetric.LeastAssistance),
        )

        val planks =
            listOf(
                LoggedSet("p1", duration = 60.seconds, completedAt = now),
                LoggedSet("p2", duration = 90.seconds, completedAt = now),
            )
        assertEquals(90.0, ExerciseProgress.valueOf(TrackingType.Duration, planks, ProgressMetric.LongestDuration))
        assertEquals(150.0, ExerciseProgress.valueOf(TrackingType.Duration, planks, ProgressMetric.TotalDuration))

        // 5 km in 25 minutes: 300 s per km.
        val run = listOf(LoggedSet("r", duration = 25.minutes, distanceMeters = 5_000.0, completedAt = now))
        assertEquals(300.0, ExerciseProgress.valueOf(TrackingType.DistanceDuration, run, ProgressMetric.BestPace))
        // A metric the tracking type doesn't have stays empty.
        assertNull(ExerciseProgress.valueOf(TrackingType.DistanceDuration, run, ProgressMetric.TotalReps))
    }

    @Test
    fun aSeriesGoesOldestFirstAndSkipsSessionsWithoutAValue() {
        val sessions =
            listOf(
                session("w3", daysAgo = 2, set(85.0, 5)),
                session("w1", daysAgo = 9, set(80.0, 5)),
                // Only a warm-up: nothing to plot.
                session("w2", daysAgo = 5, set(40.0, 10, SetType.Warmup)),
            )
        val series = ExerciseProgress.series(TrackingType.WeightReps, sessions, ProgressMetric.HeaviestWeight)
        assertEquals(listOf("w1", "w3"), series.map { it.workoutId })
        assertEquals(listOf(80.0, 85.0), series.map { it.value })
    }

    @Test
    fun noNewBestForThreeWeeksIsAStall() {
        val best = session("best", daysAgo = 30, set(100.0, 5))
        val sessions =
            listOf(
                session("before", daysAgo = 37, set(95.0, 5)),
                best,
                session("since1", daysAgo = 14, set(97.5, 5)),
                // Matching the best isn't beating it.
                session("since2", daysAgo = 3, set(100.0, 5)),
            )
        val stall = Stalls.of(TrackingType.WeightReps, sessions, now)
        assertEquals(ProgressMetric.EstimatedOneRepMax, stall?.metric)
        assertEquals("best", stall?.best?.workoutId)
        assertEquals(best.startedAt, stall?.best?.at)
    }

    @Test
    fun improvingRestingOrJustStartingIsNotAStall() {
        val old = session("old", daysAgo = 40, set(100.0, 5))
        // A new best in the window.
        assertNull(Stalls.of(TrackingType.WeightReps, listOf(old, session("new", daysAgo = 4, set(100.0, 6))), now))
        // Not trained in the window: a break, not a stall.
        assertNull(Stalls.of(TrackingType.WeightReps, listOf(old, session("mid", daysAgo = 25, set(90.0, 5))), now))
        // Everything is recent: nothing to compare with yet.
        assertNull(Stalls.of(TrackingType.WeightReps, listOf(session("a", daysAgo = 10, set(80.0, 5))), now))
        assertNull(Stalls.of(TrackingType.WeightReps, emptyList(), now))
    }

    @Test
    fun withoutAnEstimateTheMainRecordDecides() {
        val sessions =
            listOf(
                session("most", daysAgo = 28, set(reps = 15)),
                session("since", daysAgo = 7, set(reps = 14), set(reps = 14)),
            )
        // 28 reps in total beat 15, but the most in one set didn't move.
        assertEquals(ProgressMetric.MostReps, Stalls.of(TrackingType.BodyweightReps, sessions, now)?.metric)
        // A window reaching back past the best leaves nothing earlier to compare with.
        assertNull(Stalls.of(TrackingType.BodyweightReps, sessions, now, window = 30.days))
    }

    private var nextId = 0

    private fun set(
        kg: Double? = null,
        reps: Int? = null,
        type: SetType = SetType.Normal,
    ) = LoggedSet("s${nextId++}", type, kg?.let(::Mass), reps, completedAt = now)

    private fun session(
        workoutId: String,
        daysAgo: Int,
        vararg sets: LoggedSet,
    ): ExerciseSession {
        val at = now - daysAgo.days
        return ExerciseSession(workoutId, null, at, sets.map { it.copy(completedAt = at) })
    }
}
