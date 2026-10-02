package app.liora.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.liora.core.data.AppStartup
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.domain.OneRepMaxFormula
import app.liora.core.domain.Settings
import app.liora.core.domain.ThemeMode
import app.liora.core.model.DistanceUnit
import app.liora.core.model.LengthUnit
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/** Settings: every choice shows what it's set to, and a change is saved and used right away. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class SettingsFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val settings get() = GlobalContext.get().get<SettingsRepository>()

    @Before
    fun seed() = runBlocking { GlobalContext.get().get<AppStartup>().run() }

    @After
    fun tearDown() = stopKoin()

    @Test
    @Config(qualifiers = COVER)
    fun choicesAreSavedAndShown() {
        openSettings()
        composeRule.onNodeWithText("Epley").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/settings_dark.png")

        composeRule.onNodeWithText("Estimated 1RM").performClick()
        composeRule.onNodeWithText("Brzycki").performClick()
        waitForText("Brzycki")

        composeRule.onNodeWithText("Stall alert after").performClick()
        composeRule.onNodeWithText("4 weeks without a new best").performClick()
        waitForText("4 weeks without a new best")

        composeRule.onNodeWithText("Rest between sets").performClick()
        composeRule.onNodeWithText("2:30").performClick()
        waitForText("2:30")

        // The light theme applies at once, everywhere.
        composeRule.onNodeWithText("Theme").performClick()
        composeRule.onNodeWithText("Light").performClick()
        waitForText("Light")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/settings_light.png")

        val saved = runBlocking { settings.current() }
        assertEquals(OneRepMaxFormula.Brzycki, saved.oneRepMaxFormula)
        assertEquals(28.days, saved.stallWindow)
        assertEquals(150.seconds, saved.rest.working)
        assertEquals(ThemeMode.Light, saved.theme)
    }

    @Test
    @Config(qualifiers = COVER)
    fun unitsAndTheRpeColumn() {
        openSettings()
        composeRule.onNodeWithText("Kilograms (kg)").assertIsDisplayed()

        composeRule.onNodeWithText("Weight").performClick()
        composeRule.onNodeWithText("Pounds (lb)").performClick()
        waitForSaved { it.units.weight == WeightUnit.Pound }
        composeRule.onNodeWithText("Distance").performClick()
        composeRule.onNodeWithText("Miles and yards").performClick()
        waitForSaved { it.units.distance == DistanceUnit.Mile }
        composeRule.onNodeWithText("Body measurements").performClick()
        composeRule.onNodeWithText("Inches (in)").performClick()
        waitForSaved { it.units.bodyLength == LengthUnit.Inch }
        composeRule.onNodeWithText("RPE column").performClick()
        waitForSaved { it.rpe }

        composeRule.onNodeWithText("Pounds (lb)").assertIsDisplayed()
        composeRule.onNodeWithText("Miles and yards").assertIsDisplayed()
        composeRule.onNodeWithText("Inches (in)").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/settings_units_dark.png")
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen() {
        openSettings()
        composeRule.onNodeWithText("Epley").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_settings_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun german() {
        openSettings("Einstellungen")
        waitForText("Pause zwischen Sätzen")
        composeRule.onNodeWithText("3 Wochen ohne neue Bestleistung").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_settings_dark.png")
    }

    private fun openSettings(label: String = "Settings") {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(label).performClick()
        waitForText("Epley")
    }

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

    /** Waits until the stored settings pass [check]: the screen saves in the background. */
    private fun waitForSaved(check: (Settings) -> Boolean) =
        composeRule.waitUntil(TIMEOUT_MS) { check(runBlocking { settings.current() }) }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val TIMEOUT_MS = 20_000L
    }
}
