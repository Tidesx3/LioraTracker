package app.liora.core.domain

import app.liora.core.model.ActiveWorkout
import app.liora.core.model.ExerciseSettings
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.WorkoutExercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class WorkoutProgressTest {
    @Test
    fun supersetsRotateSetBySet() {
        // Squat alone, then bench (3 sets) supersetted with rows (2 sets), then curls.
        val workout =
            workout(
                exercise("squat", sets = 2),
                exercise("bench", sets = 3, group = 1),
                exercise("row", sets = 2, group = 1),
                exercise("curl", sets = 1),
            )
        assertEquals(
            listOf(0 to 0, 0 to 1, 1 to 0, 2 to 0, 1 to 1, 2 to 1, 1 to 2, 3 to 0),
            WorkoutOrder.of(workout).map { it.exerciseIndex to it.setIndex },
        )
    }

    @Test
    fun theCurrentSetFollowsTheLastOneTickedOff() {
        val start = workout(exercise("squat", sets = 3), exercise("bench", sets = 2))
        assertEquals(SetRef(0, 0), WorkoutOrder.current(start))

        // Skipping ahead to bench carries on from there, not back at the squat left open.
        val skipped = start.complete(SetRef(1, 0), at = 10)
        assertEquals(SetRef(1, 1), WorkoutOrder.current(skipped))

        // Once bench is done, the open squat sets come back.
        val benchDone = skipped.complete(SetRef(1, 1), at = 20)
        assertEquals(SetRef(0, 0), WorkoutOrder.current(benchDone))

        val allDone = benchDone.complete(SetRef(0, 0), 30).complete(SetRef(0, 1), 40).complete(SetRef(0, 2), 50)
        assertNull(WorkoutOrder.current(allDone))
    }

    @Test
    fun restComesAfterTheRoundOfASuperset() {
        val workout =
            workout(
                exercise("bench", sets = 2, group = 1),
                exercise("row", sets = 2, group = 1),
                exercise("curl", sets = 1),
            )
        assertFalse(WorkoutOrder.restsAfter(workout, SetRef(0, 0)))
        assertTrue(WorkoutOrder.restsAfter(workout, SetRef(1, 0)))
        assertTrue(WorkoutOrder.restsAfter(workout, SetRef(1, 1)))
        assertTrue(WorkoutOrder.restsAfter(workout, SetRef(2, 0)))
    }

    @Test
    fun restUsesTheMostSpecificSetting() {
        val working = LoggedSet("s")
        val warmup = LoggedSet("w", type = SetType.Warmup)
        val plain = WorkoutExercise("we", "bench")
        val settings = ExerciseSettings(restWorkingSeconds = 150, restWarmupSeconds = 45)

        assertEquals(2.minutes, restAfter(working, plain, ExerciseSettings()))
        assertEquals(1.minutes, restAfter(warmup, plain, ExerciseSettings()))
        assertEquals(150.seconds, restAfter(working, plain, settings))
        assertEquals(45.seconds, restAfter(warmup, plain, settings))
        // The routine's rest for this exercise beats the exercise's own, for working sets.
        assertEquals(90.seconds, restAfter(working, plain.copy(restSeconds = 90), settings))
        assertEquals(45.seconds, restAfter(warmup, plain.copy(restSeconds = 90), settings))
        // Zero turns the timer off.
        assertNull(restAfter(working, plain.copy(restSeconds = 0), settings))
        assertNull(restAfter(warmup, plain.copy(restSeconds = 0), settings))
        assertNull(restAfter(working, plain, ExerciseSettings(restWorkingSeconds = 0)))
    }

    @Test
    fun previousValuesMatchWarmupsAndWorkingSetsSeparately() {
        val previous =
            listOf(
                LoggedSet("p1", SetType.Warmup, Mass(40.0), 10),
                LoggedSet("p2", weight = Mass(80.0), reps = 8),
                LoggedSet("p3", weight = Mass(80.0), reps = 7),
            )
        // Today has an extra warm-up: the working sets still line up with last time's.
        val today =
            listOf(
                LoggedSet("t1", SetType.Warmup),
                LoggedSet("t2", SetType.Warmup),
                LoggedSet("t3"),
                LoggedSet("t4"),
                LoggedSet("t5"),
            )
        assertEquals("p1", SetPlaceholders.previousFor(today, 0, previous)?.id)
        assertNull(SetPlaceholders.previousFor(today, 1, previous))
        assertEquals("p2", SetPlaceholders.previousFor(today, 2, previous)?.id)
        assertEquals("p3", SetPlaceholders.previousFor(today, 3, previous)?.id)
        assertNull(SetPlaceholders.previousFor(today, 4, previous))
    }

    @Test
    fun tickingAnEmptySetLogsLastSessionOrTheTarget() {
        val previous = LoggedSet("p", weight = Mass(80.0), reps = 8)
        assertEquals(
            LoggedSet("t", weight = Mass(80.0), reps = 8),
            SetPlaceholders.fill(LoggedSet("t"), previous),
        )
        // Typed values win.
        assertEquals(
            LoggedSet("t", weight = Mass(82.5), reps = 8),
            SetPlaceholders.fill(LoggedSet("t", weight = Mass(82.5)), previous),
        )
        // No last session: the routine's target reps, the low end of a range.
        assertEquals(
            LoggedSet("t", reps = 8, targetReps = RepRange(8, 12)),
            SetPlaceholders.fill(LoggedSet("t", targetReps = RepRange(8, 12)), null),
        )
    }

    @Test
    fun aSetNeedsTheFieldsItsExerciseIsMeasuredBy() {
        assertEquals(
            setOf(SetField.Weight, SetField.Reps),
            SetPlaceholders.missingFields(TrackingType.WeightReps, LoggedSet("s")),
        )
        // Added load is optional for weighted pull-ups; the reps are not.
        assertEquals(
            setOf(SetField.Reps),
            SetPlaceholders.missingFields(TrackingType.WeightedBodyweight, LoggedSet("s")),
        )
        assertEquals(emptySet(), SetPlaceholders.missingFields(TrackingType.BodyweightReps, LoggedSet("s", reps = 12)))
        // A run needs a distance or a time.
        assertEquals(
            setOf(SetField.Distance),
            SetPlaceholders.missingFields(TrackingType.DistanceDuration, LoggedSet("s")),
        )
        assertEquals(
            emptySet(),
            SetPlaceholders.missingFields(TrackingType.DistanceDuration, LoggedSet("s", duration = 20.minutes)),
        )
        assertEquals(setOf(SetField.Duration), SetPlaceholders.missingFields(TrackingType.Duration, LoggedSet("s")))
    }

    @Test
    fun rpeComesLastAndOnlyWhereRepsAreCounted() {
        assertEquals(
            listOf(SetField.Weight, SetField.Reps, SetField.Rpe),
            SetField.of(TrackingType.WeightReps, withRpe = true),
        )
        assertEquals(listOf(SetField.Weight, SetField.Reps), SetField.of(TrackingType.WeightReps))
        assertEquals(
            listOf(SetField.Distance, SetField.Duration),
            SetField.of(TrackingType.DistanceDuration, withRpe = true),
        )
    }

    private fun workout(vararg exercises: WorkoutExercise) =
        ActiveWorkout(
            id = "w",
            name = null,
            startedAt = Instant.fromEpochMilliseconds(0),
            exercises = exercises.toList(),
        )

    private fun exercise(
        id: String,
        sets: Int,
        group: Int? = null,
    ) = WorkoutExercise(
        id = id,
        exerciseId = id,
        supersetGroup = group,
        sets = List(sets) { LoggedSet("$id-$it") },
    )

    private fun ActiveWorkout.complete(
        ref: SetRef,
        at: Long,
    ): ActiveWorkout =
        copy(
            exercises =
                exercises.mapIndexed { index, exercise ->
                    if (index != ref.exerciseIndex) {
                        exercise
                    } else {
                        exercise.copy(
                            sets =
                                exercise.sets.mapIndexed { setIndex, set ->
                                    if (setIndex ==
                                        ref.setIndex
                                    ) {
                                        set.copy(completedAt = Instant.fromEpochMilliseconds(at))
                                    } else {
                                        set
                                    }
                                },
                        )
                    }
                },
        )
}
