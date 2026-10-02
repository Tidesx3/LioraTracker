package app.liora.android

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.liora.core.data.AppStartup
import app.liora.core.data.body.BodyRepository
import app.liora.core.model.MeasurementType
import app.liora.feature.body.BodyTags
import app.liora.feature.body.log.LogTags
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
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
 * Body end to end: the first weigh-in, the overview with bodyweight and measurements, a measurement's
 * page, and correcting a past day. "Now" is fixed, so the screenshots stay the same from day to day.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class BodyFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val originalZone = java.util.TimeZone.getDefault()
    private val zone = TimeZone.of(ZONE)

    @Before
    fun setUp() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZONE))
        // Friday, 2 October 2026, before breakfast: the app's "now" for this test.
        val now = LocalDateTime(2026, 10, 2, 7, 30).toInstant(zone)
        loadKoinModules(module { single<Clock> { FixedClock(now) } })
        runBlocking { koin.get<AppStartup>().run() }
    }

    @After
    fun tearDown() {
        stopKoin()
        java.util.TimeZone.setDefault(originalZone)
    }

    @Test
    @Config(qualifiers = COVER)
    fun firstWeighIn() {
        openBody()
        waitForText("Track your body")
        composeRule.onNodeWithContentDescription("Log measurements").performClick()

        field(MeasurementType.Bodyweight).performTextInput("82.5")
        field(MeasurementType.BodyFat).performTextInput("18,4")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/body_log_dark.png")
        composeRule.onNodeWithText("Save").performClick()

        waitForText("82.5 kg")
        composeRule.onNodeWithText("18.4%").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COVER)
    fun overviewAndCorrectingAPastDay() {
        seedHistory()
        openBody()
        waitForText("82.4 kg")
        // Down from 84.6 kg on 1 September, a month before.
        composeRule.onNodeWithText("−2.2 kg since Sep 1").assertIsDisplayed()
        // Inside the card, which merges what it holds.
        composeRule.onNode(hasTestTag(BodyTags.CHART), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/body_overview_dark.png")

        composeRule.onNodeWithText("Waist").performClick()
        waitForText("3 entries")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/body_waist_dark.png")

        // 1 September had 86.5 cm; it was really 86.
        composeRule.onNodeWithText("Tuesday, September 1, 2026").performClick()
        waitForText("86.5")
        field(MeasurementType.Waist).performTextReplacement("86")
        composeRule.onNodeWithText("Save").performClick()
        waitForText("86 cm")
        composeRule.onAllNodesWithText("86.5 cm").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = INNER)
    fun innerScreen_aMeasurementOpensBesideTheOverview() {
        seedHistory()
        openBody()
        waitForText("Pick a measurement")
        composeRule.onNodeWithText("Waist").performClick()
        waitForText("3 entries")
        composeRule.onNode(isSelected() and hasText("Waist")).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fold_inner_body_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun germanOverview() {
        seedHistory()
        openBody("Fortschritt", "Körper")
        waitForText("82,4 kg")
        composeRule.onNodeWithText("−2,2 kg seit 1. Sept.").assertIsDisplayed()
        composeRule.onNodeWithText("18,1 %").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_body_overview_dark.png")
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

    private fun field(type: MeasurementType) = composeRule.onNode(hasTestTag(LogTags.field(type)))

    /**
     * Five weeks of weigh-ins every other day, drifting from 84.6 kg down to 82.4 kg, and waist, body fat
     * and arms measured on the first of each month.
     */
    private fun seedHistory() =
        runBlocking {
            val body = koin.get<BodyRepository>()
            val first = LocalDate(2026, 9, 1)
            val weights =
                listOf(84.6, 84.2, 84.4, 83.9, 83.7, 83.9, 83.4, 83.2, 83.5, 83.0, 82.8, 83.1, 82.7, 82.6, 82.8, 82.5)
            weights.forEachIndexed { index, kg ->
                body.saveDay(first.plus(index * 2, DateTimeUnit.DAY), mapOf(MeasurementType.Bodyweight to kg), zone)
            }
            body.saveDay(LocalDate(2026, 10, 2), mapOf(MeasurementType.Bodyweight to 82.4), zone)
            monthly(LocalDate(2026, 8, 1), waist = 0.88, bodyFat = 19.5)
            monthly(LocalDate(2026, 9, 1), waist = 0.865, bodyFat = 18.6)
            monthly(LocalDate(2026, 10, 1), waist = 0.855, bodyFat = 18.1)
            body.saveDay(
                LocalDate(2026, 10, 1),
                mapOf(MeasurementType.BicepsLeft to 0.38, MeasurementType.BicepsRight to 0.385),
                zone,
            )
        }

    private suspend fun monthly(
        day: LocalDate,
        waist: Double,
        bodyFat: Double,
    ) = koin
        .get<BodyRepository>()
        .saveDay(day, mapOf(MeasurementType.Waist to waist, MeasurementType.BodyFat to bodyFat), zone)

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
