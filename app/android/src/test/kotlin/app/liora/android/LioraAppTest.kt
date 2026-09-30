package app.liora.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.liora.android.ui.LioraApp
import app.liora.core.data.AppStartup
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.domain.ExerciseSearch
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app (Koin graph, Room, navigation) on the JVM with an in-memory database.
 * `./gradlew :app:android:recordRoborazziDebug` refreshes the screenshots in src/test/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi", application = TestLioraApplication::class)
class LioraAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        // Robolectric creates a fresh Application (and Koin graph) per test.
        stopKoin()
    }

    @Test
    fun trainTab_light() {
        setApp(darkTheme = false)
        composeRule.onNodeWithText("Start empty workout").assertIsDisplayed()
        // The routines arrive from Room a moment later; capture once they have.
        waitForText("No routines yet")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/train_light.png")
    }

    @Test
    fun trainTab_dark() {
        setApp(darkTheme = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/train_dark.png")
    }

    @Test
    fun startingAWorkoutOpensTheLogger_andMinimizingShowsTheMiniBar() {
        setApp(darkTheme = true)

        composeRule.onNodeWithText("Start empty workout").performClick()
        waitForText("Finish")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/logger_dark.png")

        composeRule.onNodeWithContentDescription("Minimize workout").performClick()
        waitForContentDescription("Open workout")
        waitForText("Resume workout")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/train_active_workout_dark.png")

        composeRule.onNodeWithContentDescription("Open workout").performClick()
        waitForText("Finish")
        composeRule.onNodeWithText("Finish").performClick()
        // Nothing was logged, so finishing offers to discard instead.
        waitForText("Nothing logged yet")
        composeRule.onNodeWithText("Discard").performClick()
        waitForText("Start empty workout")
    }

    @Test
    fun tabsAndFullScreenDestinations() {
        setApp(darkTheme = true)

        composeRule.onNodeWithText("Progress").performClick()
        composeRule.onNodeWithText("Nothing to chart yet").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progress_dark.png")

        composeRule.onNodeWithText("Body").performClick()
        composeRule.onNodeWithText("Track your body").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("No workouts yet").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de-w411dp-h891dp-xxhdpi")
    fun germanTrainAndLogger() {
        setApp(darkTheme = true)
        composeRule.onNodeWithText("Leeres Training starten").assertIsDisplayed()
        composeRule.onNodeWithText("Fortschritt").assertIsDisplayed()
        waitForText("Noch keine Routinen")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_train_dark.png")

        composeRule.onNodeWithText("Leeres Training starten").performClick()
        waitForText("Beenden")
        composeRule.onNodeWithText("Training verwerfen").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_logger_dark.png")
    }

    @Test
    fun bundledCatalogIsSeededWithGermanNames() =
        runBlocking {
            val koin = GlobalContext.get()
            koin.get<AppStartup>().run()
            val exercises =
                koin
                    .get<ExerciseRepository>()
                    .observeExercises("de")
                    .first()
                    .associateBy { it.id }

            assertTrue("expected the full catalog, got ${exercises.size}", exercises.size > 700)
            assertEquals("Bankdrücken (Langhantel)", exercises.getValue("fedb.Barbell_Bench_Press_-_Medium_Grip").name)
            assertEquals("Ski-Ergometer", exercises.getValue("liora.ski_erg").name)

            // Search against the real catalog, where many variants compete for the same words.
            val search = ExerciseSearch(exercises.values.toList())

            fun top(query: String) = search.search(query).first().id
            assertEquals("fedb.Barbell_Deadlift", top("Kreuzheben"))
            assertEquals("fedb.Barbell_Deadlift", top("kreuzhebn"))
            assertEquals("fedb.Barbell_Bench_Press_-_Medium_Grip", top("bankdrucken"))
            assertEquals("fedb.Barbell_Squat", top("Kniebeuge"))
            assertEquals("fedb.Pullups", top("Klimmzug"))
            assertEquals("fedb.Wide-Grip_Lat_Pulldown", top("Latzug (Kabel)"))
            assertEquals("fedb.Seated_Leg_Curl", top("Beinbeugen sitzend"))
            assertEquals("fedb.Calf_Press", top("Wadenpressen (Maschine)"))
            assertEquals("liora.ski_erg", top("Ski Erg"))
            assertEquals("fedb.Barbell_Bench_Press_-_Medium_Grip", top("bench"))
        }

    private fun setApp(darkTheme: Boolean) {
        composeRule.setContent {
            LioraTheme(darkTheme = darkTheme) {
                LioraApp()
            }
        }
    }

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForContentDescription(description: String) =
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
