package app.liora.core.domain

import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.WorkoutExercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Today against last time: set by set as the Previous column pairs them, and what it adds up to. */
class LastTimeComparisonTest {
    @Test
    fun moreRepsOrMoreWeightIsAhead() {
        assertEquals(VersusLastTime.Ahead, weightReps(55.0, 11, against = 55.0 to 10))
        assertEquals(VersusLastTime.Ahead, weightReps(57.5, 10, against = 55.0 to 10))
        assertEquals(VersusLastTime.Level, weightReps(55.0, 10, against = 55.0 to 10))
        assertEquals(VersusLastTime.Behind, weightReps(55.0, 9, against = 55.0 to 10))
        assertEquals(VersusLastTime.Behind, weightReps(50.0, 10, against = 55.0 to 10))
    }

    @Test
    fun heavierForFewerRepsGoesByTheEstimatedMax() {
        // After a step up: 35 × 8 (44.3 kg) beats 30 × 12 (42 kg); 35 × 5 (40.8 kg) doesn't.
        assertEquals(VersusLastTime.Ahead, weightReps(35.0, 8, against = 30.0 to 12))
        assertEquals(VersusLastTime.Behind, weightReps(35.0, 5, against = 30.0 to 12))
        // Past 12 reps there's no estimate: load times reps decides.
        assertEquals(VersusLastTime.Ahead, weightReps(20.0, 15, against = 22.5 to 13))
    }

    @Test
    fun lessAssistanceIsAhead() {
        assertEquals(VersusLastTime.Ahead, compare(TrackingType.AssistedBodyweight, set(20.0, 8), set(25.0, 8)))
        assertEquals(VersusLastTime.Behind, compare(TrackingType.AssistedBodyweight, set(30.0, 8), set(25.0, 8)))
        // Less help for fewer reps: no telling without bodyweight.
        assertEquals(VersusLastTime.Level, compare(TrackingType.AssistedBodyweight, set(20.0, 6), set(25.0, 8)))
    }

    @Test
    fun timeAndDistanceGoByLongerAndFarther() {
        assertEquals(VersusLastTime.Ahead, compare(TrackingType.Duration, timed(70.seconds), timed(60.seconds)))
        assertEquals(VersusLastTime.Behind, compare(TrackingType.Duration, timed(50.seconds), timed(60.seconds)))
        assertEquals(
            VersusLastTime.Ahead,
            compare(TrackingType.DistanceDuration, run(5100.0, 30.minutes), run(5000.0, 30.minutes)),
        )
        // The same distance, quicker.
        assertEquals(
            VersusLastTime.Ahead,
            compare(TrackingType.DistanceDuration, run(5000.0, 28.minutes), run(5000.0, 30.minutes)),
        )
        assertEquals(
            VersusLastTime.Behind,
            compare(TrackingType.DistanceDuration, run(5000.0, 31.minutes), run(5000.0, 30.minutes)),
        )
        assertEquals(VersusLastTime.Ahead, compare(TrackingType.BodyweightReps, set(null, 12), set(null, 10)))
    }

    @Test
    fun theWorkoutAddsUpTheSetsDoneSoFar() {
        val last = listOf(set(40.0, 10, SetType.Warmup), set(55.0, 10), set(55.0, 10), set(55.0, 9))
        val today =
            WorkoutExercise(
                id = "pulldown",
                exerciseId = PULLDOWN,
                sets =
                    listOf(
                        set(40.0, 10, SetType.Warmup, id = "w"),
                        set(55.0, 11, id = "1"),
                        set(55.0, 10, id = "2"),
                        // Not done yet: comparable, but not compared.
                        set(55.0, null, id = "3", done = false),
                        // Nothing to compare with: last time had three.
                        set(55.0, 8, id = "4"),
                    ),
            )

        val race = LastTimeComparison.of(listOf(today), mapOf(PULLDOWN to last), { TrackingType.WeightReps })

        assertEquals(mapOf("1" to VersusLastTime.Ahead, "2" to VersusLastTime.Level), race.sets)
        assertEquals(3, race.comparable)
        assertEquals(1, race.ahead)
        assertEquals(1, race.reps)
        assertEquals(55.0, race.volumeKg!!, 0.001)
    }

    @Test
    fun withoutLastTimeThereIsNothingToRace() {
        val today = WorkoutExercise(id = "plank", exerciseId = "plank", sets = listOf(timed(60.seconds)))

        val race = LastTimeComparison.of(listOf(today), emptyMap(), { TrackingType.Duration })

        assertEquals(0, race.comparable)
        assertNull(race.volumeKg)
    }

    private fun weightReps(
        kilograms: Double,
        reps: Int,
        against: Pair<Double, Int>,
    ) = compare(TrackingType.WeightReps, set(kilograms, reps), set(against.first, against.second))

    private fun compare(
        trackingType: TrackingType,
        set: LoggedSet,
        last: LoggedSet,
    ) = LastTimeComparison.compare(trackingType, set, last)

    private fun set(
        kilograms: Double?,
        reps: Int?,
        type: SetType = SetType.Normal,
        id: String = "s",
        done: Boolean = true,
    ) = LoggedSet(
        id = id,
        type = type,
        weight = kilograms?.let(::Mass),
        reps = reps,
        completedAt = DONE.takeIf { done },
    )

    private fun timed(duration: Duration) = LoggedSet("t", duration = duration, completedAt = DONE)

    private fun run(
        meters: Double,
        duration: Duration,
    ) = LoggedSet("r", distanceMeters = meters, duration = duration, completedAt = DONE)

    private companion object {
        const val PULLDOWN = "fedb.Wide-Grip_Lat_Pulldown"
        val DONE = Instant.parse("2026-10-01T18:00:00Z")
    }
}
