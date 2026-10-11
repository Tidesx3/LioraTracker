package app.liora.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.data.health.HealthAccess
import app.liora.core.data.sync.SyncState
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.LioraAlertDialog
import app.liora.core.designsystem.component.LioraCard
import app.liora.core.designsystem.component.LioraCardDefaults
import app.liora.core.designsystem.component.LioraScaffold
import app.liora.core.designsystem.component.LioraSnackbarHost
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.theme.dynamicColorSupported
import app.liora.core.designsystem.util.formatAsClock
import app.liora.core.designsystem.util.workoutDisplayName
import app.liora.core.domain.CsvContent
import app.liora.core.domain.OneRepMaxFormula
import app.liora.core.domain.RestSound
import app.liora.core.domain.Settings
import app.liora.core.domain.ThemeMode
import app.liora.core.model.DistanceUnit
import app.liora.core.model.GymProfile
import app.liora.core.model.Units
import app.liora.core.model.WeightUnit
import app.liora.core.ui.AppLanguage
import app.liora.core.ui.AppLanguages
import app.liora.core.ui.RestTimePicker
import app.liora.core.ui.UpdateDetails
import app.liora.core.ui.currentLanguage
import app.liora.core.ui.rememberAppLanguage
import app.liora.core.ui.rememberFileOpener
import app.liora.core.ui.rememberFileSaver
import app.liora.feature.settings.data.DataActions
import app.liora.feature.settings.data.DataSection
import app.liora.feature.settings.data.DataUiState
import app.liora.feature.settings.data.DataViewModel
import app.liora.feature.settings.data.RestoreDialog
import app.liora.feature.settings.data.text
import app.liora.feature.settings.gym.gymName
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.backup_file_name
import app.liora.feature.settings.resources.csv_file_name
import app.liora.feature.settings.resources.csv_file_name_body
import app.liora.feature.settings.resources.csv_file_name_both
import app.liora.feature.settings.resources.dialog_cancel
import app.liora.feature.settings.resources.dialog_done
import app.liora.feature.settings.resources.dynamic_color
import app.liora.feature.settings.resources.formula
import app.liora.feature.settings.resources.glass
import app.liora.feature.settings.resources.glass_body
import app.liora.feature.settings.resources.gym
import app.liora.feature.settings.resources.language
import app.liora.feature.settings.resources.rest_hint
import app.liora.feature.settings.resources.rest_sound
import app.liora.feature.settings.resources.rest_sound_hint
import app.liora.feature.settings.resources.rest_warmup
import app.liora.feature.settings.resources.rest_working
import app.liora.feature.settings.resources.rpe
import app.liora.feature.settings.resources.rpe_body
import app.liora.feature.settings.resources.section_about
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
import app.liora.feature.settings.resources.walkthrough_body
import app.liora.feature.settings.resources.walkthrough_title
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun SettingsScreen(
    viewModel: SettingsViewModel,
    dataViewModel: DataViewModel,
    updatesViewModel: UpdatesViewModel,
    onBack: () -> Unit,
    onOpenGyms: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenHealth: () -> Unit,
    onImport: (source: String) -> Unit,
    onImportRoutines: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sync by dataViewModel.sync.collectAsStateWithLifecycle()
    val health by dataViewModel.healthAccess.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        dataViewModel.refreshHealth()
        onPauseOrDispose {}
    }
    val gym by viewModel.gym.collectAsStateWithLifecycle()
    val data by dataViewModel.uiState.collectAsStateWithLifecycle()
    val updates by updatesViewModel.uiState.collectAsStateWithLifecycle()
    val exerciseLanguage = currentLanguage()
    val untitled = workoutDisplayName(null)
    val today = dataViewModel.today().toString()
    val backupName = stringResource(Res.string.backup_file_name, today)
    val csvNames =
        mapOf(
            CsvContent.Workouts to stringResource(Res.string.csv_file_name, today),
            CsvContent.Body to stringResource(Res.string.csv_file_name_body, today),
            CsvContent.Both to stringResource(Res.string.csv_file_name_both, today),
        )
    // What the file being picked is for; the picker can outlast the activity.
    var csvContent by rememberSaveable { mutableStateOf(CsvContent.Workouts) }
    val backupSaver = rememberFileSaver(BACKUP_TYPE) { dataViewModel.backUp(it) }
    val backupOpener = rememberFileOpener(BACKUP_TYPES) { dataViewModel.read(it) }
    val csvSaver =
        rememberFileSaver(CSV_TYPE) { dataViewModel.exportCsv(it, csvContent, exerciseLanguage, untitled) }
    val importOpener = rememberFileOpener(IMPORT_TYPES, onImport)
    SettingsContent(
        settings = settings,
        gym = gym,
        data = data,
        sync = sync,
        health = health,
        today = dataViewModel.today(),
        onBack = onBack,
        onOpenSync = onOpenSync,
        onOpenHealth = onOpenHealth,
        onOpenGyms = onOpenGyms,
        onChange = viewModel::update,
        onPreviewSound = viewModel::preview,
        onShowWalkthrough = viewModel::showWalkthrough,
        updates = updates,
        updatesActions =
            UpdatesActions(
                onCheck = updatesViewModel::check,
                onUpdate = updatesViewModel::update,
                onInstall = updatesViewModel::install,
                onAutoCheck = updatesViewModel::setAutoCheck,
            ),
        dataActions =
            DataActions(
                onBackUp = { backupSaver.save(backupName) },
                onRestore = backupOpener::open,
                onImport = importOpener::open,
                onImportRoutines = onImportRoutines,
                onExportCsv = { content ->
                    csvContent = content
                    csvSaver.save(csvNames.getValue(content))
                },
                onConfirmRestore = dataViewModel::restore,
                onCancelRestore = dataViewModel::cancelRestore,
                onMessageShown = dataViewModel::messageShown,
            ),
        language = rememberAppLanguage(),
        modifier = modifier,
    )
}

private const val BACKUP_TYPE = "application/zip"
private const val CSV_TYPE = "text/csv"

/** Some places label a ZIP archive differently, or not at all. */
private val BACKUP_TYPES = listOf(BACKUP_TYPE, "application/x-zip-compressed", "application/octet-stream")

/** CSV files go by many types, depending on where they're kept. */
private val IMPORT_TYPES =
    listOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")

/** Which choice is open in a dialog. */
internal enum class Choice {
    RestWorking,
    RestWarmup,
    RestSound,
    Weight,
    Distance,
    BodyLength,
    Formula,
    StallWindow,
    RepAims,
    StepUp,
    BelowRange,
    LastTime,
    JumpLimit,
    Theme,
    Language,
}

@Composable
private fun SettingsContent(
    settings: Settings?,
    gym: GymProfile?,
    data: DataUiState,
    sync: SyncState?,
    health: HealthAccess?,
    today: LocalDate,
    onBack: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenGyms: () -> Unit,
    onChange: (SettingsChange) -> Unit,
    onPreviewSound: (RestSound) -> Unit,
    onShowWalkthrough: () -> Unit,
    updates: UpdatesUiState,
    updatesActions: UpdatesActions,
    dataActions: DataActions,
    language: AppLanguage,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf<Choice?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(data.message) {
        val message = data.message ?: return@LaunchedEffect
        snackbar.showSnackbar(getString(message.text()))
        dataActions.onMessageShown()
    }
    LioraScaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.settings_title),
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
        snackbarHost = { LioraSnackbarHost(snackbar) },
    ) { padding ->
        if (settings == null) return@LioraScaffold
        LazyColumn(
            modifier = Modifier.readableWidth(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp),
        ) {
            item(key = "workout") {
                SettingsGroup(stringResource(Res.string.section_workout)) {
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
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                    SettingRow(
                        stringResource(Res.string.rest_sound),
                        restSoundName(settings.restSound),
                        onClick = { open = Choice.RestSound },
                    )
                    SwitchRow(
                        title = stringResource(Res.string.rpe),
                        description = stringResource(Res.string.rpe_body),
                        checked = settings.rpe,
                        onCheckedChange = { on -> onChange { it.copy(rpe = on) } },
                    )
                    SettingRow(
                        stringResource(Res.string.gym),
                        gym?.let { gymName(it) }.orEmpty(),
                        onClick = onOpenGyms,
                    )
                }
            }
            item(key = "units") {
                SettingsGroup(stringResource(Res.string.section_units)) {
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
            }
            item(key = "progress") {
                SettingsGroup(stringResource(Res.string.section_progress)) {
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
            }
            item(key = "progression") {
                ProgressionGroup(settings, onOpen = { open = it })
            }
            item(key = "appearance") {
                SettingsGroup(stringResource(Res.string.section_appearance)) {
                    SettingRow(
                        stringResource(Res.string.theme),
                        themeName(settings.theme),
                        onClick = { open = Choice.Theme },
                    )
                    SwitchRow(
                        title = stringResource(Res.string.glass),
                        description = stringResource(Res.string.glass_body),
                        checked = settings.glass,
                        onCheckedChange = { on -> onChange { it.copy(glass = on) } },
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
            item(key = "data") {
                DataSection(
                    working = data.working,
                    sync = sync,
                    health = health,
                    today = today,
                    onOpenSync = onOpenSync,
                    onOpenHealth = onOpenHealth,
                    actions = dataActions,
                )
            }
            item(key = "about") {
                SettingsGroup(stringResource(Res.string.section_about)) {
                    VersionRow(updates, onCheck = updatesActions.onCheck)
                    // A newer Liora: what's new in it, right under the version that says it's out.
                    UpdateDetails(
                        state = updates.update,
                        onUpdate = updatesActions.onUpdate,
                        onInstall = updatesActions.onInstall,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                    AutoCheckRow(updates, onAutoCheck = updatesActions.onAutoCheck)
                    SettingRow(
                        stringResource(Res.string.walkthrough_title),
                        stringResource(Res.string.walkthrough_body),
                        onClick = onShowWalkthrough,
                    )
                }
            }
        }
    }
    if (settings != null) {
        ChoiceDialogs(open, settings, onChange, onPreviewSound, language, onClose = { open = null })
    }
    data.pending?.let { summary ->
        RestoreDialog(summary, onRestore = dataActions.onConfirmRestore, onDismiss = dataActions.onCancelRestore)
    }
}

@Composable
private fun ChoiceDialogs(
    open: Choice?,
    settings: Settings,
    onChange: (SettingsChange) -> Unit,
    onPreviewSound: (RestSound) -> Unit,
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

        Choice.RestSound -> {
            // Stays open, so each sound can be heard before settling on one.
            ChoiceDialog(
                title = stringResource(Res.string.rest_sound),
                note = stringResource(Res.string.rest_sound_hint),
                options = RestSound.entries,
                selected = settings.restSound,
                label = { restSoundName(it) },
                description = { restSoundDescription(it) },
                onChoose = { sound ->
                    onChange { it.copy(restSound = sound) }
                    onPreviewSound(sound)
                },
                onDismiss = onClose,
                dismissLabel = Res.string.dialog_done,
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

        Choice.Formula, Choice.StallWindow -> {
            ProgressChoiceDialog(open, settings, choose, onClose)
        }

        Choice.RepAims, Choice.StepUp, Choice.BelowRange, Choice.LastTime, Choice.JumpLimit -> {
            ProgressionChoiceDialog(open, settings, choose, onClose)
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

/** The Progress section's choices: the e1RM formula and the stall window. */
@Composable
private fun ProgressChoiceDialog(
    open: Choice,
    settings: Settings,
    choose: (SettingsChange) -> Unit,
    onClose: () -> Unit,
) {
    when (open) {
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

        else -> {
            ChoiceDialog(
                title = stringResource(Res.string.stall_window),
                options = Settings.STALL_WINDOWS,
                selected = settings.stallWindow,
                label = { stallText(it) },
                onChoose = { window -> choose { it.copy(stallWindow = window) } },
                onDismiss = onClose,
            )
        }
    }
}

/** A section of settings: its heading, then its rows on one card. */
@Composable
internal fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        SectionHeader(title)
        LioraCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

/** A setting: its name, and what it's set to. */
@Composable
internal fun SettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        colors = LioraCardDefaults.listItemColors(),
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
        colors = LioraCardDefaults.listItemColors(),
        modifier = modifier.clickable { onCheckedChange(!checked) },
    )
}

/** One of a few options, picked with radio buttons; picking closes it. */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
    description: (@Composable (T) -> String)? = null,
    /** A line above the options. */
    note: String? = null,
    dismissLabel: StringResource = Res.string.dialog_cancel,
) {
    LioraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(dismissLabel)) } },
    )
}
