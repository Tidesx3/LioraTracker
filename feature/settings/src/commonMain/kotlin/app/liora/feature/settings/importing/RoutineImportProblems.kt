package app.liora.feature.settings.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liora.core.data.importing.ProblemPlace
import app.liora.core.data.importing.RoutineFileProblem
import app.liora.core.data.importing.RoutineFileProblem.Kind
import app.liora.core.designsystem.component.LioraBottomBar
import app.liora.core.designsystem.component.LioraButton
import app.liora.core.designsystem.component.LioraCard
import app.liora.core.designsystem.component.LioraCardDefaults
import app.liora.core.designsystem.component.LioraOutlinedButton
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.ui.rememberTextClipboard
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.routine_import_copy_problems
import app.liora.feature.settings.resources.routine_import_invalid_body
import app.liora.feature.settings.resources.routine_import_invalid_title
import app.liora.feature.settings.resources.routine_import_problems_copied
import app.liora.feature.settings.resources.routine_import_problems_label
import app.liora.feature.settings.resources.routine_import_start_over
import app.liora.feature.settings.resources.routine_problem_base_not_found
import app.liora.feature.settings.resources.routine_problem_duplicate
import app.liora.feature.settings.resources.routine_problem_file
import app.liora.feature.settings.resources.routine_problem_invalid
import app.liora.feature.settings.resources.routine_problem_invalid_value
import app.liora.feature.settings.resources.routine_problem_missing
import app.liora.feature.settings.resources.routine_problem_newer_version
import app.liora.feature.settings.resources.routine_problem_no_weight_unit
import app.liora.feature.settings.resources.routine_problem_not_a_routine_file
import app.liora.feature.settings.resources.routine_problem_not_json
import app.liora.feature.settings.resources.routine_problem_progression_ranges
import app.liora.feature.settings.resources.routine_problem_set
import app.liora.feature.settings.resources.routine_problem_superset_apart
import app.liora.feature.settings.resources.routine_problem_unknown_field
import org.jetbrains.compose.resources.stringResource

// What keeps a routine file from being imported, and what is worth knowing about it: where each problem is
// and what it is, as the screen lists it and as the AI that wrote the file gets it back.

/** What keeps the file from being imported, to hand back to the AI that wrote it. */
@Composable
internal fun Problems(
    problems: List<RoutineFileProblem>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.readableWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "header") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(Res.string.routine_import_invalid_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(Res.string.routine_import_invalid_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "problems") { ProblemCard(problems) }
    }
}

@Composable
internal fun ProblemCard(
    problems: List<RoutineFileProblem>,
    modifier: Modifier = Modifier,
) {
    LioraCard(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            problems.forEach { problem ->
                val color = MaterialTheme.colorScheme.let { if (problem.isError) it.error else it.onSurface }
                ListItem(
                    overlineContent = { Text(problemPlace(problem.place)) },
                    headlineContent = { Text(problemMessage(problem), color = color) },
                    colors = LioraCardDefaults.listItemColors(),
                )
            }
        }
    }
}

/** Copies the problems as the screen shows them, to paste back to the AI; or starts over. */
@Composable
internal fun ProblemsBar(
    problems: List<RoutineFileProblem>,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = rememberTextClipboard()
    var copied by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(Res.string.routine_import_problems_label)
    val text = problems.map { "${problemPlace(it.place)}: ${problemMessage(it)}" }.joinToString("\n")
    LioraBottomBar(modifier = modifier) {
        Row(
            modifier = Modifier.navigationBarsPadding().readableWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LioraOutlinedButton(onClick = onStartOver, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(stringResource(Res.string.routine_import_start_over))
            }
            LioraButton(
                onClick = {
                    clipboard.copy(label, text)
                    copied = true
                },
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(
                    stringResource(
                        if (copied) {
                            Res.string.routine_import_problems_copied
                        } else {
                            Res.string.routine_import_copy_problems
                        },
                    ),
                )
            }
        }
    }
}

/** Where a problem is: "Push › Bench Press › Set 2", or the file itself. */
@Composable
private fun problemPlace(place: ProblemPlace): String {
    val set = place.set?.let { stringResource(Res.string.routine_problem_set, it) }
    val parts = listOfNotNull(place.routine, place.exercise, set)
    return if (parts.isEmpty()) stringResource(Res.string.routine_problem_file) else parts.joinToString(" › ")
}

@Composable
private fun problemMessage(problem: RoutineFileProblem): String {
    val field = problem.place.field.orEmpty()
    val value = problem.value.orEmpty()
    return when (problem.kind) {
        Kind.NotJson -> stringResource(Res.string.routine_problem_not_json)
        Kind.NotARoutineFile -> stringResource(Res.string.routine_problem_not_a_routine_file)
        Kind.NewerVersion -> stringResource(Res.string.routine_problem_newer_version, value)
        Kind.Missing -> stringResource(Res.string.routine_problem_missing, field)
        Kind.Invalid -> invalidMessage(field, value)
        Kind.NoWeightUnit -> stringResource(Res.string.routine_problem_no_weight_unit, field, value)
        Kind.DuplicateExercise -> stringResource(Res.string.routine_problem_duplicate, value)
        Kind.UnknownField -> stringResource(Res.string.routine_problem_unknown_field, value)
        Kind.SupersetApart -> stringResource(Res.string.routine_problem_superset_apart, value)
        Kind.ProgressionNeedsRanges -> stringResource(Res.string.routine_problem_progression_ranges)
        Kind.BaseNotFound -> stringResource(Res.string.routine_problem_base_not_found, value)
    }
}

@Composable
private fun invalidMessage(
    field: String,
    value: String,
): String =
    if (field.isEmpty()) {
        stringResource(Res.string.routine_problem_invalid_value, value)
    } else {
        stringResource(Res.string.routine_problem_invalid, field, value)
    }
