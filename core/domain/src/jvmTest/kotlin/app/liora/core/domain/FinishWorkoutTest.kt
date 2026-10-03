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

        assertEquals(listOf("bench", "fly", "dips"), updated.exercises.map { it.exerciseId })
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
        // Skipped today, so it stays as planned.
        assertEquals(routine.exercises[1], updated.exercises[1])
        assertEquals("new-1", updated.exercises[2].id)
        assertTrue(RoutineUpdate.changes(routine, updated))
        assertFalse(
            RoutineUpdate.changes(updated, updated.copy(exercises = updated.exercises.map { it.copy(id = "other") })),
        )
        assertEquals(listOf(RoutineChange.Added("dips")), RoutineUpdate.changeList(routine, updated))
    }

    @Test
    fun aSkippedSetStaysInThePlanAndADeletedOneLeavesIt() {
        val routine = threeSets()
        val skipped =
            workoutOf(done("a", 80.0, 8, at = 1), done("b", 80.0, 8, at = 2), LoggedSet("c", weight = Mass(80.0)))
        val keepsThree = RoutineUpdate.fromWorkout(routine, skipped) { "new" }
        assertEquals(
            listOf("p1", "p2", "p3"),
            keepsThree.exercises
                .single()
                .sets
                .map { it.id },
        )
        // Done as planned with one skipped: nothing to update.
        assertFalse(RoutineUpdate.changes(routine, keepsThree))

        val deleted = workoutOf(done("a", 80.0, 8, at = 1), done("b", 80.0, 8, at = 2))
        val two = RoutineUpdate.fromWorkout(routine, deleted) { "new" }
        assertEquals(
            listOf("p1", "p2"),
            two.exercises
                .single()
                .sets
                .map { it.id },
        )
        assertEquals(listOf(RoutineChange.SetCount("bench", sets = 2, was = 3)), RoutineUpdate.changeList(routine, two))
    }

    @Test
    fun setsAddedTodayJoinOnlyOnceLogged() {
        val routine = threeSets()
        val warmup = LoggedSet("w", SetType.Warmup, Mass(40.0), 10)
        val sets = (1..3).map { done("s$it", 80.0, 8, at = it.toLong()) }
        // Warm-ups added from the menu go first; left open, they don't take a working set's place.
        val openWarmup = RoutineUpdate.fromWorkout(routine, workoutOf(warmup, *sets.toTypedArray())) { "new" }
        assertEquals(
            listOf("p1", "p2", "p3"),
            openWarmup.exercises
                .single()
                .sets
                .map { it.id },
        )

        val extra = LoggedSet("x", weight = Mass(80.0), reps = 8)
        val openExtra = RoutineUpdate.fromWorkout(routine, workoutOf(*sets.toTypedArray(), extra)) { "new" }
        assertEquals(
            3,
            openExtra.exercises
                .single()
                .sets.size,
        )

        val loggedExtra = workoutOf(*sets.toTypedArray(), done("x", 80.0, 8, at = 4))
        val four = RoutineUpdate.fromWorkout(routine, loggedExtra) { "new" }
        assertEquals(
            listOf(RoutineChange.SetCount("bench", sets = 4, was = 3)),
            RoutineUpdate.changeList(routine, four),
        )
    }

    @Test
    fun changesNameSwappedAddedAndDroppedExercisesAndANewOrder() {
        val routine =
            Routine(
                id = "push",
                name = "Push",
                exercises =
                    listOf("bench", "incline", "fly", "raise").map { id ->
                        RoutineExercise("r-$id", id, sets = listOf(RoutineSet("$id-1", reps = RepRange(10))))
                    },
            )
        val today =
            listOf(
                // Bench swapped for dumbbell bench in its place, fly before incline, raises deleted, dips added.
                WorkoutExercise("w1", "db-bench", sets = listOf(done("1", 30.0, 10, at = 1))),
                WorkoutExercise("w2", "fly", sets = listOf(done("2", 20.0, 10, at = 2))),
                WorkoutExercise(
                    "w3",
                    "incline",
                    sets = listOf(done("3", 60.0, 10, at = 3), done("4", 60.0, 10, at = 4)),
                ),
                WorkoutExercise("w4", "dips", sets = listOf(done("5", 0.0, 10, at = 5))),
            )
        val updated = RoutineUpdate.fromExercises(routine, today) { "new" }
        assertEquals(
            listOf(
                RoutineChange.Replaced(from = "bench", to = "db-bench"),
                RoutineChange.SetCount("incline", sets = 2, was = 1),
                RoutineChange.Added("dips"),
                RoutineChange.Dropped("raise"),
                RoutineChange.Reordered,
            ),
            RoutineUpdate.changeList(routine, updated),
        )
    }

    /** Bench, three sets of 80 kg × 8. */
    private fun threeSets() =
        Routine(
            id = "r",
            name = "Bench",
            exercises =
                listOf(
                    RoutineExercise(
                        id = "r-bench",
                        exerciseId = "bench",
                        sets = (1..3).map { RoutineSet("p$it", weight = Mass(80.0), reps = RepRange(8)) },
                    ),
                ),
        )

    private fun workoutOf(vararg sets: LoggedSet) =
        ActiveWorkout(
            id = "w",
            name = null,
            startedAt = Instant.fromEpochMilliseconds(0),
            routineId = "r",
            exercises = listOf(WorkoutExercise(id = "w-bench", exerciseId = "bench", sets = sets.toList())),
        )

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
                                // Added today and not done: not part of the routine.
                                LoggedSet("s3", weight = Mass(85.0)),
                            ),
                    ),
                    // Skipped entirely today: it stays as planned.
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
