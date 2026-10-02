package app.liora.feature.body.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.model.MeasurementType
import app.liora.core.ui.label
import app.liora.core.ui.measurementInputText
import app.liora.core.ui.unitSymbol
import app.liora.feature.body.DayPicker
import app.liora.feature.body.resources.Res
import app.liora.feature.body.resources.body_log
import app.liora.feature.body.resources.cd_close
import app.liora.feature.body.resources.log_circumferences
import app.liora.feature.body.resources.log_clear_hint
import app.liora.feature.body.resources.log_invalid
import app.liora.feature.body.resources.log_pick_day
import app.liora.feature.body.resources.log_save
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LogMeasurementsScreen(
    viewModel: LogMeasurementsViewModel,
    onClose: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val values by viewModel.dayValues.collectAsStateWithLifecycle()
    // Close once the save has landed; leaving earlier would cancel it.
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(form.done) { if (form.done) currentOnDone() }
    var pickingDay by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.body_log),
                navigationIcon = { LioraIconButton(LioraIcons.Close, stringResource(Res.string.cd_close), onClose) },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = form.canSave) {
                        Text(stringResource(Res.string.log_save))
                    }
                },
            )
        },
    ) { padding ->
        Fields(
            form = form,
            values = values,
            onEdit = viewModel::edit,
            onPickDay = { pickingDay = true },
            contentPadding =
                PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
        )
    }
    if (pickingDay) {
        DayPicker(
            day = form.day,
            today = form.today,
            onPick = {
                pickingDay = false
                viewModel.selectDay(it)
            },
            onDismiss = { pickingDay = false },
        )
    }
}

@Composable
private fun Fields(
    form: LogForm,
    values: DayValues,
    onEdit: (MeasurementType, String) -> Unit,
    onPickDay: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.readableWidth(), contentPadding = contentPadding) {
        item(key = "day") {
            val pickDay = stringResource(Res.string.log_pick_day)
            AssistChip(
                onClick = onPickDay,
                label = { Text(rememberDateFormatter().longDate(form.day)) },
                leadingIcon = { Icon(painterResource(LioraIcons.Calendar), contentDescription = null) },
                modifier = Modifier.padding(horizontal = 16.dp).semantics { contentDescription = pickDay },
            )
        }
        if (values.onDay.isNotEmpty()) {
            item(key = "hint") {
                Text(
                    text = stringResource(Res.string.log_clear_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
        FieldRows.forEachIndexed { index, row ->
            if (index == 1) {
                item(key = "circumferences") { SectionHeader(stringResource(Res.string.log_circumferences)) }
            }
            item(key = row.first().key) { FieldRow(row, form, values, onEdit) }
        }
    }
}

/** Two fields side by side: left and right, or two neighbours on the body. */
@Composable
private fun FieldRow(
    types: List<MeasurementType>,
    form: LogForm,
    values: DayValues,
    onEdit: (MeasurementType, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberNumberFormatter()
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        types.forEach { type ->
            MeasurementField(
                type = type,
                // Fields not changed yet show what the day has.
                text =
                    form.edits[type] ?: values.onDay[type]
                        ?.let {
                            measurementInputText(
                                type,
                                it,
                                numbers,
                            )
                        }.orEmpty(),
                hint = values.before[type]?.let { measurementInputText(type, it, numbers) },
                invalid = form.isInvalid(type),
                last = type == FieldRows.last().last(),
                onEdit = { onEdit(type, it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MeasurementField(
    type: MeasurementType,
    text: String,
    hint: String?,
    invalid: Boolean,
    last: Boolean,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = text,
        // Numbers only; both decimal separators, since keyboards differ.
        onValueChange = { typed ->
            if (typed.length <= MAX_LENGTH &&
                typed.all { it.isDigit() || it in ".," }
            ) {
                onEdit(typed)
            }
        },
        label = { Text(stringResource(type.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // The latest value before this day, while the field is empty.
        placeholder = hint?.let { { Text(it) } },
        suffix = { Text(type.unitSymbol) },
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(Res.string.log_invalid)) }) else null,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
            ),
        modifier = modifier.testTag(LogTags.field(type)),
    )
}

/** The form's rows: bodyweight and body fat, then circumferences in pairs, top to bottom. */
private val FieldRows: List<List<MeasurementType>> =
    listOf(
        listOf(MeasurementType.Bodyweight, MeasurementType.BodyFat),
        listOf(MeasurementType.Neck, MeasurementType.Shoulders),
        listOf(MeasurementType.Chest, MeasurementType.Abdomen),
        listOf(MeasurementType.Waist, MeasurementType.Hips),
        listOf(MeasurementType.BicepsLeft, MeasurementType.BicepsRight),
        listOf(MeasurementType.ForearmLeft, MeasurementType.ForearmRight),
        listOf(MeasurementType.ThighLeft, MeasurementType.ThighRight),
        listOf(MeasurementType.CalfLeft, MeasurementType.CalfRight),
    )

private const val MAX_LENGTH = 6

/** Test tags for the form. */
object LogTags {
    fun field(type: MeasurementType) = "body.log.${type.key}"
}
