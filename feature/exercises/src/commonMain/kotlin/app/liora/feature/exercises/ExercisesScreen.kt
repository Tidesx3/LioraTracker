package app.liora.feature.exercises

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.feature.exercises.resources.Res
import app.liora.feature.exercises.resources.exercises_empty_body
import app.liora.feature.exercises.resources.exercises_empty_title
import app.liora.feature.exercises.resources.exercises_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExercisesScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { LioraTopAppBar(title = stringResource(Res.string.exercises_title)) },
    ) { padding ->
        EmptyState(
            icon = LioraIcons.Exercises,
            title = stringResource(Res.string.exercises_empty_title),
            body = stringResource(Res.string.exercises_empty_body),
            modifier = Modifier.padding(padding),
        )
    }
}
