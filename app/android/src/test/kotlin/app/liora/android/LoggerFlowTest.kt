package app.liora.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import app.liora.core.data.AppStartup
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
        padButton("Log set").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasContentDescription("Personal record")) }
        // The second set like last time, the third not at all.
        composeRule.onAllNodesWithContentDescription("Log set").onFirst().performClick()
        composeRule.waitUntil(TIMEOUT_MS) { loggedSets() == 2 }

        composeRule.onNodeWithText("Finish").performClick()
        waitForText("Finish workout?")
        composeRule.onNodeWithText("3 personal records").assertIsDisplayed()
        composeRule.onNodeWithText("$BENCH: Heaviest weight, Best estimated 1RM, Best set volume").assertIsDisplayed()
        composeRule.onNodeWithText("1 planned set wasn’t logged and won’t be saved.").assertIsDisplayed()
        composeRule.onNodeWithText("Update “Push” with today’s weights and reps").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/logger_finish_dark.png")

        composeRule.onNode(hasTestTag(LoggerTags.FINISH_CONFIRM)).performClick()
        waitForText("Start empty workout")
        val routine = runBlocking { GlobalContext.get().get<RoutineRepository>().get(PUSH.id)!! }
        assertEquals(
            listOf(Mass(85.0), Mass(80.0)),
            routine.exercises
                .single()
                .sets
                .map { it.weight },
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
        // Last time's 80 kg, in pounds; the RPE column is empty, never filled in from last time.
        composeRule.onNodeWithText("lb").assertIsDisplayed()
        composeRule.onNodeWithText("RPE").assertIsDisplayed()
        composeRule.onAllNodesWithText("176.37 lb × 10").assertCountEquals(3)

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
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_logger_pad_dark.png")
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

    private fun launch() {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        composeRule.waitForIdle()
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

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"

        /** The inner screen turned sideways, so the hinge runs across. */
        const val TABLETOP_SIZE = "w1092dp-h984dp-night-xhdpi"
        const val BENCH = "Barbell Bench Press"

        /** Five warm-ups of two cells each come before the first working set. */
        const val WORKING_SET_CELL = 10
        const val TIMEOUT_MS = 10_000L

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
