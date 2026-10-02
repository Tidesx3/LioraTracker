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
import app.liora.core.ui.AppLanguage
import app.liora.core.ui.AppLanguages
import app.liora.core.ui.RestTimePicker
import app.liora.core.ui.rememberAppLanguage
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.dialog_cancel
import app.liora.feature.settings.resources.dynamic_color
import app.liora.feature.settings.resources.formula
import app.liora.feature.settings.resources.formula_brzycki
import app.liora.feature.settings.resources.formula_brzycki_body
import app.liora.feature.settings.resources.formula_epley
import app.liora.feature.settings.resources.formula_epley_body
import app.liora.feature.settings.resources.language
import app.liora.feature.settings.resources.language_de
import app.liora.feature.settings.resources.language_en
import app.liora.feature.settings.resources.language_system
import app.liora.feature.settings.resources.rest_hint
import app.liora.feature.settings.resources.rest_warmup
import app.liora.feature.settings.resources.rest_working
import app.liora.feature.settings.resources.section_appearance
import app.liora.feature.settings.resources.section_progress
import app.liora.feature.settings.resources.section_workout
import app.liora.feature.settings.resources.settings_title
import app.liora.feature.settings.resources.stall_weeks
import app.liora.feature.settings.resources.stall_window
import app.liora.feature.settings.resources.theme
import app.liora.feature.settings.resources.theme_dark
import app.liora.feature.settings.resources.theme_light
import app.liora.feature.settings.resources.theme_system
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

@Composable
internal fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        actions =
            SettingsActions(
                onBack = onBack,
                onRestWorking = viewModel::setRestWorking,
                onRestWarmup = viewModel::setRestWarmup,
                onFormula = viewModel::setFormula,
                onStallWindow = viewModel::setStallWindow,
                onTheme = viewModel::setTheme,
                onDynamicColor = viewModel::setDynamicColor,
            ),
        language = rememberAppLanguage(),
        modifier = modifier,
    )
}

internal class SettingsActions(
    val onBack: () -> Unit,
    val onRestWorking: (Int) -> Unit,
    val onRestWarmup: (Int) -> Unit,
    val onFormula: (OneRepMaxFormula) -> Unit,
    val onStallWindow: (Duration) -> Unit,
    val onTheme: (ThemeMode) -> Unit,
    val onDynamicColor: (Boolean) -> Unit,
)

/** Which choice is open in a dialog. */
private enum class Choice { RestWorking, RestWarmup, Formula, StallWindow, Theme, Language }

@Composable
private fun SettingsContent(
    settings: Settings?,
    actions: SettingsActions,
    language: AppLanguage,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf<Choice?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.settings_title),
                navigationIcon = { BackButton(onClick = actions.onBack) },
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
                SettingRow(stringResource(Res.string.rest_working), settings.rest.working.formatAsClock(), onClick = {
                    open =
                        Choice.RestWorking
                })
                SettingRow(stringResource(Res.string.rest_warmup), settings.rest.warmup.formatAsClock(), onClick = {
                    open =
                        Choice.RestWarmup
                })
                Text(
                    text = stringResource(Res.string.rest_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "progress") {
                SectionHeader(stringResource(Res.string.section_progress))
                SettingRow(stringResource(Res.string.formula), formulaName(settings.oneRepMaxFormula), onClick = {
                    open =
                        Choice.Formula
                })
                SettingRow(stringResource(Res.string.stall_window), stallText(settings.stallWindow), onClick = {
                    open =
                        Choice.StallWindow
                })
            }
            item(key = "appearance") {
                SectionHeader(stringResource(Res.string.section_appearance))
                SettingRow(
                    stringResource(Res.string.theme),
                    themeName(settings.theme),
                    onClick = { open = Choice.Theme },
                )
                if (dynamicColorSupported) {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.dynamic_color)) },
                        trailingContent = {
                            Switch(checked = settings.dynamicColor, onCheckedChange = actions.onDynamicColor)
                        },
                        modifier = Modifier.clickable { actions.onDynamicColor(!settings.dynamicColor) },
                    )
                }
                if (language.available) {
                    SettingRow(stringResource(Res.string.language), languageName(language.current), onClick = {
                        open =
                            Choice.Language
                    })
                }
            }
        }
    }
    if (settings != null) {
        ChoiceDialogs(open, settings, actions, language, onClose = { open = null })
    }
}

@Composable
private fun ChoiceDialogs(
    open: Choice?,
    settings: Settings,
    actions: SettingsActions,
    language: AppLanguage,
    onClose: () -> Unit,
) {
    val choose = { action: () -> Unit ->
        onClose()
        action()
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
                    val chosen = seconds ?: return@RestTimePicker
                    choose { if (working) actions.onRestWorking(chosen) else actions.onRestWarmup(chosen) }
                },
                onDismiss = onClose,
                offerExerciseDefault = false,
            )
        }

        Choice.Formula -> {
            ChoiceDialog(
                title = stringResource(Res.string.formula),
                options = OneRepMaxFormula.entries,
                selected = settings.oneRepMaxFormula,
                label = { formulaName(it) },
                description = { formulaDescription(it) },
                onChoose = { choose { actions.onFormula(it) } },
                onDismiss = onClose,
            )
        }

        Choice.StallWindow -> {
            ChoiceDialog(
                title = stringResource(Res.string.stall_window),
                options = Settings.STALL_WINDOWS,
                selected = settings.stallWindow,
                label = { stallText(it) },
                onChoose = { choose { actions.onStallWindow(it) } },
                onDismiss = onClose,
            )
        }

        Choice.Theme -> {
            ChoiceDialog(
                title = stringResource(Res.string.theme),
                options = ThemeMode.entries,
                selected = settings.theme,
                label = { themeName(it) },
                onChoose = { choose { actions.onTheme(it) } },
                onDismiss = onClose,
            )
        }

        Choice.Language -> {
            ChoiceDialog(
                title = stringResource(Res.string.language),
                options = listOf(null) + AppLanguages,
                selected = language.current,
                label = { languageName(it) },
                onChoose = { choose { language.choose(it) } },
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

@Composable
private fun formulaName(formula: OneRepMaxFormula): String =
    stringResource(
        when (formula) {
            OneRepMaxFormula.Epley -> Res.string.formula_epley
            OneRepMaxFormula.Brzycki -> Res.string.formula_brzycki
        },
    )

@Composable
private fun formulaDescription(formula: OneRepMaxFormula): String =
    stringResource(
        when (formula) {
            OneRepMaxFormula.Epley -> Res.string.formula_epley_body
            OneRepMaxFormula.Brzycki -> Res.string.formula_brzycki_body
        },
    )

@Composable
private fun stallText(window: Duration): String {
    val weeks = (window.inWholeDays / DAYS_PER_WEEK).toInt()
    return pluralStringResource(Res.plurals.stall_weeks, weeks, weeks)
}

@Composable
private fun themeName(theme: ThemeMode): String =
    stringResource(
        when (theme) {
            ThemeMode.System -> Res.string.theme_system
            ThemeMode.Light -> Res.string.theme_light
            ThemeMode.Dark -> Res.string.theme_dark
        },
    )

@Composable
private fun languageName(language: String?): String =
    stringResource(
        when (language) {
            null -> Res.string.language_system
            "de" -> Res.string.language_de
            else -> Res.string.language_en
        },
    )

private const val DAYS_PER_WEEK = 7
