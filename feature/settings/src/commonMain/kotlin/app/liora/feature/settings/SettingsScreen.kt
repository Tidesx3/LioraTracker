package app.liora.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.theme.dynamicColorSupported
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.domain.OneRepMaxFormula
import app.liora.core.domain.Settings
import app.liora.core.domain.ThemeMode
import app.liora.core.model.DistanceUnit
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import app.liora.core.ui.AppLanguage
import app.liora.core.ui.AppLanguages
import app.liora.core.ui.RestTimePicker
import app.liora.core.ui.rememberAppLanguage
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.dialog_cancel
import app.liora.feature.settings.resources.dynamic_color
import app.liora.feature.settings.resources.formula
import app.liora.feature.settings.resources.language
import app.liora.feature.settings.resources.rest_hint
import app.liora.feature.settings.resources.rest_warmup
import app.liora.feature.settings.resources.rest_working
import app.liora.feature.settings.resources.rpe
import app.liora.feature.settings.resources.rpe_body
import app.liora.feature.settings.resources.section_appearance
import app.liora.feature.settings.resources.section_progress
import app.liora.feature.settings.resources.section_units
import app.liora.feature.settings.resources.section_workout
import app.liora.feature.settings.resources.settings_title
import app.liora.feature.settings.resources.stall_window
import app.liora.feature.settings.resources.theme
import app.liora.feature.settings.resources.unit_body_length
import app.liora.feature.settings.resources.unit_distance
import app.liora.feature.settings.resources.unit_weight
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        onBack = onBack,
        onChange = viewModel::update,
        language = rememberAppLanguage(),
        modifier = modifier,
    )
}

/** Which choice is open in a dialog. */
private enum class Choice {
    RestWorking,
    RestWarmup,
    Weight,
    Distance,
    BodyLength,
    Formula,
    StallWindow,
    Theme,
    Language,
}

@Composable
private fun SettingsContent(
    settings: Settings?,
    onBack: () -> Unit,
    onChange: (SettingsChange) -> Unit,
    language: AppLanguage,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf<Choice?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.settings_title),
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        if (settings == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.readableWidth(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp),
        ) {
            item(key = "workout") {
                SectionHeader(stringResource(Res.string.section_workout))
                SettingRow(
                    stringResource(Res.string.rest_working),
                    settings.rest.working.formatAsClock(),
                    onClick = { open = Choice.RestWorking },
                )
                SettingRow(
                    stringResource(Res.string.rest_warmup),
                    settings.rest.warmup.formatAsClock(),
                    onClick = { open = Choice.RestWarmup },
                )
                Text(
                    text = stringResource(Res.string.rest_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SwitchRow(
                    title = stringResource(Res.string.rpe),
                    description = stringResource(Res.string.rpe_body),
                    checked = settings.rpe,
                    onCheckedChange = { on -> onChange { it.copy(rpe = on) } },
                )
            }
            item(key = "units") {
                SectionHeader(stringResource(Res.string.section_units))
                SettingRow(
                    stringResource(Res.string.unit_weight),
                    weightUnitName(settings.units.weight),
                    onClick = { open = Choice.Weight },
                )
                SettingRow(
                    stringResource(Res.string.unit_distance),
                    distanceUnitName(settings.units.distance),
                    onClick = { open = Choice.Distance },
                )
                SettingRow(
                    stringResource(Res.string.unit_body_length),
                    bodyLengthUnitName(settings.units.bodyLength),
                    onClick = { open = Choice.BodyLength },
                )
            }
            item(key = "progress") {
                SectionHeader(stringResource(Res.string.section_progress))
                SettingRow(
                    stringResource(Res.string.formula),
                    formulaName(settings.oneRepMaxFormula),
                    onClick = { open = Choice.Formula },
                )
                SettingRow(
                    stringResource(Res.string.stall_window),
                    stallText(settings.stallWindow),
                    onClick = { open = Choice.StallWindow },
                )
            }
            item(key = "appearance") {
                SectionHeader(stringResource(Res.string.section_appearance))
                SettingRow(
                    stringResource(Res.string.theme),
                    themeName(settings.theme),
                    onClick = { open = Choice.Theme },
                )
                if (dynamicColorSupported) {
                    SwitchRow(
                        title = stringResource(Res.string.dynamic_color),
                        checked = settings.dynamicColor,
                        onCheckedChange = { on -> onChange { it.copy(dynamicColor = on) } },
                    )
                }
                if (language.available) {
                    SettingRow(
                        stringResource(Res.string.language),
                        languageName(language.current),
                        onClick = { open = Choice.Language },
                    )
                }
            }
        }
    }
    if (settings != null) {
        ChoiceDialogs(open, settings, onChange, language, onClose = { open = null })
    }
}

@Composable
private fun ChoiceDialogs(
    open: Choice?,
    settings: Settings,
    onChange: (SettingsChange) -> Unit,
    language: AppLanguage,
    onClose: () -> Unit,
) {
    val choose = { change: SettingsChange ->
        onClose()
        onChange(change)
    }
    when (open) {
        null -> {
            Unit
        }

        Choice.RestWorking, Choice.RestWarmup -> {
            val working = open == Choice.RestWorking
            RestTimePicker(
                current = (if (working) settings.rest.working else settings.rest.warmup).inWholeSeconds.toInt(),
                onChoose = { seconds ->
                    val chosen = seconds?.seconds ?: return@RestTimePicker
                    choose {
                        it.copy(rest = if (working) it.rest.copy(working = chosen) else it.rest.copy(warmup = chosen))
                    }
                },
                onDismiss = onClose,
                offerExerciseDefault = false,
            )
        }

        Choice.Weight -> {
            ChoiceDialog(
                title = stringResource(Res.string.unit_weight),
                options = WeightUnit.entries,
                selected = settings.units.weight,
                label = { weightUnitName(it) },
                onChoose = { unit -> choose { it.copy(units = it.units.copy(weight = unit)) } },
                onDismiss = onClose,
            )
        }

        Choice.Distance -> {
            ChoiceDialog(
                title = stringResource(Res.string.unit_distance),
                options = DistanceUnit.entries,
                selected = settings.units.distance,
                label = { distanceUnitName(it) },
                onChoose = { unit -> choose { it.copy(units = it.units.copy(distance = unit)) } },
                onDismiss = onClose,
            )
        }

        Choice.BodyLength -> {
            ChoiceDialog(
                title = stringResource(Res.string.unit_body_length),
                options = Units.BODY_LENGTHS,
                selected = settings.units.bodyLength,
                label = { bodyLengthUnitName(it) },
                onChoose = { unit -> choose { it.copy(units = it.units.copy(bodyLength = unit)) } },
                onDismiss = onClose,
            )
        }

        Choice.Formula -> {
            ChoiceDialog(
                title = stringResource(Res.string.formula),
                options = OneRepMaxFormula.entries,
                selected = settings.oneRepMaxFormula,
                label = { formulaName(it) },
                description = { formulaDescription(it) },
                onChoose = { formula -> choose { it.copy(oneRepMaxFormula = formula) } },
                onDismiss = onClose,
            )
        }

        Choice.StallWindow -> {
            ChoiceDialog(
                title = stringResource(Res.string.stall_window),
                options = Settings.STALL_WINDOWS,
                selected = settings.stallWindow,
                label = { stallText(it) },
                onChoose = { window -> choose { it.copy(stallWindow = window) } },
                onDismiss = onClose,
            )
        }

        Choice.Theme -> {
            ChoiceDialog(
                title = stringResource(Res.string.theme),
                options = ThemeMode.entries,
                selected = settings.theme,
                label = { themeName(it) },
                onChoose = { theme -> choose { it.copy(theme = theme) } },
                onDismiss = onClose,
            )
        }

        Choice.Language -> {
            // The OS keeps the app's language, not the synced settings.
            ChoiceDialog(
                title = stringResource(Res.string.language),
                options = listOf(null) + AppLanguages,
                selected = language.current,
                label = { languageName(it) },
                onChoose = {
                    onClose()
                    language.choose(it)
                },
                onDismiss = onClose,
            )
        }
    }
}

/** A setting: its name, and what it's set to. */
@Composable
private fun SettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = modifier.clickable(onClick = onClick),
    )
}

/** A setting that's on or off; the whole row toggles it. */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = modifier.clickable { onCheckedChange(!checked) },
    )
}

/** One of a few options, picked with radio buttons; picking closes it. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
    description: (@Composable (T) -> String)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onChoose(option) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onChoose(option) })
                        Column {
                            Text(label(option), style = MaterialTheme.typography.bodyLarge)
                            description?.let {
                                Text(
                                    text = it(option),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}
