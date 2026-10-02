package app.liora.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.liora.core.data.AppStartup
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
import app.liora.core.model.SetType
import app.liora.core.model.WeightUnit
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Routines end to end: building one with the exercise picker, targets and a superset, starting it, and
 * the Train tab's list beside the open routine on the Fold's inner screen. Runs the real MainActivity.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class RoutinesFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun seedCatalog() = runBlocking { GlobalContext.get().get<AppStartup>().run() }

    @After
    fun tearDown() = stopKoin()

    @Test
    @Config(qualifiers = COVER)
    fun buildARoutineWithASupersetAndStartIt() {
        launch()
        composeRule.onNodeWithText("New routine").performClick()
        waitForText("Routine name")
        composeRule.onNode(hasSetTextAction() and hasText("Routine name")).performTextInput("Push day")

        // Pick two exercises; they're added in the order they were tapped.
        composeRule.onNodeWithText("Add exercises").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasText("Add exercises") and !hasSetTextAction()) }
        composeRule.onNode(hasSetTextAction()).performTextInput("bench press")
        waitForText(BENCH)
        composeRule.onAllNodesWithText(BENCH).onFirst().performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("flyes")
        waitForText(FLYES)
        composeRule.onAllNodesWithText(FLYES).onFirst().performClick()
        waitForText("Add 2 exercises")
        composeRule.onNodeWithText("Add 2 exercises").performClick()

        // Back in the editor: targets for the first set, then a superset of the two.
        waitForText(FLYES)
        composeRule.onAllNodesWithText(BENCH).assertCountEquals(1)
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[FIRST_SET_WEIGHT].performTextInput("80")
        fields[FIRST_SET_WEIGHT + 1].performTextInput("8")
        fields[FIRST_SET_WEIGHT + 2].performTextInput("12")
        composeRule.onAllNodesWithContentDescription("More options").onFirst().performClick()
        composeRule.onNodeWithText("Superset with next").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText("Superset").fetchSemanticsNodes().size == 2 }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/routine_editor_dark.png")

        // Saving a new routine opens it, ready to start.
        composeRule.onNodeWithText("Save").performClick()
        waitForText("Start workout")
        composeRule.onNodeWithText("Push day").assertIsDisplayed()
        composeRule.onNodeWithText("2 exercises · 6 sets").assertIsDisplayed()
        composeRule.onNodeWithText("80 kg × 8–12").assertIsDisplayed()
        composeRule.onNodeWithText("Superset").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/routine_detail_dark.png")

        composeRule.onNodeWithText("Start workout").performClick()
        waitForText("Finish")

        // One workout at a time: starting the routine again offers to resume the running one.
        composeRule.onNodeWithContentDescription("Minimize workout").performClick()
        waitForText("Start workout")
        composeRule.onNodeWithText("Start workout").performClick()
        waitForText("Workout in progress")
        composeRule.onNodeWithText("Resume").performClick()
        waitForText("Finish")
    }

    @Test
    @Config(qualifiers = COVER)
    fun coverScreen_foldersAndOneScreenAtATime() {
        seedRoutines()
        launch()
        waitForText(PPL)
        waitForText("Barbell Bench Press, Dumbbell Flyes, Triceps Pushdown")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_cover_train_routines_dark.png")

        composeRule.onNodeWithText("Pull").performClick()
        // Pull-ups are tracked by reps alone.
        waitForText("8 reps")
        // The routine replaces the list, with a way back to it.
        composeRule.onAllNodesWithText(PPL).assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText(PPL)
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen_routinesBesideTheOpenRoutine() {
        seedRoutines()
        launch()
        waitForText("Pick a routine")
        waitForText(PPL)

        composeRule.onNodeWithText("Push").performClick()
        waitForText("Start workout")
        // Both panes: the list stays, the open routine is highlighted, and nothing needs a back arrow.
        composeRule.onNode(isSelected() and hasText("Push")).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        composeRule.onNodeWithText("40 kg × 10").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_train_routine_dark.png")

        // The editor opens in the same pane and closes back to the routine.
        composeRule.onNodeWithContentDescription("Edit").performClick()
        waitForText("Edit routine")
        composeRule.onNodeWithText(PPL).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_routine_editor_dark.png")
        composeRule.onNodeWithContentDescription("Close").performClick()
        waitForText("Start workout")
    }

    @Test
    @Config(qualifiers = COVER)
    fun targetsInPounds() {
        runBlocking {
            GlobalContext.get().get<SettingsRepository>().update {
                it.copy(units = it.units.copy(weight = WeightUnit.Pound))
            }
        }
        seedRoutines()
        launch()
        waitForText(PPL)
        composeRule.onNodeWithText("Push").performClick()
        waitForText("Start workout")
        composeRule.onAllNodesWithText("176.37 lb × 8–12").assertCountEquals(3)

        composeRule.onNodeWithContentDescription("Edit").performClick()
        waitForText("Edit routine")
        composeRule.onAllNodesWithText("lb").onFirst().assertIsDisplayed()
        // The warm-up's 40 kg, retyped in pounds. "95." stays as typed although every keystroke is stored
        // in kilograms and read back.
        composeRule.onNode(hasSetTextAction() and hasText("88.18")).performTextReplacement("95.")
        composeRule.onNode(hasSetTextAction() and hasText("95.")).performTextInput("5")
        composeRule.onNode(hasSetTextAction() and hasText("95.5")).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/routine_editor_pounds_dark.png")

        composeRule.onNodeWithText("Save").performClick()
        waitForText("95.5 lb × 10")
        val warmup = runBlocking { GlobalContext.get().get<RoutineRepository>().get("push")!! }
        assertEquals(
            95.5,
            warmup.exercises
                .first()
                .sets
                .first()
                .weight!!
                .inUnit(WeightUnit.Pound),
            1e-9,
        )
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun germanRoutineDetailAndEditor() {
        seedRoutines()
        launch()
        waitForText("Bankdrücken (Langhantel), Fliegende (Kurzhantel), Trizepsdrücken (Kabelzug)")
        composeRule.onNodeWithText("Push").performClick()
        waitForText("Training starten")
        composeRule.onNodeWithText("3 Übungen · 8 Sätze").assertIsDisplayed()
        composeRule.onNodeWithText("Supersatz").assertIsDisplayed()
        composeRule.onAllNodesWithText("80 kg × 8–12").assertCountEquals(3)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_routine_detail_dark.png")

        composeRule.onNodeWithContentDescription("Bearbeiten").performClick()
        waitForText("Routine bearbeiten")
        // Decimal comma, as typed in German.
        composeRule.onAllNodes(hasSetTextAction() and hasText("22,5")).onFirst().assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_routine_editor_dark.png")
    }

    /** A push/pull split in a folder, with warm-ups, rep ranges, a rest override and a superset. */
    private fun seedRoutines() =
        runBlocking {
            val routines = GlobalContext.get().get<RoutineRepository>()
            val folder = routines.createFolder(PPL)
            routines.save(Routine(id = "push", name = "Push", folderId = folder, exercises = pushExercises()))
            routines.save(Routine(id = "pull", name = "Pull", folderId = folder, exercises = pullExercises()))
        }

    private fun pushExercises() =
        listOf(
            RoutineExercise(
                id = "push-bench",
                exerciseId = "fedb.Barbell_Bench_Press_-_Medium_Grip",
                restSeconds = 150,
                sets =
                    listOf(RoutineSet("push-bench-w", SetType.Warmup, Mass(40.0), RepRange(10))) +
                        sets("push-bench", 3) { copy(weight = Mass(80.0), reps = RepRange(8, 12)) },
            ),
            RoutineExercise(
                id = "push-flyes",
                exerciseId = "fedb.Dumbbell_Flyes",
                supersetGroup = 1,
                sets = sets("push-flyes", 2) { copy(weight = Mass(22.5), reps = RepRange(12)) },
            ),
            RoutineExercise(
                id = "push-pushdown",
                exerciseId = "fedb.Triceps_Pushdown",
                supersetGroup = 1,
                sets = sets("push-pushdown", 2) { copy(weight = Mass(25.0), reps = RepRange(10, 15)) },
            ),
        )

    private fun pullExercises() =
        listOf(
            RoutineExercise(
                id = "pull-pullup",
                exerciseId = "fedb.Pullups",
                sets = sets("pull-pullup", 3) { copy(reps = RepRange(8)) },
            ),
            RoutineExercise(
                id = "pull-row",
                exerciseId = "fedb.Seated_Cable_Rows",
                sets = sets("pull-row", 3) { copy(weight = Mass(60.0), reps = RepRange(10)) },
            ),
        )

    private fun sets(
        idPrefix: String,
        count: Int,
        targets: RoutineSet.() -> RoutineSet,
    ) = List(count) { RoutineSet(id = "$idPrefix-$it").targets() }

    private fun launch(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).setup().also { composeRule.waitForIdle() }

    private fun nodeExists(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val BENCH = "Barbell Bench Press"
        const val FLYES = "Dumbbell Flyes"
        const val PPL = "Push Pull Legs"

        /** Text fields in the editor, top down: name, notes, then each set's targets (kg, reps min, reps max). */
        const val FIRST_SET_WEIGHT = 2
        const val TIMEOUT_MS = 10_000L
    }
}
