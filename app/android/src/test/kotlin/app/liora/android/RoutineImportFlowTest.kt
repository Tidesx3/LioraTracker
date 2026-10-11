package app.liora.android

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.liora.core.data.AppStartup
import app.liora.core.data.exercise.ExerciseRepository
import app.liora.core.data.routine.RoutineRepository
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Routines an AI wrote, imported: a file opened from the Train tab, an answer shared from a chat app, and a
 * broken file pasted, whose problems go back to the AI. The files are the examples in docs/routine-format.md,
 * against the real catalog, so the format's documentation always imports as it says.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class RoutineImportFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val context get() = koin.get<Context>()
    private lateinit var activity: Activity

    @Before
    fun seedCatalog() = runBlocking { koin.get<AppStartup>().run() }

    @After
    fun tearDown() = stopKoin()

    @Test
    @Config(qualifiers = COVER)
    fun aProgramOpenedFromTheTrainTab() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        composeRule.onNodeWithContentDescription("Import routines").performClick()
        waitForText("Routines from an AI")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/routine_import_start_dark.png")

        composeRule.onNodeWithText("Open file").performClick()
        pick(File(context.cacheDir, "upper-lower.liora.json").apply { writeText(PROGRAM) })
        waitForText("Upper / Lower")
        composeRule.onNodeWithText("New rotation: the routines take turns").assertIsDisplayed()
        composeRule.onNodeWithText("New, a variation of Cable Lateral Raise").assertIsDisplayed()
        composeRule.onNodeWithText("Goes to Barbell Bench Press").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/routine_import_review_dark.png")

        composeRule.onNodeWithText("Add 2 routines").performClick()
        waitForText("Fresh plans on the board")
        composeRule.onNodeWithText("2 routines are waiting on the Train tab\n1 new exercise").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/routine_import_done_dark.png")

        val routines = runBlocking { koin.get<RoutineRepository>().routines.first() }
        assertEquals(listOf("Upper A", "Lower A"), routines.map { it.name })
        val folder = runBlocking { koin.get<RoutineRepository>().folders.first() }.single()
        assertTrue(folder.rotation)
        // Every name in the example is in the catalog; only the exercise it brings along is new.
        assertEquals(listOf("Cable Y-Raise"), customExercises())

        // A program shows on the Train tab, as a rotation with its first routine up next.
        composeRule.onNodeWithText("Show me").performClick()
        waitForText("Upper A")
        composeRule.onNodeWithContentDescription("Import routines").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COVER)
    fun theFormatGoesToAnAIFromThePhone() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        composeRule.onNodeWithContentDescription("Import routines").performClick()
        waitForText("Routines from an AI")
        composeRule.onNodeWithText("Copy the format for your AI").performClick()
        waitForText("Copied. Paste it into the chat")
        // The app carries its own copy of the documentation; it must be the documentation.
        assertEquals(
            DOCUMENTATION,
            clipboard()
                .primaryClip!!
                .getItemAt(0)
                .text
                .toString(),
        )
    }

    @Test
    @Config(qualifiers = COVER)
    fun anAnswerSharedFromAChatApp() {
        share("Here's a simple full-body routine for you:\n\n```json\n$SMALL\n```\n\nEnjoy!")
        waitForText("Full Body")
        composeRule.onNodeWithText("4 exercises · 11 sets").assertIsDisplayed()
        composeRule.onNodeWithText("Add routine").performClick()
        waitForText("Fresh plans on the board")
        assertEquals(emptyList<String>(), customExercises())

        // The one routine opens, ready to start.
        composeRule.onNodeWithText("Show me").performClick()
        waitForText("Start workout")
        composeRule.onNodeWithText("Full Body").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COVER)
    fun aBrokenFileGoesBackToTheAI() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        composeRule.onNodeWithContentDescription("Import routines").performClick()
        waitForText("Routines from an AI")
        clipboard().setPrimaryClip(ClipData.newPlainText("chat", BROKEN))
        composeRule.onNodeWithText("Paste").performClick()
        waitForText("Liora can’t import this yet")
        composeRule.onNodeWithText("Push › Bench Press › Set 2").assertIsDisplayed()
        composeRule.onNodeWithText("reps: Liora can’t read “eight” here").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/routine_import_problems_dark.png")

        composeRule.onNodeWithText("Copy for the AI").performClick()
        waitForText("Copied")
        val copied =
            clipboard()
                .primaryClip!!
                .getItemAt(0)
                .text
                .toString()
        assertEquals(
            "Push › Bench Press › Set 1: weight: “80” needs a unit. Give the file a weightUnit (kg or lb), " +
                "or write the unit with it, like “80 kg”.\n" +
                "Push › Bench Press › Set 2: reps: Liora can’t read “eight” here\n" +
                "Push › Bench Press: Liora doesn’t use tempo, so it’s left out",
            copied,
        )

        composeRule.onNodeWithText("Try another").performClick()
        waitForText("Routines from an AI")
    }

    @Test
    @Config(qualifiers = COVER)
    fun choosingWhereAnExerciseGoes() {
        share(PROGRAM)
        waitForText("Cable Y-Raise")
        composeRule.onNodeWithText("Cable Y-Raise").performClick()
        waitForText("Add as a new exercise")
        composeRule.onNodeWithText("The routine uses it").assertIsDisplayed()
        composeRule.onNodeWithText("Use Cable Lateral Raise").performClick()
        waitForText("Goes to Cable Lateral Raise")
        composeRule.onNodeWithText("Add 2 routines").performClick()
        waitForText("Fresh plans on the board")
        assertEquals(emptyList<String>(), customExercises())
    }

    @Test
    @Config(qualifiers = INNER)
    fun theReviewOnTheInnerScreen() {
        share(PROGRAM)
        waitForText("New, a variation of Cable Lateral Raise")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_routine_import_review_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun german() {
        share(PROGRAM)
        waitForText("Neue Rotation: Die Routinen wechseln sich ab")
        composeRule.onNodeWithText("2 Routinen hinzufügen").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_routine_import_review_dark.png")
    }

    /** Starts Liora with [text] shared to it, as a chat app's share button does. */
    private fun share(text: String) {
        val intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
        activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()
    }

    /** Answers the file picker the app just opened with [file]. */
    private fun pick(file: File) {
        val shadow = shadowOf(activity)
        val started = checkNotNull(shadow.nextStartedActivityForResult) { "No file picker opened" }
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
        shadow.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
    }

    private fun clipboard() = context.getSystemService(ClipboardManager::class.java)

    private fun customExercises(): List<String> =
        runBlocking { koin.get<ExerciseRepository>().observeExercises("en").first() }
            .filter { it.isCustom }
            .map { it.name }

    private fun nodeExists(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasText(text, substring = true)) }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val TIMEOUT_MS = 20_000L

        val DOCUMENTATION = File("../../docs/routine-format.md").readText()

        /** The JSON examples in the format's documentation, in their order there. */
        val EXAMPLES: List<String> =
            Regex("```json\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
                .findAll(DOCUMENTATION)
                .map { it.groupValues[1] }
                .toList()

        val SMALL = EXAMPLES[0]
        val PROGRAM = EXAMPLES[1]

        val BROKEN =
            """
            {"format": "liora-routine", "version": 1, "routines": [{"name": "Push", "exercises": [
              {"exercise": "Bench Press", "tempo": "3-1-1", "sets": [{"weight": 80, "reps": 8}, {"reps": "eight"}]}
            ]}]}
            """.trimIndent()
    }
}
