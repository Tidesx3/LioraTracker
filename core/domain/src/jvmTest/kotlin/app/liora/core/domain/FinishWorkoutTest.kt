package app.liora.core.domain

import app.liora.core.model.ActiveWorkout
import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import app.liora.core.model.WorkoutExercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** What finishing a workout works out: the records it set, and the routine updated to match it. */
class FinishWorkoutTest {
    @Test
    fun recordsCountAgainstHistoryAndEarlierSetsToday() {
        val history = listOf(done("h1", 80.0, 8, at = 1), done("h2", 80.0, 8, at = 2))
        val today =
            listOf(
                done("t1", 60.0, 10, at = 10, type = SetType.Warmup),
                done("t2", 82.5, 8, at = 11),
                // Same as the set before: equal is not a record.
                done("t3", 82.5, 8, at = 12),
                LoggedSet("t4", weight = Mass(100.0), reps = 8),
            )
        val records = SessionRecords.of(TrackingType.WeightReps, history, today)
        assertEquals(setOf("t2"), records.keys)
        assertTrue(RecordKey(RecordType.HeaviestWeight) in records.getValue("t2"))
        assertTrue(RecordKey(RecordType.EstimatedOneRepMax) in records.getValue("t2"))
    }

    @Test
    fun theFirstSessionIsTheBaseline() {
        val today = listOf(done("t1", 60.0, 8, at = 1), done("t2", 80.0, 8, at = 2))
        assertEquals(emptyMap(), SessionRecords.of(TrackingType.WeightReps, emptyList(), today))
    }

    @Test
    fun theRoutineTakesTodaysWeightsAndKeepsItsRepRanges() {
        val routine = pushRoutine()
        val workout = pushWorkout()
        var next = 0
        val updated = RoutineUpdate.fromWorkout(routine, workout) { "new-${next++}" }

        assertEquals(listOf("bench", "dips"), updated.exercises.map { it.exerciseId })
        val bench = updated.exercises.first()
        // The routine's rows are kept where they still fit, so a sync only carries the change.
        assertEquals("r-bench", bench.id)
        assertEquals(
            listOf(
                RoutineSet("rs1", weight = Mass(82.5), reps = RepRange(8, 12), rpe = 8.0),
                RoutineSet("rs2", weight = Mass(85.0), reps = RepRange(6)),
            ),
            bench.sets,
        )
        assertEquals("new-0", updated.exercises[1].id)
        assertTrue(RoutineUpdate.changes(routine, updated))
        assertFalse(
            RoutineUpdate.changes(updated, updated.copy(exercises = updated.exercises.map { it.copy(id = "other") })),
        )
    }

    /** Push: bench 2 sets (a range, then an exact 5) and flyes. */
    private fun pushRoutine() =
        Routine(
            id = "push",
            name = "Push",
            exercises =
                listOf(
                    RoutineExercise(
                        id = "r-bench",
                        exerciseId = "bench",
                        restSeconds = 120,
                        sets =
                            listOf(
                                RoutineSet("rs1", weight = Mass(80.0), reps = RepRange(8, 12), rpe = 8.0),
                                RoutineSet("rs2", weight = Mass(80.0), reps = RepRange(5)),
                            ),
                    ),
                    RoutineExercise(
                        id = "r-fly",
                        exerciseId = "fly",
                        sets = listOf(RoutineSet("rs3", reps = RepRange(12))),
                    ),
                ),
        )

    /** Push as done today: bench heavier, flyes skipped, dips added, one planned set left open. */
    private fun pushWorkout() =
        ActiveWorkout(
            id = "w",
            name = "Push",
            startedAt = Instant.fromEpochMilliseconds(0),
            routineId = "push",
            exercises =
                listOf(
                    WorkoutExercise(
                        id = "w-bench",
                        exerciseId = "bench",
                        restSeconds = 120,
                        sets =
                            listOf(
                                done("s1", 82.5, 12, at = 1).copy(targetReps = RepRange(8, 12)),
                                done("s2", 85.0, 6, at = 2).copy(targetReps = RepRange(5)),
                                // Planned but not done: not part of the routine any more.
                                LoggedSet("s3", weight = Mass(85.0)),
                            ),
                    ),
                    // Skipped entirely today: it drops out.
                    WorkoutExercise(id = "w-fly", exerciseId = "fly", sets = listOf(LoggedSet("s4"))),
                    // Added on the fly: it joins.
                    WorkoutExercise(id = "w-dips", exerciseId = "dips", sets = listOf(done("s5", 0.0, 10, at = 3))),
                ),
        )

    private fun done(
        id: String,
        kg: Double,
        reps: Int,
        at: Long,
        type: SetType = SetType.Normal,
    ) = LoggedSet(id, type, Mass(kg), reps, completedAt = Instant.fromEpochMilliseconds(at))
}
