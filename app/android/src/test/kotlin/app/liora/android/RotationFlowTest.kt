package app.liora.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.liora.core.data.AppStartup
import app.liora.core.data.routine.RoutineRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.model.Mass
import app.liora.core.model.RepRange
import app.liora.core.model.Routine
import app.liora.core.model.RoutineExercise
import app.liora.core.model.RoutineSet
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A folder made a rotation: what's next heads the Train tab, and moves on with each workout. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestLioraApplication::class)
class RotationFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val routines get() = koin.get<RoutineRepository>()

    @Before
    fun seed() =
        runBlocking {
            koin.get<AppStartup>().run()
            val folder = routines.createFolder(PPL)
            listOf(
                "push" to "Push Day" to "fedb.Barbell_Bench_Press_-_Medium_Grip",
                "pull" to "Pull Day" to "fedb.Seated_Cable_Rows",
                "legs" to "Leg Day" to "fedb.Barbell_Squat",
            ).forEach { (idAndName, exercise) ->
                val (id, name) = idAndName
                routines.save(
                    Routine(
                        id = id,
                        name = name,
                        folderId = folder,
                        exercises =
                            listOf(
                                RoutineExercise(
                                    id = "$id-e",
                                    exerciseId = exercise,
                                    sets =
                                        List(
                                            3,
                                        ) { RoutineSet("$id-$it", weight = Mass(60.0), reps = RepRange(8, 12)) },
                                ),
                            ),
                    ),
                )
            }
        }

    @After
    fun tearDown() = stopKoin()

    @Test
    @Config(qualifiers = COVER)
    fun aRotationSaysWhatsNextAndMovesOn() {
        launch()
        waitForText(PPL)
        // A plain folder says nothing about what's next.
        composeRule.onAllNodesWithText("Next up in $PPL").assertCountEqualsZero()

        // The folder's menu comes before its routines'.
        composeRule.onAllNodesWithContentDescription("More options").onFirst().performClick()
        composeRule.onNodeWithText("Make it a rotation").performClick()
        waitForText("Next up in $PPL")
        waitForText("Start Push Day")

        // Push is done: Pull is next.
        runBlocking {
            val workouts = koin.get<ActiveWorkoutRepository>()
            workouts.startFromRoutine("push")
            workouts.finish()
        }
        waitForText("Start Pull Day")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/rotation_next_up_dark.png")

        composeRule.onNodeWithText("Start Pull Day").performClick()
        waitForText("Finish")
        val active = runBlocking { koin.get<ActiveWorkoutRepository>().activeWorkout.first() }
        assertEquals("pull", active?.routineId)
    }

    @Test
    @Config(qualifiers = INNER)
    fun onTheInnerScreen() {
        runBlocking {
            routines.setRotation(
                routines.folders
                    .first()
                    .single()
                    .id,
                true,
            )
        }
        launch()
        waitForText("Start Push Day")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/rotation_next_up_inner_dark.png")
    }

    @Test
    @Config(qualifiers = "de-$COVER")
    fun inGerman() {
        runBlocking {
            routines.setRotation(
                routines.folders
                    .first()
                    .single()
                    .id,
                true,
            )
        }
        launch()
        waitForText("Als Nächstes in $PPL")
        waitForText("Push Day starten")
        composeRule.onRoot().captureRoboImage("src/test/screenshots/de_rotation_next_up_dark.png")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEqualsZero() {
        assertEquals(0, fetchSemanticsNodes().size)
    }

    private fun launch(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).setup().also { composeRule.waitForIdle() }

    private fun nodeExists(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    // Room emits on a background dispatcher that Compose's idling doesn't track, so wait explicitly.
    private fun waitForText(text: String) = composeRule.waitUntil(TIMEOUT_MS) { nodeExists(hasText(text)) }

    private companion object {
        const val COVER = "w411dp-h960dp-night-xxhdpi"
        const val INNER = "w984dp-h1092dp-night-xhdpi"
        const val TIMEOUT_MS = 10_000L
        const val PPL = "Push Pull Legs"
    }
}
