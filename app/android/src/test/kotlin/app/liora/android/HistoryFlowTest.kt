package app.liora.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.liora.core.data.AppStartup
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.SetLogger
import app.liora.core.database.dao.WorkoutDao
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.feature.logger.LoggerTags
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * History end to end: finished workouts by month and on a calendar, a workout with the records it set,
 * and what can be done with one: correct it, save it as a routine, repeat it, delete it. Workouts are seeded at
 * fixed dates in a fixed zone, so the screenshots don't change from day to day.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class HistoryFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val originalZone = java.util.TimeZone.getDefault()

    @Before
    fun seed() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZONE))
        runBlocking {
            koin.get<AppStartup>().run()
            val routines = koin.get<RoutineRepository>()
            routines.save(PUSH)
            routines.save(PULL)
        }
        // A baseline Push, a heavier Push that sets records, a Pull, and a Push from the month before.
        finishedWorkout(PUSH.id, at(2026, 7, 28, 18, 0), benchKg = 77.5)
        finishedWorkout(PUSH.id, at(2026, 8, 4, 17, 30), benchKg = 80.0)
        finishedWorkout(PULL.id, at(2026, 8, 6, 18, 15))
        finishedWorkout(PUSH.id, at(2026, 8, 11, 17, 45), benchKg = 82.5)
    }

    @After
    fun tearDown() {
        stopKoin()
        java.util.TimeZone.setDefault(originalZone)
    }

    @Test
    @Config(qualifiers = COVER)
    fun workoutsByMonthAndTheRecordsTheySet() {
        openHistory()
        waitForText("August 2026")
        composeRule.onNodeWithText("3 workouts").assertIsDisplayed()
        // The time follows the phone's clock setting; the date is what matters here.
        waitForText("Tue, Aug 11 · ")
        composeRule.onAllNodesWithText("3 × $BENCH").onFirst().assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/history_dark.png")

        // The heavier Push: its records, with a trophy on the sets that set them.
        composeRule.onAllNodesWithText("Push").onFirst().performClick()
        waitForText("Repeat workout")
        waitForText("Tuesday, August 11, 2026 · ")
        waitForText("82.5 kg × 10")
        composeRule.onNodeWithText("$BENCH: Heaviest weight, Best estimated 1RM, Best set volume").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/workout_detail_dark.png")

        // What else it offers. The save dialog's name field isn't driven here: under Robolectric, a text
        // field in a dialog keeps Compose from ever going idle (the logger's rename dialog does the same).
        // Saving itself is covered in core/data's WorkoutHistoryTest.
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Save as routine").assertIsDisplayed()
        composeRule.onNodeWithText("Delete workout").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COVER)
    fun calendarOfTrainingDays() {
        openHistory()
        waitForText("August 2026")
        composeRule.onNodeWithContentDescription("Show calendar").performClick()
        // It opens on the current month; go back to the seeded one.
        val today = Clock.System.todayIn(TimeZone.of(ZONE))
        repeat(YearMonth(2026, 8).monthsUntil(today.yearMonth)) {
            composeRule.onNodeWithContentDescription("Previous month").performClick()
        }
        waitForText("August 2026")
        composeRule.onAllNodesWithText("Push").onFirst().assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/history_calendar_dark.png")

        // A training day narrows the list to that day, and back to the whole month.
        composeRule.onNodeWithText("6").performClick()
        waitForText("Thu, Aug 6")
        composeRule.onAllNodesWithText("Push").assertCountEquals(0)
        composeRule.onNodeWithText("Pull").assertIsDisplayed()
        composeRule.onNodeWithText("Whole month").performClick()
        waitForText("Push")

        // July has the first workout; there's nothing before it.
        composeRule.onNodeWithContentDescription("Previous month").performClick()
        waitForText("July 2026")
        composeRule.onAllNodesWithContentDescription("Previous month").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = COVER)
    fun repeatAWorkoutOneAtATime() {
        openHistory()
        waitForText("Pull")
        composeRule.onNodeWithText("Pull").performClick()
        waitForText("Repeat workout")
        composeRule.onNodeWithText("Repeat workout").performClick()
        // The logger opens with the same exercises, still to be done.
        waitForText("Finish")
        waitForText(PULLUPS)

        // Another one can't start while this one runs; resuming goes back to it.
        composeRule.onNodeWithContentDescription("Minimize workout").performClick()
        waitForText("Repeat workout")
        composeRule.onNodeWithText("Repeat workout").performClick()
        waitForText("Workout in progress")
        composeRule.onNodeWithText("Resume").performClick()
        waitForText("Finish")
    }

    @Test
    @Config(qualifiers = COVER)
    fun deleteAWorkout() {
        openHistory()
        waitForText("Pull")
        composeRule.onNodeWithText("Pull").performClick()
        waitForText("Repeat workout")
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Delete workout").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        // Back on the list once it's gone.
        waitForText("2 workouts")
        composeRule.onAllNodesWithText("Pull").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = COVER)
    fun correctAFinishedWorkout() {
        openLatestPush()
        composeRule.onNodeWithContentDescription("Edit workout").performClick()
        waitForText("Done")
        waitForText("Tuesday, August 11, 2026")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/workout_edit_dark.png")

        // The first bench set was heavier than logged.
        composeRule.onAllNodesWithTag(LoggerTags.CELL)[0].performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.PAD)) }
        listOf("8", "5").forEach { padKey(it).performClick() }
        // A set added but never logged doesn't stay.
        composeRule.onAllNodesWithText("Add set").onFirst().performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            nodeExists(hasContentDescription("Log set") and !hasAnyAncestor(hasTestTag(LoggerTags.PAD)))
        }
        composeRule.onNode(hasTestTag(LoggerTags.FINISH)).performClick()
        waitForText("1 set isn’t logged")
        composeRule.onNode(hasTestTag(LoggerTags.CORRECTIONS_CONFIRM)).performClick()

        // Back on the workout, corrected.
        waitForText("Repeat workout")
        waitForText("85 kg × 10")
        composeRule.onAllNodesWithText("82.5 kg × 10").assertCountEquals(2)
    }

    @Test
    @Config(qualifiers = COVER)
    fun moveAWorkoutToAnotherDay() {
        openLatestPush()
        composeRule.onNodeWithContentDescription("Edit workout").performClick()
        waitForText("Done")
        composeRule.onNode(hasTestTag(LoggerTags.DATE)).performClick()
        // The picker's window fills in a moment after it opens; each day is named by its full date.
        val monday = hasText("Monday, August 10, 2026") and hasAnyAncestor(isDialog())
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(monday) }
        composeRule.onNode(monday).performClick()
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Monday, August 10, 2026")

        // Back leaves like Done; nothing was left unlogged, so nothing asks.
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("Repeat workout")
        waitForText("Monday, August 10, 2026 · ")
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen_correctingAWorkoutTakesTheWholeWindow() {
        openHistory()
        waitForText("Pick a workout")
        composeRule.onAllNodesWithText("Push").onFirst().performClick()
        waitForText("Repeat workout")
        composeRule.onNodeWithContentDescription("Edit workout").performClick()
        waitForText("Done")
        composeRule.onAllNodesWithText("Pick a workout").assertCountEquals(0)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_workout_edit_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun germanCorrections() {
        openLatestPush("Verlauf", repeat = "Training wiederholen")
        composeRule.onNodeWithContentDescription("Training bearbeiten").performClick()
        waitForText("Fertig")
        composeRule.onNodeWithText("Dienstag, 11. August 2026").assertExists()
        composeRule.onNodeWithText("Beginn 17:45").assertExists()
        composeRule.onNodeWithText("Ende 18:03").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_workout_edit_dark.png")
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen_listBesideTheOpenWorkout() {
        openHistory()
        waitForText("Pick a workout")
        composeRule.onAllNodesWithText("Push").onFirst().performClick()
        waitForText("Repeat workout")
        // Both panes: the list stays, the open workout is highlighted, and nothing needs a back arrow.
        composeRule.onNode(isSelected() and hasText("Push")).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_history_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun germanDatesAndNumbers() {
        openHistory("Verlauf")
        waitForText("August 2026")
        composeRule.onNodeWithText("3 Trainings").assertIsDisplayed()
        composeRule.onNodeWithText("Di., 11. Aug. · 17:45").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_history_dark.png")

        composeRule.onAllNodesWithText("Push").onFirst().performClick()
        waitForText("Training wiederholen")
        composeRule.onNodeWithText("Dienstag, 11. August 2026 · 17:45").assertExists()
        waitForText("82,5 kg × 10")
    }

    private fun openHistory(tab: String = "History") {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(tab).performClick()
    }

    /** The heavier Push on 11 August, newest of all. */
    private fun openLatestPush(
        tab: String = "History",
        repeat: String = "Repeat workout",
    ) {
        openHistory(tab)
        waitForText("Push")
        composeRule.onAllNodesWithText("Push").onFirst().performClick()
        waitForText(repeat)
    }

    private fun padKey(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(LoggerTags.PAD)))

    private fun nodeExists(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /**
     * A finished workout from [routineId], every set done, then moved to [start]: ten minutes a set.
     * [benchKg] is the bench press weight that day.
     */
    private fun finishedWorkout(
        routineId: String,
        start: kotlin.time.Instant,
        benchKg: Double? = null,
    ) = runBlocking {
        val workouts = koin.get<ActiveWorkoutRepository>()
        val sets = koin.get<SetLogger>()
        val workout = workouts.startFromRoutine(routineId)
        workout.exercises.forEach { exercise ->
            exercise.sets.forEach { set ->
                if (benchKg != null && exercise.exerciseId == BENCH_ID) {
                    sets.updateSet(set.id) { copy(weight = Mass(benchKg)) }
                }
                sets.completeSet(set.id)
            }
        }
        workouts.finish()
        val dao = koin.get<WorkoutDao>()
        val stored = dao.setsOf(workout.id)
        dao.upsertSets(
            stored.mapIndexed { index, set ->
                set.copy(completedAt = (start + SET_MINUTES * (index + 1)).toEpochMilliseconds())
            },
        )
        val row = checkNotNull(dao.get(workout.id))
        dao.upsert(
            row.copy(
                startedAt = start.toEpochMilliseconds(),
                endedAt = (start + SET_MINUTES * (stored.size + 1)).toEpochMilliseconds(),
            ),
        )
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ) = LocalDateTime(year, month, day, hour, minute).toInstant(TimeZone.of(ZONE))

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val ZONE = "Europe/Vienna"
        const val TIMEOUT_MS = 10_000L
        val SET_MINUTES = 3.minutes

        const val BENCH_ID = "fedb.Barbell_Bench_Press_-_Medium_Grip"
        const val BENCH = "Barbell Bench Press"
        const val PULLUPS = "Pull-Up"

        val PUSH =
            Routine(
                id = "push",
                name = "Push",
                exercises =
                    listOf(
                        RoutineExercise(
                            id = "push-bench",
                            exerciseId = BENCH_ID,
                            sets = List(3) { RoutineSet("push-bench-$it", weight = Mass(80.0), reps = RepRange(10)) },
                        ),
                        RoutineExercise(
                            id = "push-flyes",
                            exerciseId = "fedb.Dumbbell_Flyes",
                            sets = List(2) { RoutineSet("push-flyes-$it", weight = Mass(22.5), reps = RepRange(12)) },
                        ),
                    ),
            )

        val PULL =
            Routine(
                id = "pull",
                name = "Pull",
                exercises =
                    listOf(
                        RoutineExercise(
                            id = "pull-pullup",
                            exerciseId = "fedb.Pullups",
                            sets = List(3) { RoutineSet("pull-pullup-$it", reps = RepRange(8)) },
                        ),
                    ),
            )
    }
}
