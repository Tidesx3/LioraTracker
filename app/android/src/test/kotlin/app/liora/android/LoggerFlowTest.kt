package app.liora.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import app.liora.core.data.AppStartup
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.gym.GymProfileRepository
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.data.workout.SetLogger
import app.liora.core.domain.GymProfiles
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.core.model.SetType
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import app.liora.feature.logger.LoggerTags
import app.liora.feature.logger.done.WorkoutDoneTags
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The logger end to end: a routine's sets logged with one tap each from last session's values, the
 * rest timer that follows, typing a set on the number pad (also in pounds, with an RPE), and the two panes on
 * the Fold's inner screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class LoggerFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @get:Rule
    val windowLayout = WindowLayoutInfoPublisherRule()

    @Before
    fun seed() =
        runBlocking {
            GlobalContext.get().get<AppStartup>().run()
            GlobalContext.get().get<RoutineRepository>().save(PUSH)
        }

    @After
    fun tearDown() = stopKoin()

    @Test
    @Config(qualifiers = COVER)
    fun lastSessionsSetsTakeOneTapEach() {
        logLastSession()
        launch()
        waitForText("Start")
        composeRule.onNodeWithText("Start").performClick()
        waitForText("Finish")
        // Last time's sets show in the previous column and as placeholders.
        composeRule.onAllNodesWithText("80 kg × 10").assertCountEquals(3)

        composeRule.onAllNodesWithContentDescription("Log set").onFirst().performClick()
        waitForText("Skip")
        composeRule.onNodeWithText("Rest").assertIsDisplayed()
        composeRule.waitUntil(TIMEOUT_MS) { loggedSets() == 1 }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/logger_resting_dark.png")

        composeRule.onNodeWithText("Skip").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { !nodeExists(hasText("Skip")) }
    }

    @Test
    @Config(qualifiers = COVER)
    fun racingLastTimeScoresEachSetDone() {
        logLastSession()
        launch()
        waitForText("Start")
        composeRule.onNodeWithText("Start").performClick()
        waitForText("Finish")
        // Before the first tick: what there is to beat, so the list doesn't jump when the score starts.
        waitForDescription("3 sets to beat from last time")

        // A rep more than last time on the first set, the second as last time.
        runBlocking {
            val sets = GlobalContext.get().get<SetLogger>()
            val (first, second) = currentSets()
            sets.updateSet(first.id) { copy(reps = 11) }
            sets.completeSet(first.id)
            sets.completeSet(second.id)
        }
        // The pill reads as one sentence; the volume is the first set's extra rep at 80 kg.
        waitForDescription("1 of 3 sets ahead of last time, +80 kg")
        composeRule.onAllNodesWithContentDescription("Ahead of last time").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Same as last time").assertCountEquals(1)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/logger_race_dark.png")
    }

    @Test
    @Config(qualifiers = COVER)
    fun tickingASetPopsTheCheckFillsTheRowAndRollsTheTotals() {
        logLastSession()
        launch()
        waitForText("Start")
        composeRule.onNodeWithText("Start").performClick()
        waitForText("Finish")

        // Frame by frame from here: the clock moves only when the test moves it.
        composeRule.mainClock.autoAdvance = false
        composeRule.onAllNodesWithContentDescription("Log set").onFirst().performClick()
        // The set is written off the main thread; the frame that shows it starts the motion.
        var frames = 0
        while (loggedSets() == 0 && frames++ < MAX_FRAMES) composeRule.mainClock.advanceTimeByFrame()
        assertEquals(1, loggedSets())
        // Early on: the check overshoots, the tint spreads from it, the volume's digits are mid-roll.
        composeRule.mainClock.advanceTimeBy(EARLY_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/motion_tick_early_dark.png")
        composeRule.mainClock.advanceTimeBy(LATE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/motion_tick_late_dark.png")

        composeRule.mainClock.autoAdvance = true
        waitForText("Skip")
        composeRule.onNodeWithText("800 kg").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COVER)
    fun beatLastTimeThenFinishAndUpdateTheRoutine() {
        logLastSession()
        launch()
        waitForText("Start")
        composeRule.onNodeWithText("Start").performClick()
        waitForText("Finish")

        // 85 kg for 10 where last time was 80: a personal record, marked on the set.
        composeRule.onAllNodesWithTag(LoggerTags.CELL)[0].performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.PAD)) }
        listOf("8", "5").forEach { padKey(it).performClick() }
        padButton("Next field").performClick()
        listOf("1", "0").forEach { padKey(it).performClick() }
        // Frame by frame: the trophy pops in and confetti flies from it.
        composeRule.mainClock.autoAdvance = false
        padButton("Log set").performClick()
        var frames = 0
        while (!nodeExists(hasContentDescription("Personal record")) && frames++ < MAX_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.mainClock.advanceTimeBy(BURST_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/motion_record_dark.png")
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasContentDescription("Personal record")) }
        // The second set like last time, the third not at all.
        composeRule.onAllNodesWithContentDescription("Log set").onFirst().performClick()
        composeRule.waitUntil(TIMEOUT_MS) { loggedSets() == 2 }

        composeRule.onNodeWithText("Finish").performClick()
        waitForText("Call it a day?")
        composeRule.onNodeWithText("1 planned set wasn’t logged and won’t be saved.").assertIsDisplayed()
        composeRule.onNodeWithText("Update “Push” with today’s weights and reps").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/logger_finish_dark.png")

        // The records wait for the summary, once the workout is saved.
        composeRule.onNode(hasTestTag(LoggerTags.FINISH_CONFIRM)).performClick()
        waitForText("That’s a wrap")
        composeRule.onNodeWithText("2 workouts in the books").assertIsDisplayed()
        composeRule.onNodeWithText("3 new personal records!").assertIsDisplayed()
        // Each record on a row of its own, with the value it was set at.
        composeRule.onNodeWithText("Heaviest weight").assertIsDisplayed()
        composeRule.onNodeWithText("85 kg").assertIsDisplayed()
        composeRule.onNode(hasTestTag(WorkoutDoneTags.CLOSE)).performClick()
        waitForText("Start empty workout")
        // The set left open was skipped, not deleted, so the plan keeps it.
        val routine = runBlocking { GlobalContext.get().get<RoutineRepository>().get(PUSH.id)!! }
        assertEquals(
            listOf(Mass(85.0), Mass(80.0), Mass(80.0)),
            routine.exercises
                .single()
                .sets
                .map { it.weight },
        )
    }

    @Test
    @Config(qualifiers = COVER)
    fun finishingNamesHowTheRoutineWouldChange() {
        logLastSession()
        val activity = launch()
        waitForText("Start")
        composeRule.onNodeWithText("Start").performClick()
        waitForText("Finish")
        repeat(3) { done ->
            composeRule.onAllNodesWithContentDescription("Log set").onFirst().performClick()
            composeRule.waitUntil(TIMEOUT_MS) { loggedSets() == done + 1 }
        }
        // One more than planned.
        composeRule.onNodeWithText("Add set").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasContentDescription("Log set")) }
        composeRule.onAllNodesWithContentDescription("Log set").onFirst().performClick()
        composeRule.waitUntil(TIMEOUT_MS) { loggedSets() == 4 }

        composeRule.onNodeWithText("Finish").performClick()
        waitForText("Call it a day?")
        composeRule.onNodeWithText("Update “Push” to today’s workout").assertIsDisplayed()
        composeRule.onNodeWithText("Barbell Bench Press: 4 sets, was 3").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/logger_finish_changes_dark.png")

        composeRule.onNode(hasTestTag(LoggerTags.FINISH_CONFIRM)).performClick()
        waitForText("That’s a wrap")
        // Back closes the summary like Done.
        composeRule.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        waitForText("Start empty workout")
        val routine = runBlocking { GlobalContext.get().get<RoutineRepository>().get(PUSH.id)!! }
        assertEquals(
            4,
            routine.exercises
                .single()
                .sets.size,
        )
    }

    @Test
    @Config(qualifiers = TABLETOP_SIZE)
    fun tabletop_theTimerStandsUpAndThePadLiesFlat() {
        logLastSession()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        waitForText("Start")
        composeRule.onNodeWithText("Start").performClick()
        waitForText("Finish")
        // Half fold it once the logger is showing: posture updates reach only screens already listening.
        windowLayout.overrideWindowLayoutInfo(
            TestWindowLayoutInfo(
                listOf(
                    FoldingFeature(
                        activity = activity,
                        state = androidx.window.layout.FoldingFeature.State.HALF_OPENED,
                        orientation = androidx.window.layout.FoldingFeature.Orientation.HORIZONTAL,
                    ),
                ),
            ),
        )
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.TABLETOP)) }
        waitForText("Set 1 of 3")
        composeRule.onNode(hasText("Log set") and !hasAnyAncestor(hasTestTag(LoggerTags.PAD))).performClick()
        waitForText("Skip")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_tabletop_logger_dark.png")
    }

    @Test
    @Config(qualifiers = COVER)
    fun typeASetOnTheNumberPadAndMoveOn() {
        launch()
        composeRule.onNodeWithText("Start empty workout").performClick()
        waitForText("Add exercises")
        composeRule.onNodeWithText("Add exercises").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasSetTextAction()) }
        composeRule.onNode(hasSetTextAction()).performTextInput("bench press")
        waitForText(BENCH)
        composeRule.onAllNodesWithText(BENCH).onFirst().performClick()
        waitForText("Add 1 exercise")
        composeRule.onNodeWithText("Add 1 exercise").performClick()
        waitForText("Add set")

        // Weight, Next, reps, then the tick on the pad logs the set and jumps to the next one.
        composeRule.onAllNodesWithTag(LoggerTags.CELL)[0].performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.PAD)) }
        listOf("8", "2", ".", "5").forEach { padKey(it).performClick() }
        waitForText("82.5")
        padButton("Next field").performClick()
        listOf("1", "0").forEach { padKey(it).performClick() }
        padButton("Log set").performClick()

        composeRule.waitUntil(TIMEOUT_MS) { loggedSets() == 1 }
        waitForText("Skip")
        // The second set's weight is up next, already selected.
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.CELL) and isSelected()) }
        composeRule.onAllNodesWithTag(LoggerTags.CELL)[2].assert(isSelected())
        composeRule.onRoot().captureRoboImage("src/test/screenshots/logger_pad_dark.png")
    }

    @Test
    @Config(qualifiers = COVER)
    fun poundsAndTheRpeColumn() {
        runBlocking {
            GlobalContext.get().get<SettingsRepository>().update {
                it.copy(units = Units(weight = WeightUnit.Pound), rpe = true)
            }
        }
        logLastSession()
        launch()
        waitForText("Start")
        composeRule.onNodeWithText("Start").performClick()
        waitForText("Finish")
        // Last time's 80 kg, in pounds to the tenth, so the column has room for it; the RPE column is
        // empty, never filled in from last time.
        composeRule.onNodeWithText("lb").assertIsDisplayed()
        composeRule.onNodeWithText("RPE").assertIsDisplayed()
        composeRule.onAllNodesWithText("176.4 lb × 10").assertCountEquals(3)

        // 185 lb for 8 at RPE 8.5: typed in pounds, stored in kilograms.
        composeRule.onAllNodesWithTag(LoggerTags.CELL)[0].performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.PAD)) }
        listOf("1", "8", "5").forEach { padKey(it).performClick() }
        padButton("Next field").performClick()
        padKey("8").performClick()
        padButton("Next field").performClick()
        listOf("8", ".", "5").forEach { padKey(it).performClick() }
        padButton("Log set").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { loggedSets() == 1 }

        val logged =
            runBlocking {
                GlobalContext
                    .get()
                    .get<ActiveWorkoutRepository>()
                    .activeWorkout
                    .first()!!
                    .exercises
                    .single()
                    .sets
                    .first()
            }
        assertEquals(185.0, logged.weight!!.inUnit(WeightUnit.Pound), 1e-9)
        assertEquals(8, logged.reps)
        assertEquals(8.5, logged.rpe!!, 0.0)
        waitForText("Skip")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/logger_pounds_rpe_dark.png")
    }

    @Test
    @Config(qualifiers = COVER)
    fun warmupSetsAndThePlatesOnTheBar() {
        logLastSession()
        launch()
        waitForText("Start")
        composeRule.onNodeWithText("Start").performClick()
        waitForText("Finish")

        // A ramp to last time's 80 kg, in what the plates make: the bar, then about 40, 60, 80 and 90 %.
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Add warm-up sets").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { currentSets().size == 8 }
        assertEquals(
            listOf(20.0 to 10, 30.0 to 8, 47.5 to 5, 62.5 to 3, 70.0 to 1),
            currentSets().take(5).map { it.weight!!.kilograms to it.reps },
        )
        assertTrue(currentSets().take(5).all { it.type == SetType.Warmup })

        // The first working set's 80 kg: 25 and 5 each side of a 20 kg bar.
        composeRule.onAllNodesWithTag(LoggerTags.CELL)[WORKING_SET_CELL].performClick()
        waitForPlates("Per side: 25 · 5 kg")
        // 101 kg can't be loaded; the closest below shows with its total.
        listOf("1", "0", "1").forEach { padKey(it).performClick() }
        waitForPlates("Per side: 25 · 15 kg (100 kg in all)")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/logger_plates_dark.png")
    }

    @Test
    @Config(qualifiers = COVER)
    fun switchGymsFromThePad() {
        runBlocking {
            val gyms = GlobalContext.get().get<GymProfileRepository>()
            gyms.save(GymProfiles.standard(WeightUnit.Kilogram, "Studio Nord").copy(id = ""))
            // No 25s at home; the gym saved last is the one in use.
            val standard = GymProfiles.standard(WeightUnit.Kilogram, "Home")
            gyms.save(standard.copy(id = "", plates = standard.plates.filterNot { it.weight == Mass(25.0) }))
        }
        logLastSession()
        launch()
        waitForText("Start")
        composeRule.onNodeWithText("Start").performClick()
        waitForText("Finish")
        composeRule.onAllNodesWithTag(LoggerTags.CELL)[0].performClick()
        waitForPlates("Per side: 20 · 10 kg")

        composeRule.onNodeWithContentDescription("Gym: Home").performClick()
        waitForText("Studio Nord")
        composeRule.onNodeWithText("Studio Nord").performClick()
        waitForPlates("Per side: 25 · 5 kg")
        composeRule.onNodeWithContentDescription("Gym: Studio Nord").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen_theSetUpNextBesideTheWorkout() {
        logLastSession()
        launch()
        waitForText("Start")
        composeRule.onNodeWithText("Start").performClick()
        waitForText("Set 1 of 3")
        // The pad is always there beside the list, and logs the set that's up next.
        composeRule.onNode(hasTestTag(LoggerTags.PAD)).assertIsDisplayed()
        composeRule.onNodeWithText("Last time: 80 kg × 10").assertIsDisplayed()
        composeRule.onNode(hasText("Log set") and !hasAnyAncestor(hasTestTag(LoggerTags.PAD))).performClick()
        waitForText("Set 2 of 3")
        waitForText("Skip")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_logger_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun germanDecimalCommaOnThePad() {
        launch()
        waitForText("Starten")
        composeRule.onNodeWithText("Starten").performClick()
        waitForText("Beenden")
        composeRule.onAllNodesWithTag(LoggerTags.CELL)[0].performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.PAD)) }
        listOf("8", "2", ",", "5").forEach { padKey(it).performClick() }
        waitForText("82,5")
        // The plates follow a moment after the digits.
        waitForPlates("Pro Seite: 25 · 5 · 1,25 kg")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_logger_pad_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun germanRestPillKeepsTheTimeOnOneLine() {
        logLastSession()
        launch()
        waitForText("Starten")
        composeRule.onNodeWithText("Starten").performClick()
        waitForText("Beenden")
        composeRule.onAllNodesWithContentDescription("Satz abhaken").onFirst().performClick()
        waitForText("Überspringen")
        // German's longer buttons leave no room for "Pause" on the cover screen, so the time takes it,
        // on one line: wider than it's tall.
        val time =
            composeRule
                .onNodeWithTag(LoggerTags.REST_TIME)
                .assertTextEquals("1:30")
                .fetchSemanticsNode()
                .size
        assertTrue("the time wraps: $time", time.width > time.height)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_logger_resting_dark.png")
    }

    @Test
    @Config(qualifiers = COVER)
    fun theWorkoutBarOpensTheLoggerAtTheSetUpNext() {
        halfwayThroughLegDay()
        launch()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasContentDescription("Open workout")) }
        composeRule.onNodeWithContentDescription("Open workout").performClick()
        waitForText("Finish")
        // Four exercises done: the list opens at the fifth, not back at the top.
        waitForText("Leg Press")
        composeRule.onNodeWithText("Leg Press").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Barbell Squat").fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(qualifiers = COVER)
    fun turningThePhoneSidewaysKeepsThePlace() {
        halfwayThroughLegDay()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        composeRule.waitForIdle()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasContentDescription("Open workout")) }
        composeRule.onNodeWithContentDescription("Open workout").performClick()
        waitForText("Leg Press")

        // Sideways the cover screen is wide enough for two panes: a different layout, the same place.
        RuntimeEnvironment.setQualifiers("+$COVER_SIDEWAYS")
        activity.configurationChange()
        composeRule.waitForIdle()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.PAD)) }
        assertTrue(composeRule.onAllNodesWithText("Barbell Squat").fetchSemanticsNodes().isEmpty())

        RuntimeEnvironment.setQualifiers("+$COVER")
        activity.configurationChange()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Leg Press").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Barbell Squat").fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(qualifiers = COVER)
    fun theExercisesNoteStaysWithItAndTodaysWithToday() {
        launch()
        waitForText("Start")
        composeRule.onNodeWithText("Start").performClick()
        waitForText("Finish")
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Add exercise note").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasSetTextAction()) }
        composeRule.onNode(hasSetTextAction()).performTextInput("Seat on 4")
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Add note for this workout").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 2 }
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("Only 8 reps today")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/logger_notes_dark.png")

        // Away and back: both come from the database now.
        composeRule.onNodeWithContentDescription("Minimize workout").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasContentDescription("Open workout")) }
        composeRule.onNodeWithContentDescription("Open workout").performClick()
        waitForText("Seat on 4")
        waitForText("Only 8 reps today")

        val koin = GlobalContext.get()
        val bench =
            runBlocking {
                koin
                    .get<ExerciseRepository>()
                    .observeExercise(PUSH.exercises.single().exerciseId, "en")
                    .first()
            }
        assertEquals("Seat on 4", bench?.settings?.stickyNote)
        val today = runBlocking { koin.get<ActiveWorkoutRepository>().activeWorkout.first()!! }
        assertEquals("Only 8 reps today", today.exercises.single().notes)
    }

    /** Leg day in progress: the first four of six exercises done, the fifth (leg press) up next. */
    private fun halfwayThroughLegDay() =
        runBlocking {
            val koin = GlobalContext.get()
            koin.get<RoutineRepository>().save(LEG_DAY)
            val workout = koin.get<ActiveWorkoutRepository>().startFromRoutine(LEG_DAY.id)
            val sets = koin.get<SetLogger>()
            workout.exercises.take(4).flatMap { it.sets }.forEach { set ->
                sets.updateSet(set.id) { copy(weight = Mass(60.0), reps = 8) }
                sets.completeSet(set.id)
            }
        }

    /** A finished Push session: every set 80 kg × 10. */
    private fun logLastSession() =
        runBlocking {
            val koin = GlobalContext.get()
            val workouts = koin.get<ActiveWorkoutRepository>()
            val sets = koin.get<SetLogger>()
            val workout = workouts.startFromRoutine(PUSH.id)
            workout.exercises.single().sets.forEach { set ->
                sets.updateSet(set.id) { copy(reps = 10) }
                sets.completeSet(set.id)
            }
            workouts.finish()
        }

    private fun launch(): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        composeRule.waitForIdle()
        return activity
    }

    private fun padKey(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(LoggerTags.PAD)))

    private fun padButton(description: String): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(description) and hasAnyAncestor(hasTestTag(LoggerTags.PAD)))

    /** How many sets are ticked off: their tick now offers to undo. */
    private fun loggedSets() =
        composeRule.onAllNodesWithContentDescription("Undo logged set").fetchSemanticsNodes().size

    private fun currentSets() =
        runBlocking {
            GlobalContext
                .get()
                .get<ActiveWorkoutRepository>()
                .activeWorkout
                .first()!!
                .exercises
                .single()
                .sets
        }

    private fun waitForPlates(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasTestTag(LoggerTags.PLATES) and hasText(text)) }

    private fun nodeExists(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun waitForDescription(description: String) =
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private companion object {
        /** Points into the motion of ticking a set off, for screenshots, and how long to wait for it to start. */
        const val EARLY_MS = 80L
        const val LATE_MS = 120L
        const val MAX_FRAMES = 200
        const val BURST_MS = 220L

        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"

        /** The cover screen turned sideways. */
        const val COVER_SIDEWAYS = "w960dp-h411dp-land"

        /** The inner screen turned sideways, so the hinge runs across. */
        const val TABLETOP_SIZE = "w1092dp-h984dp-night-xhdpi"
        const val BENCH = "Barbell Bench Press"

        /** Five warm-ups of two cells each come before the first working set. */
        const val WORKING_SET_CELL = 10
        const val TIMEOUT_MS = 10_000L

        val LEG_DAY =
            Routine(
                id = "legs",
                name = "Leg day",
                exercises =
                    listOf(
                        "fedb.Barbell_Squat",
                        "fedb.Barbell_Deadlift",
                        "fedb.Pullups",
                        "fedb.Seated_Cable_Rows",
                        "fedb.Leg_Press",
                        "fedb.Dumbbell_Bicep_Curl",
                    ).map { exerciseId ->
                        RoutineExercise(
                            id = "legs-$exerciseId",
                            exerciseId = exerciseId,
                            sets = List(4) { RoutineSet("legs-$exerciseId-$it", reps = RepRange(8)) },
                        )
                    },
            )

        val PUSH =
            Routine(
                id = "push",
                name = "Push",
                exercises =
                    listOf(
                        RoutineExercise(
                            id = "push-bench",
                            exerciseId = "fedb.Barbell_Bench_Press_-_Medium_Grip",
                            restSeconds = 90,
                            sets =
                                List(
                                    3,
                                ) { RoutineSet("push-bench-$it", weight = Mass(80.0), reps = RepRange(8, 12)) },
                        ),
                    ),
            )
    }
}
