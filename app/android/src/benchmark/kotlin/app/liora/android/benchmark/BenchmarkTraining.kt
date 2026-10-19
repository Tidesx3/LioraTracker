package app.liora.android.benchmark

import app.liora.core.common.Hlc
import app.liora.core.common.IdGenerator
import app.liora.core.data.backup.Backup
import app.liora.core.data.backup.BackupExercise
import app.liora.core.data.backup.BackupExerciseSettings
import app.liora.core.data.backup.BackupJson
import app.liora.core.data.backup.BackupMeasurement
import app.liora.core.data.backup.BackupPreference
import app.liora.core.data.backup.BackupRoutine
import app.liora.core.data.backup.BackupRoutineExercise
import app.liora.core.data.backup.BackupRoutineFolder
import app.liora.core.data.backup.BackupRoutineSet
import app.liora.core.data.backup.BackupWorkout
import app.liora.core.data.backup.BackupWorkoutExercise
import app.liora.core.data.backup.BackupWorkoutSet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A year of made-up training for the builds that measure speed, as a backup to restore. It's the training
 * Liora is made for first: a push/pull/legs rotation on machines and cables four days a week, a bike warm-up
 * first, 3 sets of 8–12 climbing by double progression, now and then a week with only two days, two weeks at
 * a holiday gym on variations of its own, core and a run once a month, and a weigh-in every few days. Random,
 * but the same every time (one seed), and dated back from [today], so this week, the streak and the monthly
 * report are live.
 */
internal class BenchmarkTraining(
    private val today: LocalDate,
    private val zone: ZoneId,
) {
    private val random = Random(SEED)
    private val clock = PinnedClock()
    private val ids = IdGenerator(clock, random)
    private val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS)
    private val createdAt = millis(firstMonday.minusDays(1), LocalTime.NOON)

    /** Where each lift stands, carried from session to session. */
    private val lifts = mutableMapOf<String, LiftState>()

    fun backup(): Backup {
        val ppl = folder("Push Pull Legs", position = 0, rotation = true)
        val holiday = folder("Holiday gym", position = 1, rotation = false)
        val legPress = variation("Leg Press (Holiday gym)", LEG_PRESS, listOf("quadriceps"), listOf("glutes"))
        val chestPress = variation("Chest Press (Holiday gym)", CHEST_PRESS, listOf("chest"), listOf("triceps"))
        val rotation =
            listOf(
                routine(PUSH_ROUTINE_ID, ppl.id, 0, push),
                routine(id(createdAt), ppl.id, 1, pull),
                routine(id(createdAt), ppl.id, 2, legs),
            )
        val away =
            listOf(
                routine(id(createdAt), holiday.id, 0, upper(chestPress.id)),
                routine(id(createdAt), holiday.id, 1, lower(legPress.id)),
            )
        val core = routine(id(createdAt), folderId = null, position = 0, coreAndRun)
        return Backup(
            format = BackupJson.FORMAT,
            version = BackupJson.VERSION,
            exportedAt = millis(today, LocalTime.MIDNIGHT),
            exercises = listOf(legPress, chestPress),
            exerciseSettings = exerciseSettings(),
            routineFolders = listOf(ppl, holiday),
            routines = (rotation + away + core).map { it.row },
            workouts = schedule(rotation, away, core),
            measurements = measurements(),
            preferences = listOf(preference(WEEKLY_GOALS, "$firstMonday:$GOAL")),
        )
    }

    private fun schedule(
        rotation: List<PlannedRoutine>,
        away: List<PlannedRoutine>,
        core: PlannedRoutine,
    ): List<BackupWorkout> {
        val workouts = mutableListOf<BackupWorkout>()
        var turn = 0
        var awayTurn = 0
        for (week in 0..WEEKS.toInt()) {
            val monday = firstMonday.plusWeeks(week.toLong())
            val days = if (week % BUSY_WEEK_EVERY == BUSY_WEEK_EVERY - 1) BUSY_WEEK_DAYS else TRAINING_DAYS
            for (day in days.map { monday.with(TemporalAdjusters.nextOrSame(it)) }) {
                if (!day.isBefore(today)) continue
                val routine =
                    if (week in HOLIDAY_WEEKS) away[awayTurn++ % away.size] else rotation[turn++ % rotation.size]
                workouts += workout(routine, day)
            }
            val sunday = monday.plusDays(6)
            if (week % CORE_EVERY == 0 && sunday.isBefore(today)) workouts += workout(core, sunday)
        }
        return workouts
    }

    private fun workout(
        routine: PlannedRoutine,
        day: LocalDate,
    ): BackupWorkout {
        val startedAt = millis(day, START) + random.nextLong(START_SPREAD_MS)
        var time = startedAt

        fun after(seconds: Int): Long {
            time += seconds * MS + random.nextLong(JITTER_MS)
            return time
        }
        val exercises = mutableListOf<BackupWorkoutExercise>()
        if (routine.plan.bike) {
            val distance = BIKE_M + random.nextInt(BIKE_SPREAD_M)
            exercises += workoutExercise(BIKE, 0, listOf(set(0, WARMUP, after(BIKE_SEC), BIKE_SEC, distance)))
        }
        for (lift in routine.plan.lifts) {
            exercises += workoutExercise(lift.exerciseId, exercises.size, session(lift, ::after))
        }
        return BackupWorkout(
            id = id(startedAt),
            name = routine.plan.name,
            startedAt = startedAt,
            endedAt = after(COOL_DOWN_SEC),
            routineId = routine.row.id,
            createdAt = startedAt,
            hlc = stamp(startedAt),
            exercises = exercises,
        )
    }

    /** One lift's sets today, [after] the time each takes; double progression moves it on afterwards. */
    private fun session(
        lift: Lift,
        after: (seconds: Int) -> Long,
    ): List<BackupWorkoutSet> {
        if (lift.run) {
            val seconds = RUN_SEC + random.nextInt(RUN_SPREAD_SEC)
            return listOf(set(0, NORMAL, after(seconds), seconds, RUN_M + random.nextInt(RUN_SPREAD_M)))
        }
        val state = lifts.getOrPut(lift.exerciseId) { LiftState(lift.startKg, lift.reps) }
        val kg = state.kg.takeIf { lift.weighted }
        val sets = mutableListOf<BackupWorkoutSet>()
        if (lift.warmup) {
            sets += set(0, WARMUP, after(SET_SEC), kg = kg?.let { warmupKg(it, lift.stepKg) }, reps = WARMUP_REPS)
        }
        // Now and then a day off form: the last set falls short, and the lift stays where it was.
        val offDay = random.nextInt(OFF_DAY_ODDS) == 0
        state.reps.forEachIndexed { index, reps ->
            val short = if (offDay && index == state.reps.lastIndex) OFF_DAY_REPS else 0
            sets += set(sets.size, NORMAL, after(SET_SEC), kg = kg, reps = reps - short, range = lift.reps)
        }
        if (!offDay) state.advance(lift.stepKg)
        return sets
    }

    private fun measurements(): List<BackupMeasurement> {
        val days = ChronoUnit.DAYS.between(firstMonday, today).toInt()
        val measurements = mutableListOf<BackupMeasurement>()

        fun take(
            every: () -> Int,
            type: String,
            value: (progress: Double) -> Double,
        ) {
            var day = 0
            while (day < days) {
                val at = millis(firstMonday.plusDays(day.toLong()), WEIGH_IN)
                measurements += measurement(at, type, value(day.toDouble() / days))
                day += every()
            }
        }
        take({ if (random.nextBoolean()) 2 else 3 }, "bodyweight") {
            tenths(BODYWEIGHT_KG - BODYWEIGHT_LOST_KG * it + random.nextDouble(-NOISE_KG, NOISE_KG))
        }
        take({ 7 }, "body_fat") { tenths(BODY_FAT - BODY_FAT_LOST * it) }
        take({ 14 }, "waist") { (WAIST_CM - WAIST_LOST_CM * it).roundToInt() / CM_PER_M }
        return measurements
    }

    private fun exerciseSettings() =
        listOf(
            BackupExerciseSettings(
                exerciseId = LEG_PRESS,
                stickyNote = "Seat at 5, feet high on the plate",
                createdAt = createdAt,
                hlc = stamp(createdAt),
            ),
            // A cable stack with small steps of its own.
            BackupExerciseSettings(
                exerciseId = CABLE_LATERAL_RAISE,
                loadStepKg = SMALL_STEP_KG,
                createdAt = createdAt,
                hlc = stamp(createdAt),
            ),
        )

    private fun folder(
        name: String,
        position: Int,
        rotation: Boolean,
    ) = BackupRoutineFolder(
        id = id(createdAt),
        name = name,
        position = position,
        rotation = rotation,
        createdAt = createdAt,
        hlc = stamp(createdAt),
    )

    private fun variation(
        name: String,
        of: String,
        primary: List<String>,
        secondary: List<String>,
    ) = BackupExercise(
        id = id(createdAt),
        name = name,
        trackingType = "weight_reps",
        equipment = "machine",
        category = "strength",
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        variationOf = of,
        createdAt = createdAt,
        hlc = stamp(createdAt),
    )

    private fun routine(
        id: String,
        folderId: String?,
        position: Int,
        plan: Plan,
    ): PlannedRoutine {
        val exercises = mutableListOf<BackupRoutineExercise>()
        if (plan.bike) {
            val bike = routineSet(0, WARMUP, durationSec = BIKE_SEC)
            exercises += routineExercise(BIKE, 0, progression = null, sets = listOf(bike))
        }
        for (lift in plan.lifts) {
            val sets = mutableListOf<BackupRoutineSet>()
            if (lift.run) {
                sets += routineSet(0, NORMAL, durationSec = RUN_SEC, distanceM = RUN_M.toDouble())
            } else {
                if (lift.warmup) {
                    val kg = warmupKg(lift.startKg, lift.stepKg)
                    sets += routineSet(0, WARMUP, kg = kg, reps = WARMUP_REPS..WARMUP_REPS)
                }
                repeat(SETS) {
                    sets += routineSet(sets.size, NORMAL, kg = lift.startKg.takeIf { lift.weighted }, reps = lift.reps)
                }
            }
            val progression = DOUBLE_PROGRESSION.takeIf { lift.weighted }
            exercises += routineExercise(lift.exerciseId, exercises.size, progression, sets)
        }
        val row =
            BackupRoutine(
                id = id,
                folderId = folderId,
                name = plan.name,
                position = position,
                createdAt = createdAt,
                hlc = stamp(createdAt),
                exercises = exercises,
            )
        return PlannedRoutine(row, plan)
    }

    private fun routineExercise(
        exerciseId: String,
        position: Int,
        progression: String?,
        sets: List<BackupRoutineSet>,
    ) = BackupRoutineExercise(
        id = id(createdAt),
        exerciseId = exerciseId,
        position = position,
        progression = progression,
        createdAt = createdAt,
        hlc = stamp(createdAt),
        sets = sets,
    )

    private fun routineSet(
        position: Int,
        type: String,
        kg: Double? = null,
        reps: IntRange? = null,
        durationSec: Int? = null,
        distanceM: Double? = null,
    ) = BackupRoutineSet(
        id = id(createdAt),
        position = position,
        setType = type,
        targetWeightKg = kg,
        targetRepsMin = reps?.first,
        targetRepsMax = reps?.last,
        targetDurationSec = durationSec,
        targetDistanceM = distanceM,
        createdAt = createdAt,
        hlc = stamp(createdAt),
    )

    private fun workoutExercise(
        exerciseId: String,
        position: Int,
        sets: List<BackupWorkoutSet>,
    ): BackupWorkoutExercise {
        val at = sets.first().createdAt
        return BackupWorkoutExercise(
            id = id(at),
            exerciseId = exerciseId,
            position = position,
            createdAt = at,
            hlc = stamp(at),
            sets = sets,
        )
    }

    private fun set(
        position: Int,
        type: String,
        at: Long,
        durationSec: Int? = null,
        distanceM: Number? = null,
        kg: Double? = null,
        reps: Int? = null,
        range: IntRange? = null,
    ) = BackupWorkoutSet(
        id = id(at),
        position = position,
        setType = type,
        weightKg = kg,
        reps = reps,
        durationSec = durationSec,
        distanceM = distanceM?.toDouble(),
        completedAt = at,
        targetRepsMin = range?.first,
        targetRepsMax = range?.last,
        createdAt = at,
        hlc = stamp(at),
    )

    private fun measurement(
        at: Long,
        type: String,
        value: Double,
    ) = BackupMeasurement(id = id(at), takenAt = at, type = type, value = value, createdAt = at, hlc = stamp(at))

    private fun preference(
        key: String,
        value: String,
    ) = BackupPreference(key = key, value = value, createdAt = createdAt, hlc = stamp(createdAt))

    /** A UUIDv7 dated [at], so ids sort as the rows were made, like the app's own. */
    private fun id(at: Long): String {
        clock.at = at
        return ids.newId()
    }

    private fun stamp(at: Long) = Hlc(millis = at, counter = 0, node = NODE).encoded

    private fun millis(
        date: LocalDate,
        time: LocalTime,
    ) = date
        .atTime(time)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private class PinnedClock : Clock {
        var at = 0L

        override fun now() = Instant.fromEpochMilliseconds(at)
    }

    private class Lift(
        val exerciseId: String,
        val startKg: Double = 0.0,
        val stepKg: Double = 0.0,
        val warmup: Boolean = false,
        val reps: IntRange = REPS,
        val weighted: Boolean = true,
        val run: Boolean = false,
    )

    private class Plan(
        val name: String,
        val lifts: List<Lift>,
        val bike: Boolean = true,
    )

    private class PlannedRoutine(
        val row: BackupRoutine,
        val plan: Plan,
    )

    /** Double progression: the weakest set gets a rep more each time, and the top of every set earns a step. */
    private class LiftState(
        var kg: Double,
        val range: IntRange,
    ) {
        val reps = IntArray(SETS) { range.first }

        fun advance(stepKg: Double) {
            if (reps.all { it >= range.last }) {
                kg += stepKg
                reps.fill(range.first)
            } else {
                reps[reps.indices.minBy { reps[it] }]++
            }
        }
    }

    private val push =
        Plan(
            "Push",
            listOf(
                Lift(CHEST_PRESS, startKg = 50.0, stepKg = 5.0, warmup = true),
                Lift("fedb.Incline_Dumbbell_Press", startKg = 22.0, stepKg = 2.0),
                Lift("fedb.Butterfly", startKg = 45.0, stepKg = 5.0),
                Lift("fedb.Machine_Shoulder_Military_Press", startKg = 35.0, stepKg = 5.0),
                Lift(CABLE_LATERAL_RAISE, startKg = 7.5, stepKg = SMALL_STEP_KG),
                Lift("fedb.Triceps_Pushdown_-_Rope_Attachment", startKg = 25.0, stepKg = 2.5),
            ),
        )

    private val pull =
        Plan(
            "Pull",
            listOf(
                Lift("fedb.Wide-Grip_Lat_Pulldown", startKg = 55.0, stepKg = 5.0, warmup = true),
                Lift("fedb.Seated_Cable_Rows", startKg = 55.0, stepKg = 5.0),
                Lift("fedb.Reverse_Machine_Flyes", startKg = 35.0, stepKg = 5.0),
                Lift("fedb.Face_Pull", startKg = 20.0, stepKg = 2.5),
                Lift("fedb.Preacher_Curl", startKg = 25.0, stepKg = 2.5),
                Lift("fedb.Incline_Dumbbell_Curl", startKg = 12.0, stepKg = 2.0),
            ),
        )

    private val legs =
        Plan(
            "Legs",
            listOf(
                Lift(LEG_PRESS, startKg = 140.0, stepKg = 10.0, warmup = true),
                Lift("fedb.Hack_Squat", startKg = 80.0, stepKg = 10.0),
                Lift("fedb.Leg_Extensions", startKg = 50.0, stepKg = 5.0),
                Lift("fedb.Seated_Leg_Curl", startKg = 45.0, stepKg = 5.0),
                Lift("liora.hip_thrust_machine", startKg = 80.0, stepKg = 10.0),
                Lift("fedb.Standing_Calf_Raises", startKg = 70.0, stepKg = 10.0),
            ),
        )

    private fun upper(chestPress: String) =
        Plan(
            "Upper",
            listOf(
                Lift(chestPress, startKg = 45.0, stepKg = 5.0, warmup = true),
                Lift("fedb.Wide-Grip_Lat_Pulldown", startKg = 55.0, stepKg = 5.0),
                Lift("fedb.Dumbbell_Shoulder_Press", startKg = 18.0, stepKg = 2.0),
                Lift("fedb.Seated_Cable_Rows", startKg = 55.0, stepKg = 5.0),
                Lift("fedb.Triceps_Pushdown", startKg = 30.0, stepKg = 2.5),
            ),
        )

    private fun lower(legPress: String) =
        Plan(
            "Lower",
            listOf(
                Lift(legPress, startKg = 120.0, stepKg = 10.0, warmup = true),
                Lift("fedb.Lying_Leg_Curls", startKg = 40.0, stepKg = 5.0),
                Lift("fedb.Leg_Extensions", startKg = 50.0, stepKg = 5.0),
                Lift("fedb.Seated_Calf_Raise", startKg = 40.0, stepKg = 5.0),
            ),
        )

    private val coreAndRun =
        Plan(
            "Core and a run",
            listOf(
                Lift("fedb.Running_Treadmill", weighted = false, run = true),
                Lift("fedb.Cable_Crunch", startKg = 30.0, stepKg = 5.0, reps = 12..15),
                Lift("fedb.Crunches", weighted = false, reps = 15..25),
            ),
            bike = false,
        )

    companion object {
        /** The routine the logger journey starts (benchmark/, `Liora.PUSH_ROUTINE_ID` is the same). */
        const val PUSH_ROUTINE_ID = "0192f000-0000-7000-8000-00000000b001"

        private const val SEED = 20_261_019
        private const val NODE = "benchmark"
        private const val WEEKS = 52L
        private const val GOAL = 4
        private val TRAINING_DAYS =
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        private val BUSY_WEEK_DAYS = listOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)
        private const val BUSY_WEEK_EVERY = 9
        private val HOLIDAY_WEEKS = 30..31
        private const val CORE_EVERY = 4

        private const val WEEKLY_GOALS = "goals.weekly"
        private const val DOUBLE_PROGRESSION = "double"
        private const val WARMUP = "warmup"
        private const val NORMAL = "normal"

        private const val BIKE = "fedb.Bicycling_Stationary"
        private const val LEG_PRESS = "fedb.Leg_Press"
        private const val CHEST_PRESS = "fedb.Leverage_Chest_Press"
        private const val CABLE_LATERAL_RAISE = "liora.cable_lateral_raise"
        private const val SMALL_STEP_KG = 1.25

        private const val SETS = 3
        private val REPS = 8..12
        private const val WARMUP_REPS = 12
        private const val OFF_DAY_ODDS = 12
        private const val OFF_DAY_REPS = 2

        private val START: LocalTime = LocalTime.of(17, 30)
        private const val START_SPREAD_MS = 90 * 60_000L
        private const val MS = 1_000L
        private const val JITTER_MS = 40_000L
        private const val SET_SEC = 150
        private const val COOL_DOWN_SEC = 180
        private const val BIKE_SEC = 480
        private const val BIKE_M = 3_000
        private const val BIKE_SPREAD_M = 600
        private const val RUN_SEC = 1_800
        private const val RUN_SPREAD_SEC = 240
        private const val RUN_M = 5_000
        private const val RUN_SPREAD_M = 400

        private val WEIGH_IN: LocalTime = LocalTime.of(7, 15)
        private const val BODYWEIGHT_KG = 84.0
        private const val BODYWEIGHT_LOST_KG = 3.5
        private const val NOISE_KG = 0.4
        private const val BODY_FAT = 19.5
        private const val BODY_FAT_LOST = 3.0
        private const val WAIST_CM = 88.0
        private const val WAIST_LOST_CM = 4.0
        private const val CM_PER_M = 100.0

        /** Half the first working weight, down to a step: one warm-up set before it. */
        private fun warmupKg(
            kg: Double,
            stepKg: Double,
        ) = floor(kg / 2 / stepKg) * stepKg

        private fun tenths(value: Double) = (value * 10).roundToInt() / 10.0
    }
}
