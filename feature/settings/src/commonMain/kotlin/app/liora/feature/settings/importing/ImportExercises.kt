package app.liora.feature.settings.importing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.domain.importing.ExerciseChoice
import app.liora.core.domain.importing.ImportSource
import app.liora.core.model.WeightUnit
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.backup_measurements
import app.liora.feature.settings.resources.backup_workouts
import app.liora.feature.settings.resources.dialog_cancel
import app.liora.feature.settings.resources.import_choice_new
import app.liora.feature.settings.resources.import_choice_pick
import app.liora.feature.settings.resources.import_choice_use
import app.liora.feature.settings.resources.import_choice_use_body
import app.liora.feature.settings.resources.import_choice_variation
import app.liora.feature.settings.resources.import_close
import app.liora.feature.settings.resources.import_confirm
import app.liora.feature.settings.resources.import_done
import app.liora.feature.settings.resources.import_done_title
import app.liora.feature.settings.resources.import_exercises_hint
import app.liora.feature.settings.resources.import_from_hevy
import app.liora.feature.settings.resources.import_from_strong
import app.liora.feature.settings.resources.import_goes_to
import app.liora.feature.settings.resources.import_measurements_here
import app.liora.feature.settings.resources.import_misfit
import app.liora.feature.settings.resources.import_new_exercise
import app.liora.feature.settings.resources.import_new_exercises
import app.liora.feature.settings.resources.import_new_variation
import app.liora.feature.settings.resources.import_not_an_export_body
import app.liora.feature.settings.resources.import_not_an_export_title
import app.liora.feature.settings.resources.import_nothing_new
import app.liora.feature.settings.resources.import_range
import app.liora.feature.settings.resources.import_reading
import app.liora.feature.settings.resources.import_section_matched
import app.liora.feature.settings.resources.import_section_new
import app.liora.feature.settings.resources.import_sets
import app.liora.feature.settings.resources.import_title
import app.liora.feature.settings.resources.import_unit
import app.liora.feature.settings.resources.import_unit_body
import app.liora.feature.settings.resources.import_unreadable_body
import app.liora.feature.settings.resources.import_unreadable_title
import app.liora.feature.settings.resources.import_working
import app.liora.feature.settings.resources.import_workouts_here
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.exerciseSection(
    key: String,
    title: StringResource,
    rows: List<ImportExerciseRow>,
    importing: Boolean,
    onOpen: (String) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = key) { SectionHeader(stringResource(title)) }
    items(rows, key = { "$key:${it.name}" }) { row ->
        ExerciseRow(row, enabled = !importing, onClick = { onOpen(row.name) })
    }
}

@Composable
private fun ExerciseRow(
    row: ImportExerciseRow,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(row.name) },
        supportingContent = {
            Column {
                Text(destination(row))
                if (!row.fits) {
                    Text(stringResource(Res.string.import_misfit), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        trailingContent = {
            Text(
                pluralStringResource(Res.plurals.import_sets, row.sets, row.sets),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

/** Where a row's sets go: "Goes to Lat Pulldown", "New, a variation of Lat Pulldown", "New exercise". */
@Composable
private fun destination(row: ImportExerciseRow): String {
    val target = row.target
    return when {
        row.choice is ExerciseChoice.Existing && target != null -> {
            stringResource(
                Res.string.import_goes_to,
                target.name,
            )
        }

        target != null -> {
            stringResource(Res.string.import_new_variation, target.name)
        }

        else -> {
            stringResource(Res.string.import_new_exercise)
        }
    }
}

/** A new exercise, the closest one here, the one picked, or another from the library. */
@Composable
internal fun ExerciseChoiceDialog(
    row: ImportExerciseRow,
    onChoose: (ExerciseChoice) -> Unit,
    onPick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val closest = row.closest
    val picked = (row.choice as? ExerciseChoice.Existing)?.takeIf { it.exerciseId != closest?.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.name) },
        text = {
            Column(Modifier.selectableGroup()) {
                ChoiceRow(
                    title = stringResource(Res.string.import_choice_new),
                    body = closest?.let { stringResource(Res.string.import_choice_variation, it.name) },
                    selected = row.choice is ExerciseChoice.New,
                    onClick = { onChoose(ExerciseChoice.New(basedOn = closest?.id)) },
                )
                if (closest != null) {
                    ChoiceRow(
                        title = stringResource(Res.string.import_choice_use, closest.name),
                        body = stringResource(Res.string.import_choice_use_body),
                        selected = row.choice == ExerciseChoice.Existing(closest.id),
                        onClick = { onChoose(ExerciseChoice.Existing(closest.id)) },
                    )
                }
                val target = row.target
                if (picked != null && target != null) {
                    ChoiceRow(
                        title = stringResource(Res.string.import_choice_use, target.name),
                        body = stringResource(Res.string.import_choice_use_body),
                        selected = true,
                        onClick = { onChoose(picked) },
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.import_choice_pick)) },
                    leadingContent = { Icon(painterResource(LioraIcons.Search), contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.clickable(onClick = onPick),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    body: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = body?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}
