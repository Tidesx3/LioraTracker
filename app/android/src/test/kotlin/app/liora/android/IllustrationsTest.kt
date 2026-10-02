package app.liora.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.illustration.Illustration
import app.liora.core.designsystem.illustration.LioraIllustration
import app.liora.core.designsystem.theme.LioraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every empty-state illustration on one sheet, in light and dark, to review them side by side. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w720dp-h1100dp-xhdpi", application = TestLioraApplication::class)
class IllustrationsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun light() = capture(darkTheme = false, "illustrations_light")

    @Test
    fun dark() = capture(darkTheme = true, "illustrations_dark")

    private fun capture(
        darkTheme: Boolean,
        name: String,
    ) {
        composeRule.setContent {
            LioraTheme(darkTheme = darkTheme) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Illustration.entries.chunked(COLUMNS).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEach { illustration ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    LioraIllustration(illustration, Modifier.width(200.dp))
                                    Text(
                                        illustration.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val COLUMNS = 3
    }
}
