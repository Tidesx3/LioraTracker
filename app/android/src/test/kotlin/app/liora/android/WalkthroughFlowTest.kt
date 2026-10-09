package app.liora.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import app.liora.android.ui.ShellTags
import app.liora.core.data.AppStartup
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.sync.SyncRepository
import app.liora.core.data.walkthrough.WalkthroughRepository
import app.liora.core.model.DistanceUnit
import app.liora.core.model.LengthUnit
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** The first-launch walkthrough: its pages, the units, the demo set, Google Drive, and seeing it again. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class WalkthroughFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val walkthrough get() = koin.get<WalkthroughRepository>()

    @Before
    fun setUp() =
        runBlocking {
            koin.get<AppStartup>().run()
            // The test app starts past it, as an existing install would.
            walkthrough.showAgain()
        }

    @After
    fun tearDown() = stopKoin()

    @Test
    @Config(qualifiers = COVER)
    fun stepThroughAndFinishOnTheTabs() {
        val controller = launch()
        waitForText("Welcome to Liora")
        // In place of the tabs.
        composeRule.onAllNodesWithTag(ShellTags.NAVIGATION_BAR).assertCountEquals(0)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/walkthrough_welcome_dark.png")

        composeRule.onNodeWithText("Next").performClick()
        waitForText("Plan it once")
        composeRule.onNodeWithContentDescription("Page 2 of 5").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/walkthrough_routines_dark.png")

        // Swiping works as well as Next, and back goes the other way.
        composeRule.onRoot().performTouchInput { swipeLeft() }
        waitForText("One tap per set")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/walkthrough_logging_dark.png")
        composeRule.runOnIdle { controller.get().onBackPressedDispatcher.onBackPressed() }
        waitForText("Plan it once")
        composeRule.onNodeWithText("Next").performClick()
        waitForText("One tap per set")

        // The demo set ticks off for real.
        composeRule.onNodeWithText("57.5 kg × 8").assertExists()
        composeRule.onNodeWithContentDescription("Log set").performClick()
        waitForText("That’s all it takes.")
        composeRule.onNodeWithContentDescription("Undo logged set").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/walkthrough_logging_ticked_dark.png")

        composeRule.onNodeWithText("Next").performClick()
        waitForText("Watch it add up")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/walkthrough_progress_dark.png")
        composeRule.onNodeWithText("Next").performClick()
        waitForText("Keep it safe")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/walkthrough_data_dark.png")

        composeRule.onNodeWithText("Let’s go").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasContentDescription("Settings")) }
        composeRule.onAllNodesWithText("Keep it safe").assertCountEquals(0)
        assertFalse(runBlocking { walkthrough.pending.first() })

        // And it stays done.
        controller.recreate()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasContentDescription("Settings")) }
        composeRule.onAllNodesWithText("Welcome to Liora").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = COVER)
    fun skipThenConnectGoogleDrive() {
        launch()
        waitForText("Welcome to Liora")
        composeRule.onNodeWithText("Skip").performClick()
        waitForText("Keep it safe")
        composeRule.onAllNodesWithText("Skip").assertCountEquals(0)

        // The consent screen answers at once in tests.
        composeRule.onNodeWithText("Connect Google Drive").performClick()
        waitForText("Syncing with $TEST_ACCOUNT")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/walkthrough_data_connected_dark.png")
        assertEquals(
            TEST_ACCOUNT,
            runBlocking {
                koin
                    .get<SyncRepository>()
                    .state
                    .first()
                    .account
            },
        )
    }

    @Test
    @Config(qualifiers = COVER_LIGHT)
    fun poundsCarryIntoTheDemoAndSettings() {
        launch()
        waitForText("Welcome to Liora")
        composeRule.onNodeWithText("Pounds").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasText("Pounds") and isSelected()) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/walkthrough_welcome.png")

        composeRule.onNodeWithText("Next").performClick()
        waitForText("Plan it once")
        composeRule.onNodeWithText("Next").performClick()
        waitForText("130 lb × 8")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/walkthrough_logging.png")
        assertEquals(
            Units(WeightUnit.Pound, DistanceUnit.Mile, LengthUnit.Inch),
            runBlocking { koin.get<SettingsRepository>().current().units },
        )
    }

    @Test
    @Config(qualifiers = COVER)
    fun settingsShowsItAgain() {
        runBlocking { walkthrough.finish() }
        launch()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasScrollToKeyAction()) }
        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("about")
        waitForText("How Liora works")
        composeRule.onNodeWithText("How Liora works").performClick()

        waitForText("Welcome to Liora")
        composeRule.onNodeWithText("Skip").performClick()
        waitForText("Keep it safe")
        composeRule.onNodeWithText("Let’s go").performClick()
        // Back where it was asked for.
        waitForText("How Liora works")
    }

    @Test
    @Config(qualifiers = INNER)
    fun onTheInnerScreen() {
        launch()
        waitForText("Welcome to Liora")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/walkthrough_inner_welcome_dark.png")
        composeRule.onNodeWithText("Next").performClick()
        waitForText("Plan it once")
        composeRule.onNodeWithText("Next").performClick()
        waitForText("One tap per set")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/walkthrough_inner_logging_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun inGerman() {
        launch()
        waitForText("Willkommen bei Liora")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_walkthrough_welcome_dark.png")
        composeRule.onNodeWithText("Überspringen").performClick()
        waitForText("Sicher ist sicher")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_walkthrough_data_dark.png")
    }

    private fun launch(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).setup().also { composeRule.waitForIdle() }

    private fun nodeExists(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) = composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasText(text)) }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val COVER_LIGHT = "w411dp-h960dp-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val TIMEOUT_MS = 10_000L
    }
}
