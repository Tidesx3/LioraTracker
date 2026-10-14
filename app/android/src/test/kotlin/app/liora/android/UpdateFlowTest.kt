package app.liora.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.liora.core.data.AppStartup
import app.liora.core.data.update.InstallProgress
import app.liora.core.data.update.UpdateRepository
import app.liora.core.domain.update.AppVersion
import app.liora.core.domain.update.Release
import app.liora.core.domain.update.ReleaseApk
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A newer Liora on GitHub: offered on the Train tab and in Settings, downloaded and installed on a tap. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class UpdateFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val installer get() = koin.get<TestUpdateInstaller>()

    @Before
    fun seed() = runBlocking { koin.get<AppStartup>().run() }

    @After
    fun tearDown() = stopKoin()

    @Test
    @Config(qualifiers = COVER)
    fun aNewerReleaseIsOfferedDownloadedAndInstalled() {
        offer(NEWER)
        launch()
        waitForText("Liora 0.5.0 is here")
        waitForText("Rotations take turns", substring = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/update_card_dark.png")

        composeRule.onNodeWithText("Update").performClick()
        waitForText("Downloading…")
        assertEquals(listOf(NEWER), installer.started)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/update_downloading_dark.png")

        // Downloaded and checked with Liora in the background: Install waits for a tap.
        installer.progress.value = InstallProgress.Ready(NEWER.version)
        waitForText("Downloaded and checked.")
        composeRule.onNodeWithText("Install").performClick()
        waitForText("Android is installing it, and closes Liora to do so.")
    }

    @Test
    @Config(qualifiers = COVER)
    fun notNowLeavesItToSettings() {
        offer(NEWER)
        launch()
        waitForText("Liora 0.5.0 is here")
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { !nodeExists(hasText("Liora 0.5.0 is here")) }

        // In About Liora: what's new under the version that says it's out, and Update.
        openAbout()
        waitForText("Version 0.5.0 is out")
        waitForText("Rotations take turns", substring = true)
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("How Liora works"))
        composeRule.onNodeWithText("Update").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/settings_update_dark.png")
    }

    @Test
    @Config(qualifiers = COVER)
    fun upToDateItSaysSoAndCanStopLooking() {
        runBlocking { koin.get<UpdateRepository>().check() }
        launch()
        openAbout()
        waitForText("Up to date as of", substring = true)

        composeRule.onNodeWithText("Look for updates").performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            nodeExists(isToggleable() and isOff() and hasAnyAncestor(hasText("Look for updates")))
        }
        assertFalse(runBlocking { koin.get<UpdateRepository>().autoCheck.first() })
    }

    @Test
    @Config(qualifiers = INNER)
    fun onTheInnerScreen() {
        offer(NEWER)
        launch()
        waitForText("Liora 0.5.0 is here")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/update_card_inner_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun inGerman() {
        offer(NEWER)
        launch()
        waitForText("Liora 0.5.0 ist da")
        waitForText("Rotationen wechseln sich ab", substring = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_update_card_dark.png")
    }

    private fun offer(release: Release) {
        koin.get<TestReleases>().latest = release
        runBlocking { koin.get<UpdateRepository>().check() }
    }

    private fun launch() {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        composeRule.waitForIdle()
    }

    private fun openAbout() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        waitForText("Rest between sets")
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Look for updates"))
    }

    private fun nodeExists(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(
        text: String,
        substring: Boolean = false,
    ) = composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasText(text, substring = substring)) }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val TIMEOUT_MS = 10_000L

        val NEWER =
            Release(
                version = AppVersion(0, 5, 0),
                notes =
                    """
                    ## English

                    - Rotations take turns, with what's next on the Train tab.
                    - A widget for the home screen.
                    - Updates from GitHub.
                    - Fixes all over.
                    - And a few more.

                    ## Deutsch

                    - Rotationen wechseln sich ab, und der Training-Tab sagt, was dran ist.
                    - Ein Widget für den Startbildschirm.
                    - Updates von GitHub.
                    """.trimIndent(),
                apk = ReleaseApk("https://example.com/liora-0.5.0.apk", sizeBytes = 10_000_000, sha256 = null),
            )
    }
}
