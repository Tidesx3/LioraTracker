package app.liora.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.liora.android.ui.LioraApp
import app.liora.core.designsystem.theme.LioraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app shell (Koin graph from [LioraApplication], real navigation) on the JVM.
 * `./gradlew :app:android:recordRoborazziDebug` refreshes the screenshots in src/test/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
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
        composeRule.onNodeWithText("Finish").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/logger_dark.png")

        composeRule.onNodeWithContentDescription("Minimize workout").performClick()
        composeRule.onNodeWithContentDescription("Open workout").assertIsDisplayed()
        composeRule.onNodeWithText("Resume workout").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/train_active_workout_dark.png")

        composeRule.onNodeWithContentDescription("Open workout").performClick()
        composeRule.onNodeWithText("Finish").performClick()
        composeRule.onNodeWithText("Start empty workout").assertIsDisplayed()
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

    private fun setApp(darkTheme: Boolean) {
        composeRule.setContent {
            LioraTheme(darkTheme = darkTheme) {
                LioraApp()
            }
        }
    }
}
