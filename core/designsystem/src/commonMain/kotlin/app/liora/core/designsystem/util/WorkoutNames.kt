package app.liora.core.designsystem.util

import androidx.compose.runtime.Composable
import app.liora.core.designsystem.resources.Res
import app.liora.core.designsystem.resources.workout_untitled
import org.jetbrains.compose.resources.stringResource

/** The name to show for a workout, falling back to a localized default when the user hasn't named it. */
@Composable
fun workoutDisplayName(name: String?): String = name ?: stringResource(Res.string.workout_untitled)
