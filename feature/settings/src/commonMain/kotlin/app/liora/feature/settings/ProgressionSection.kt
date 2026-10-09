package app.liora.feature.settings

import androidx.compose.runtime.Composable
import app.liora.core.domain.BelowRange
import app.liora.core.domain.LastTime
import app.liora.core.domain.ProgressionSettings
import app.liora.core.domain.RepAims
import app.liora.core.domain.Settings
import app.liora.core.domain.StepUpWhen
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.jump_limit
import app.liora.feature.settings.resources.jump_limit_value
import app.liora.feature.settings.resources.progression_below
import app.liora.feature.settings.resources.progression_last_time
import app.liora.feature.settings.resources.progression_rep_aims
import app.liora.feature.settings.resources.progression_step_up
import app.liora.feature.settings.resources.section_progression
import org.jetbrains.compose.resources.stringResource

/** Settings › Progression: how double progression aims, steps and looks back, and when a jump is flagged. */
@Composable
internal fun ProgressionGroup(
    settings: Settings,
    onOpen: (Choice) -> Unit,
) {
    val progression = settings.progression
    SettingsGroup(stringResource(Res.string.section_progression)) {
        SettingRow(
            stringResource(Res.string.progression_rep_aims),
            repAimsName(progression.repAims),
            onClick = { onOpen(Choice.RepAims) },
        )
        SettingRow(
            stringResource(Res.string.progression_step_up),
            stepUpName(progression.stepUp),
            onClick = { onOpen(Choice.StepUp) },
        )
        SettingRow(
            stringResource(Res.string.progression_below),
            belowRangeName(progression.belowRange),
            onClick = { onOpen(Choice.BelowRange) },
        )
        SettingRow(
            stringResource(Res.string.progression_last_time),
            lastTimeName(progression.lastTime),
            onClick = { onOpen(Choice.LastTime) },
        )
        SettingRow(
            stringResource(Res.string.jump_limit),
            stringResource(Res.string.jump_limit_value, settings.jumpLimitPercent),
            onClick = { onOpen(Choice.JumpLimit) },
        )
    }
}

/** The Progression section's choices, each with a line showing what it does. */
@Composable
internal fun ProgressionChoiceDialog(
    open: Choice,
    settings: Settings,
    choose: (SettingsChange) -> Unit,
    onClose: () -> Unit,
) {
    val progression = settings.progression
    val chooseProgression = { change: ProgressionSettings.() -> ProgressionSettings ->
        choose { it.copy(progression = it.progression.change()) }
    }
    when (open) {
        Choice.RepAims -> {
            ChoiceDialog(
                title = stringResource(Res.string.progression_rep_aims),
                options = RepAims.entries,
                selected = progression.repAims,
                label = { repAimsName(it) },
                description = { repAimsDescription(it) },
                onChoose = { aim -> chooseProgression { copy(repAims = aim) } },
                onDismiss = onClose,
            )
        }

        Choice.StepUp -> {
            ChoiceDialog(
                title = stringResource(Res.string.progression_step_up),
                options = StepUpWhen.entries,
                selected = progression.stepUp,
                label = { stepUpName(it) },
                description = { stepUpDescription(it) },
                onChoose = { stepUp -> chooseProgression { copy(stepUp = stepUp) } },
                onDismiss = onClose,
            )
        }

        Choice.BelowRange -> {
            ChoiceDialog(
                title = stringResource(Res.string.progression_below),
                options = BelowRange.entries,
                selected = progression.belowRange,
                label = { belowRangeName(it) },
                description = { belowRangeDescription(it) },
                onChoose = { below -> chooseProgression { copy(belowRange = below) } },
                onDismiss = onClose,
            )
        }

        Choice.LastTime -> {
            ChoiceDialog(
                title = stringResource(Res.string.progression_last_time),
                options = LastTime.entries,
                selected = progression.lastTime,
                label = { lastTimeName(it) },
                description = { lastTimeDescription(it) },
                onChoose = { lastTime -> chooseProgression { copy(lastTime = lastTime) } },
                onDismiss = onClose,
            )
        }

        else -> {
            ChoiceDialog(
                title = stringResource(Res.string.jump_limit),
                options = Settings.JUMP_LIMITS,
                selected = settings.jumpLimitPercent,
                label = { stringResource(Res.string.jump_limit_value, it) },
                onChoose = { limit -> choose { it.copy(jumpLimitPercent = limit) } },
                onDismiss = onClose,
            )
        }
    }
}
