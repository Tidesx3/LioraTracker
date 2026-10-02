package app.liora.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.liora.core.data.AppStartup
import app.liora.core.data.body.BodyRepository
import app.liora.core.data.body.ProgressPhotoRepository
import app.liora.core.model.MeasurementType
import app.liora.core.model.PhotoPose
import app.liora.feature.body.BodyTags
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Progress photos end to end: the gallery by day, one photo with its pose and day, deleting one, and
 * comparing two. Three months of synthetic photos are imported through the real storage, dated by the
 * camera, with a weigh-in on each of those days.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class BodyPhotosFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val originalZone = java.util.TimeZone.getDefault()
    private val zone = TimeZone.of(ZONE)

    @Before
    fun setUp() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZONE))
        // Friday, 2 October 2026: the app's "now" for this test.
        val now = LocalDateTime(2026, 10, 2, 7, 30).toInstant(zone)
        loadKoinModules(module { single<Clock> { FixedClock(now) } })
        runBlocking {
            koin.get<AppStartup>().run()
            seed()
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        java.util.TimeZone.setDefault(originalZone)
    }

    @Test
    @Config(qualifiers = COVER)
    fun galleryOnePhotoAndDeleting() {
        openBody()
        waitForText("7 photos")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/body_with_photos_dark.png")
        composeRule.onNodeWithText("Progress photos").performClick()
        waitForText("Thursday, October 1, 2026 · 82.5 kg")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/body_photos_dark.png")

        chip("Side").performClick()
        waitForPhotos(3)

        // The newest side photo, with its pose and the weigh-in that day.
        composeRule.onNodeWithContentDescription("Progress photo from Thu, Oct 1").performClick()
        waitForText("Thursday, October 1, 2026")
        chip("Side").assertIsSelected()
        composeRule.onNodeWithText("82.5 kg").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/body_photo_dark.png")

        composeRule.onNodeWithContentDescription("Delete photo").performClick()
        composeRule.onNodeWithText("Delete this photo?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        waitForPhotos(2)
    }

    @Test
    @Config(qualifiers = COVER)
    fun comparingTwoPhotos() {
        openGallery()
        composeRule.onNodeWithContentDescription("Compare").performClick()
        // The latest back photo has nothing to compare with yet; side does: 1 August against 1 October.
        waitForText("8 weeks apart · −2.5 kg")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/body_compare_dark.png")

        // "Before" is the side being chosen: a month ago instead.
        composeRule.onNodeWithContentDescription("Progress photo from Tue, Sep 1").performClick()
        waitForText("4 weeks apart · −2.1 kg")

        chip("Front").performClick()
        waitForText("8 weeks apart · −2.5 kg")
    }

    @Test
    @Config(qualifiers = COVER)
    fun addingAskForThePoseAndWhereFrom() {
        openGallery()
        composeRule.onNodeWithContentDescription("Add photo").performClick()
        waitForText("Choose from gallery")
        composeRule.onNodeWithText("Take photo").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/body_add_photo_dark.png")
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen_galleryBesideTheOverviewAndAWideComparison() {
        openGallery()
        // The Body page stays, its photos card highlighted.
        val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        composeRule.onNode(selected and hasText("Progress photos")).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_body_photos_dark.png")
        composeRule.onNodeWithContentDescription("Compare").performClick()
        waitForText("8 weeks apart")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_compare_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun germanComparison() {
        openBody("Fortschritt", "Körper")
        waitForText("7 Fotos")
        composeRule.onNodeWithText("Fortschrittsfotos").performClick()
        waitForText("Donnerstag, 1. Oktober 2026")
        composeRule.onNodeWithContentDescription("Vergleichen").performClick()
        waitForText("8 Wochen dazwischen · −2,5 kg")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_body_compare_dark.png")
    }

    private fun openBody(
        tab: String = "Progress",
        card: String = "Body",
    ) {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(tab).performClick()
        waitForText(card)
        composeRule.onNodeWithText(card).performClick()
    }

    private fun openGallery() {
        openBody()
        waitForText("7 photos")
        composeRule.onNodeWithText("Progress photos").performClick()
        waitForPhotos(7)
    }

    /** A filter chip, not the pose badges on the photos. */
    private fun chip(label: String) =
        composeRule
            .onAllNodes(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
            .onFirst()

    /**
     * Three months, photographed on the first of each: front and side, plus back the last time, a little
     * slimmer at the waist each month, with a weigh-in the same morning.
     */
    private suspend fun seed() {
        val body = koin.get<BodyRepository>()
        val photos = koin.get<ProgressPhotoRepository>()
        val context = koin.get<android.content.Context>()
        val months =
            listOf(
                Triple(LocalDate(2026, 8, 1), 85.0, 236f),
                Triple(LocalDate(2026, 9, 1), 84.6, 224f),
                Triple(LocalDate(2026, 10, 1), 82.5, 208f),
            )
        months.forEachIndexed { index, (day, kg, waist) ->
            body.saveDay(day, mapOf(MeasurementType.Bodyweight to kg), zone)
            val poses = if (index == months.lastIndex) PhotoPose.entries else listOf(PhotoPose.Front, PhotoPose.Side)
            poses.forEachIndexed { minute, pose ->
                val exifDate = day.toString().replace('-', ':') + " 08:0$minute:00"
                photos.add(syntheticPhoto(context, pose, waist, exifDate), pose)
            }
        }
    }

    private fun waitForPhotos(count: Int) =
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag(BodyTags.PHOTO)).fetchSemanticsNodes().size == count
        }

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) =
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

    private class FixedClock(
        private val now: Instant,
    ) : Clock {
        override fun now() = now
    }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val ZONE = "Europe/Vienna"
        const val TIMEOUT_MS = 20_000L
    }
}
