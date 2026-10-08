package app.liora.android

import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import app.liora.core.data.AppStartup
import app.liora.core.data.body.BodyReading
import app.liora.core.data.health.HealthAccess
import app.liora.core.data.health.HealthAvailability
import app.liora.core.data.health.HealthStore
import app.liora.core.model.MeasurementType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
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
import kotlin.time.Instant

/** Health Connect in Settings: set it up, see what it does, turn it off; and when it isn't on the phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class HealthFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val store get() = koin.get<HealthStore>() as TestHealthStore

    @Before
    fun seed() = runBlocking { koin.get<AppStartup>().run() }

    @After
    fun tearDown() = stopKoin()

    @Test
    @Config(qualifiers = COVER)
    fun setUpHealthConnectAndTurnItOff() {
        store.readings = listOf(BodyReading(MeasurementType.Bodyweight, Instant.parse("2026-10-01T06:30:00Z"), 81.4))
        openHealth()
        waitForText("Get your apps talking")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/health_off_dark.png")

        // The permission screen allows everything at once in tests.
        composeRule.onNodeWithText("Set up Health Connect").performClick()
        waitForText("Saved to Health Connect")
        composeRule.onNodeWithText("Brought in from Health Connect").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/health_on_dark.png")
        // Setting it up traded with Health Connect right away; what came in is HealthRepositoryTest's.
        composeRule.waitUntil(TIMEOUT_MS) { store.readings.isEmpty() }

        composeRule.onNodeWithText("Turn off").performClick()
        waitForText("Turn off Health Connect?")
        composeRule.onAllNodesWithText("Turn off")[1].performClick()
        waitForText("Get your apps talking")
    }

    @Test
    @Config(qualifiers = COVER)
    fun withoutHealthConnectItSaysWhereToGetIt() {
        store.access = HealthAccess(HealthAvailability.NotInstalled)
        openHealth(row = "Health Connect")
        waitForText("Health Connect isn’t here yet")
        composeRule.onNodeWithText("Get Health Connect").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/health_missing_dark.png")
    }

    @Test
    @Config(qualifiers = INNER)
    fun healthConnectOnTheInnerScreen() {
        store.access = TestHealthStore.ALLOWED
        openHealth()
        waitForText("Saved to Health Connect")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/health_on_inner_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun healthConnectInGerman() {
        openHealth(settings = "Einstellungen", section = "Deine Daten")
        waitForText("Deine Apps, per Du")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_health_off_dark.png")
    }

    /** Settings › Your data › Health Connect. */
    private fun openHealth(
        settings: String = "Settings",
        section: String = "Your data",
        row: String = "Health Connect",
    ) {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(settings).performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(hasScrollToKeyAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("data")
        waitForText(section)
        composeRule.onNodeWithText(row).performClick()
    }

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val TIMEOUT_MS = 10_000L
    }
}
