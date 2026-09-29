package app.liora.core.domain

import app.liora.core.model.LoggedSet
import app.liora.core.model.Mass
import app.liora.core.model.Muscle
import app.liora.core.model.SetType
import app.liora.core.model.TrackingType
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class OneRepMaxTest {
    @Test
    fun epleyAndBrzyckiMatchTheirFormulas() {
        assertClose(116.67, estimateOneRepMax(Mass(100.0), 5)!!.kilograms)
        assertClose(112.5, estimateOneRepMax(Mass(100.0), 5, OneRepMaxFormula.Brzycki)!!.kilograms)
    }

    @Test
    fun aSingleIsItsOwnMax() {
        assertEquals(Mass(140.0), estimateOneRepMax(Mass(140.0), 1))
    }

    @Test
    fun noEstimateOutsideTheMeaningfulRange() {
        assertNull(estimateOneRepMax(Mass(60.0), 13))
        assertNull(estimateOneRepMax(Mass(60.0), 0))
        assertNull(estimateOneRepMax(Mass(0.0), 5))
    }
}

class PersonalRecordsTest {
    private fun set(
        id: String,
        kg: Double? = null,
        reps: Int? = null,
        at: Long,
        type: SetType = SetType.Normal,
        seconds: Int? = null,
        meters: Double? = null,
        completed: Boolean = true,
    ) = LoggedSet(
        id = id,
        type = type,
        weight = kg?.let(::Mass),
        reps = reps,
        duration = seconds?.seconds,
        distanceMeters = meters,
        completedAt = if (completed) Instant.fromEpochMilliseconds(at) else null,
    )

    @Test
    fun weightRepsRecordsTrackHeaviestE1rmVolumeAndRepMaxes() {
        val history =
            listOf(
                set("a", kg = 100.0, reps = 5, at = 1),
                set("b", kg = 110.0, reps = 1, at = 2),
                set("c", kg = 80.0, reps = 12, at = 3),
                set("w", kg = 140.0, reps = 1, at = 4, type = SetType.Warmup),
                set("x", kg = 150.0, reps = 1, at = 5, completed = false),
            )
        val records = PersonalRecords.compute(TrackingType.WeightReps, history)

        assertEquals("b", records.getValue(RecordKey(RecordType.HeaviestWeight)).setId)
        assertEquals(110.0, records.getValue(RecordKey(RecordType.HeaviestWeight)).value)
        assertEquals("a", records.getValue(RecordKey(RecordType.EstimatedOneRepMax)).setId)
        assertEquals(960.0, records.getValue(RecordKey(RecordType.BestSetVolume)).value)
        assertEquals(100.0, records.getValue(RecordKey(RecordType.RepMax, reps = 5)).value)
        assertEquals(80.0, records.getValue(RecordKey(RecordType.RepMax, reps = 12)).value)
    }

    @Test
    fun equalValueDoesNotStealTheRecordFromTheEarlierSet() {
        val records =
            PersonalRecords.compute(
                TrackingType.WeightReps,
                listOf(set("first", kg = 100.0, reps = 5, at = 1), set("second", kg = 100.0, reps = 5, at = 2)),
            )
        assertEquals("first", records.getValue(RecordKey(RecordType.HeaviestWeight)).setId)
    }

    @Test
    fun brokenRecordsIgnoreFirstEverBaselines() {
        val previous = PersonalRecords.compute(TrackingType.WeightReps, listOf(set("a", kg = 100.0, reps = 5, at = 1)))

        val heavier =
            PersonalRecords.brokenBy(
                TrackingType.WeightReps,
                previous,
                set("b", kg = 105.0, reps = 3, at = 2),
            )
        assertTrue(RecordKey(RecordType.HeaviestWeight) in heavier)
        assertTrue(RecordKey(RecordType.RepMax, 3) in heavier)
        assertTrue(RecordKey(RecordType.RepMax, 6) !in heavier, "no previous 6RM, so no celebration")

        val lighter = PersonalRecords.brokenBy(TrackingType.WeightReps, previous, set("c", kg = 90.0, reps = 5, at = 3))
        assertTrue(lighter.isEmpty())
    }

    @Test
    fun assistedExercisesImproveWithLessAssistance() {
        val records =
            PersonalRecords.compute(
                TrackingType.AssistedBodyweight,
                listOf(set("a", kg = 30.0, reps = 8, at = 1), set("b", kg = 20.0, reps = 6, at = 2)),
            )
        assertEquals("b", records.getValue(RecordKey(RecordType.LeastAssistance)).setId)
        assertEquals("a", records.getValue(RecordKey(RecordType.MostReps)).setId)
    }

    @Test
    fun cardioTracksDistanceDurationAndPace() {
        val records =
            PersonalRecords.compute(
                TrackingType.DistanceDuration,
                listOf(
                    set("5k", seconds = 25.minutes.inWholeSeconds.toInt(), meters = 5_000.0, at = 1),
                    set("10k", seconds = 55.minutes.inWholeSeconds.toInt(), meters = 10_000.0, at = 2),
                    set("sprint", seconds = 10, meters = 100.0, at = 3),
                ),
            )
        assertEquals("10k", records.getValue(RecordKey(RecordType.LongestDistance)).setId)
        assertEquals("5k", records.getValue(RecordKey(RecordType.BestPace)).setId)
        assertEquals(300.0, records.getValue(RecordKey(RecordType.BestPace)).value)
    }
}

class TrainingVolumeTest {
    @Test
    fun volumeCountsCompletedWorkingSetsOnly() {
        val done = Instant.fromEpochMilliseconds(1)
        val sets =
            listOf(
                LoggedSet("w", SetType.Warmup, Mass(60.0), 10, completedAt = done),
                LoggedSet("1", SetType.Normal, Mass(100.0), 5, completedAt = done),
                LoggedSet("2", SetType.Drop, Mass(80.0), 8, completedAt = done),
                LoggedSet("3", SetType.Normal, Mass(100.0), 5),
            )
        assertEquals(1_140.0, volumeOf(TrackingType.WeightReps, sets))
        assertEquals(0.0, volumeOf(TrackingType.AssistedBodyweight, sets))
    }

    @Test
    fun secondaryMusclesCountHalf() {
        val totals =
            setsPerMuscle(
                listOf(
                    MuscleWork(setOf(Muscle.Chest), setOf(Muscle.Triceps, Muscle.Shoulders), workingSets = 4),
                    MuscleWork(setOf(Muscle.Triceps), emptySet(), workingSets = 3),
                ),
            )
        assertEquals(4.0, totals[Muscle.Chest])
        assertEquals(5.0, totals[Muscle.Triceps])
        assertEquals(2.0, totals[Muscle.Shoulders])
    }
}

class PlateCalculatorTest {
    private val standardKg =
        listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25).map { PlateStock(it) }

    @Test
    fun loadsCommonWeightsExactly() {
        val load = PlateCalculator.load(target = 102.5, barWeight = 20.0, plates = standardKg)
        assertEquals(listOf(25.0, 15.0, 1.25), load.perSide)
        assertTrue(load.isExact)
    }

    @Test
    fun comesAsCloseAsPossibleWithoutGoingOver() {
        val load = PlateCalculator.load(target = 101.0, barWeight = 20.0, plates = standardKg)
        assertEquals(100.0, load.total)
        assertTrue(!load.isExact)
    }

    @Test
    fun respectsLimitedInventoryBetterThanGreedy() {
        // Greedy takes the 25 and gets stuck at 25 + 10 = 35 per side; 20 + 15 + 5 reaches 40.
        val plates =
            listOf(
                PlateStock(25.0, 1),
                PlateStock(20.0, 1),
                PlateStock(15.0, 1),
                PlateStock(10.0, 1),
                PlateStock(5.0, 1),
            )
        val load = PlateCalculator.load(target = 100.0, barWeight = 20.0, plates = plates)
        assertEquals(100.0, load.total)
    }

    @Test
    fun targetAtOrBelowTheBarNeedsNoPlates() {
        assertEquals(emptyList(), PlateCalculator.load(20.0, 20.0, standardKg).perSide)
    }
}

class WarmupGeneratorTest {
    @Test
    fun rampsFromTheBarTowardTheWorkingWeight() {
        val sets = WarmupGenerator.generate(workingWeight = 100.0, barWeight = 20.0, increment = 2.5)
        assertEquals(
            listOf(WarmupSet(20.0, 10), WarmupSet(40.0, 8), WarmupSet(60.0, 5), WarmupSet(80.0, 3), WarmupSet(90.0, 1)),
            sets,
        )
    }

    @Test
    fun lightWorkingWeightsGetAShortRampWithoutDuplicates() {
        val sets = WarmupGenerator.generate(workingWeight = 40.0, barWeight = 20.0, increment = 2.5)
        assertEquals(listOf(WarmupSet(20.0, 10), WarmupSet(22.5, 5), WarmupSet(30.0, 3)), sets)
    }

    @Test
    fun nothingToWarmUpForAtBarWeight() {
        assertTrue(WarmupGenerator.generate(20.0, 20.0, 2.5).isEmpty())
    }
}

private fun assertClose(
    expected: Double,
    actual: Double,
) = assertTrue(abs(expected - actual) < 0.01, "expected $expected but was $actual")
