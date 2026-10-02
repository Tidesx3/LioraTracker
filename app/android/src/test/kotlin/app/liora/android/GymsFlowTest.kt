package app.liora.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.liora.core.data.AppStartup
import app.liora.core.data.gym.GymProfileRepository
import app.liora.core.domain.GymProfiles
import app.liora.core.model.Mass
import app.liora.core.model.WeightUnit
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
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

/** Gyms in Settings: set one up, switch between them, change and delete one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class GymsFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val gyms get() = GlobalContext.get().get<GymProfileRepository>()

    @Before
    fun seed() = runBlocking { GlobalContext.get().get<AppStartup>().run() }

    @After
    fun tearDown() = stopKoin()

    @Test
    @Config(qualifiers = COVER)
    fun setUpAGymAndUseIt() {
        openGyms()
        // No gym yet: weights round to standard equipment.
        waitForText("No gyms yet")
        composeRule.onNodeWithContentDescription("Add gym").performClick()
        waitForText("New gym")
        field("Name").performTextReplacement("Studio Nord")
        field("Stack steps").performScrollTo().performTextReplacement("7")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/gym_editor_dark.png")

        composeRule.onNodeWithText("Save").performClick()
        // Back on the list once the save has landed (the name alone is still in the editor until then).
        waitForText("20 kg bar · 7 plate sizes")
        composeRule.onNodeWithText("Studio Nord").assertIsDisplayed()
        // The new gym is the one in use.
        composeRule.onNodeWithContentDescription("Use Studio Nord").assertIsSelected()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/gyms_dark.png")

        val saved = runBlocking { gyms.active.first() }
        assertEquals("Studio Nord", saved.name)
        assertEquals(Mass(7.0), saved.stackStep)
    }

    @Test
    @Config(qualifiers = COVER)
    fun aNewGymInPoundsStartsWithPoundEquipment() {
        openGyms()
        composeRule.onNodeWithContentDescription("Add gym").performClick()
        waitForText("New gym")
        field("Name").performTextReplacement("Garage")
        composeRule.onNodeWithText("lb").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasSetTextAction() and hasText("Barbell") and hasText("45")) }
        composeRule.onNodeWithText("Save").performClick()
        waitForText("45 lb bar · 6 plate sizes")

        val saved = runBlocking { gyms.active.first() }
        assertEquals(WeightUnit.Pound, saved.unit)
        assertEquals(45.0, saved.barbell.inUnit(WeightUnit.Pound), 1e-9)
    }

    @Test
    @Config(qualifiers = COVER)
    fun switchAndDeleteGyms() {
        val home = saveGym("Home")
        saveGym("Studio Nord")
        openGyms()
        waitForText("Home")

        composeRule.onNodeWithContentDescription("Use Home").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasContentDescription("Use Home") and isSelected()) }
        assertEquals(home, runBlocking { gyms.active.first().id })

        composeRule.onNodeWithText("Home").performClick()
        waitForText("Edit gym")
        composeRule.onNodeWithText("Delete gym").performScrollTo().performClick()
        waitForText("Delete Home?")
        composeRule.onNodeWithText("Delete").performClick()
        // Back on the list without it; the other gym takes over.
        waitForText("Gyms")
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText("Home").fetchSemanticsNodes().isEmpty() }
        assertEquals("Studio Nord", runBlocking { gyms.active.first().name })
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen() {
        saveGym("Studio Nord")
        openGyms()
        waitForText("Studio Nord")
        composeRule.onNodeWithText("Studio Nord").performClick()
        waitForText("Edit gym")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_gym_editor_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun german() {
        saveGym("Studio Nord")
        openGyms(settings = "Einstellungen", row = "Studio")
        waitForText("Studio Nord")
        composeRule.onNodeWithText("Stange 20 kg · 7 Scheibengrößen").assertIsDisplayed()
        composeRule.onNodeWithText("Studio Nord").performClick()
        waitForText("Studio bearbeiten")
        composeRule.onNodeWithText("Langhantelstange").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_gym_editor_dark.png")
    }

    private fun saveGym(name: String): String =
        runBlocking { gyms.save(GymProfiles.standard(WeightUnit.Kilogram, name).copy(id = "")) }

    private fun openGyms(
        settings: String = "Settings",
        row: String = "Gym",
    ) {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(settings).performClick()
        waitForText(row)
        composeRule.onNodeWithText(row).performClick()
    }

    private fun field(label: String) = composeRule.onNode(hasSetTextAction() and hasText(label))

    private fun nodeExists(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val TIMEOUT_MS = 10_000L
    }
}
