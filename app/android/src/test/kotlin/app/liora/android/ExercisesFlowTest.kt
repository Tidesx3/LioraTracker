package app.liora.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import app.liora.android.ui.LioraApp
import app.liora.core.data.AppStartup
import app.liora.core.designsystem.theme.LioraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The exercise library, detail and custom-exercise flows on the real catalog, in German. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "de-w411dp-h891dp-xxhdpi", application = TestLioraApplication::class)
class ExercisesFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun seedCatalog() = runBlocking { GlobalContext.get().get<AppStartup>().run() }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun searchWithATypoAndOpenTheExercise() {
        openLibrary()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_exercises_library_dark.png")

        composeRule.onNode(hasSetTextAction()).performTextInput("Kreuzhebn")
        waitForText("Kreuzheben (Langhantel)")
        composeRule.onAllNodesWithText("Kreuzheben (Langhantel)").onFirst().performClick()

        waitForText("Ausführung")
        composeRule.onNodeWithText("Gewicht × Wiederholungen").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Variante erstellen"))
        composeRule.onNodeWithText("Variante erstellen").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToIndex(0)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_exercise_detail_dark.png")
    }

    @Test
    fun createAGymSpecificExerciseFromSearchThenDeleteIt() {
        openLibrary()
        composeRule.onNode(hasSetTextAction()).performTextInput("Beinpresse Internat")
        waitForText("Keine Übung gefunden")
        composeRule.onNodeWithText("„Beinpresse Internat“ anlegen").performClick()

        waitForText("Neue Übung")
        composeRule.onNodeWithText("Maschine").performClick()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_exercise_editor_dark.png")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Quadrizeps"))
        composeRule.onAllNodesWithText("Quadrizeps").onFirst().performClick()
        composeRule.onNodeWithText("Speichern").performClick()

        // Back in the library, the new exercise matches the search that created it.
        waitForText("Eigene")
        composeRule.onNode(hasText("Beinpresse Internat") and !hasSetTextAction()).performClick()
        waitForText("Quadrizeps")

        composeRule.onNodeWithContentDescription("Weitere Optionen").performClick()
        composeRule.onNodeWithText("Löschen").performClick()
        waitForText("Übung löschen?")
        composeRule.onAllNodesWithText("Löschen").onFirst().performClick()
        waitForText("Keine Übung gefunden")
    }

    private fun openLibrary() {
        composeRule.setContent { LioraTheme(darkTheme = true) { LioraApp() } }
        composeRule.onNodeWithText("Übungen").performClick()
        waitForText("Bankdrücken (Langhantel)")
    }

    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
