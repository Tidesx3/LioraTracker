package app.liora.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import app.liora.core.data.AppStartup
import app.liora.core.data.body.BodyRepository
import app.liora.core.data.sync.SyncRepository
import app.liora.core.data.sync.SyncSignInNeededException
import app.liora.core.data.sync.SyncStoreFullException
import app.liora.core.data.sync.SyncStores
import app.liora.core.data.sync.SyncUnreachableException
import app.liora.core.model.MeasurementType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertNull
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

/** Sync in Settings: connect Google Drive, see how it went, sign in again, disconnect. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class SyncFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val sync get() = koin.get<SyncRepository>()
    private val stores get() = koin.get<SyncStores>() as TestSyncStores

    @Before
    fun seed() = runBlocking { koin.get<AppStartup>().run() }

    @After
    fun tearDown() = stopKoin()

    @Test
    @Config(qualifiers = COVER)
    fun connectSyncAndDisconnect() {
        runBlocking {
            koin.get<BodyRepository>().saveDay(
                LocalDate(2026, 10, 1),
                mapOf(MeasurementType.Bodyweight to 82.5),
                TimeZone.currentSystemDefault(),
            )
        }
        openSync()
        waitForText("Two copies beat one")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/sync_off_dark.png")

        // The consent screen answers at once in tests.
        composeRule.onNodeWithText("Connect Google Drive").performClick()
        waitForText(TEST_ACCOUNT)
        composeRule.onNodeWithText("Not synced yet").assertIsDisplayed()
        // The weigh-in hasn't gone up yet.
        waitForText("1 change waiting")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/sync_on_dark.png")

        // A sync as the background work runs it.
        runBlocking { sync.sync() }
        waitForText("Synced today at", substring = true)
        waitForText("Everything is in Drive")
        // And how much of the Drive it takes.
        composeRule.onNodeWithText("Space in your Drive").assertExists()
        waitForText("2.4 MB")
        // What it and connecting ask the scheduler for is in SyncRepositoryTest: those calls land after
        // the screen changes, where a UI test can't wait for them.
        composeRule.onNodeWithText("Sync now").assertIsEnabled()

        composeRule.onNodeWithText("Disconnect").performClick()
        waitForText("Stop syncing?")
        composeRule.onNodeWithText("Stop syncing").performClick()
        waitForText("Two copies beat one")
        assertNull(runBlocking { sync.state.first().account })
    }

    @Test
    @Config(qualifiers = COVER)
    fun signingInAgainClearsTheProblem() {
        runBlocking { sync.connect(TEST_ACCOUNT) }
        stores.failure = SyncSignInNeededException()
        runBlocking { sync.sync() }

        openSync()
        waitForText("Google Drive needs you to sign in again.")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/sync_sign_in_dark.png")

        stores.failure = null
        composeRule.onNodeWithText("Sign in").performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Google Drive needs you to sign in again.").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    @Config(qualifiers = COVER)
    fun theSettingsGearSaysWhenSyncNeedsYou() {
        runBlocking { sync.connect(TEST_ACCOUNT) }
        stores.failure = SyncStoreFullException()
        runBlocking { sync.sync() }

        Robolectric.buildActivity(MainActivity::class.java).setup()
        val needsYou = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sync needs you")
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(hasContentDescription("Settings") and needsYou).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/train_sync_needs_you_dark.png")

        // A Drive that's only busy for a moment doesn't need anyone.
        stores.failure = SyncUnreachableException("offline")
        runBlocking { sync.sync() }
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(hasContentDescription("Settings") and needsYou).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    @Config(qualifiers = INNER)
    fun syncOnTheInnerScreen() {
        openSync()
        waitForText("Two copies beat one")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/sync_off_inner_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun syncInGerman() {
        runBlocking { sync.connect(TEST_ACCOUNT) }
        openSync(settings = "Einstellungen", section = "Deine Daten", row = "Mit Google Drive synchronisieren")
        waitForText("Noch nicht synchronisiert")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_sync_on_dark.png")
    }

    /** Settings › Your data › Sync with Google Drive. */
    private fun openSync(
        settings: String = "Settings",
        section: String = "Your data",
        row: String = "Sync with Google Drive",
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
    private fun waitForText(
        text: String,
        substring: Boolean = false,
    ) = composeRule.waitUntil(TIMEOUT_MS) {
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val TIMEOUT_MS = 10_000L
    }
}
