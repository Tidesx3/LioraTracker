package app.liora.feature.settings

import androidx.compose.runtime.Composable
import app.liora.core.domain.BelowRange
import app.liora.core.domain.LastTime
import app.liora.core.domain.RepAims
import app.liora.core.domain.StepUpWhen
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.progression_below_never
import app.liora.feature.settings.resources.progression_below_never_body
import app.liora.feature.settings.resources.progression_below_once
import app.liora.feature.settings.resources.progression_below_once_body
import app.liora.feature.settings.resources.progression_below_twice
import app.liora.feature.settings.resources.progression_below_twice_body
import app.liora.feature.settings.resources.progression_last_time_anywhere
import app.liora.feature.settings.resources.progression_last_time_anywhere_body
import app.liora.feature.settings.resources.progression_last_time_routine
import app.liora.feature.settings.resources.progression_last_time_routine_body
import app.liora.feature.settings.resources.progression_rep_aims_each
import app.liora.feature.settings.resources.progression_rep_aims_each_body
import app.liora.feature.settings.resources.progression_rep_aims_first
import app.liora.feature.settings.resources.progression_rep_aims_first_body
import app.liora.feature.settings.resources.progression_rep_aims_same
import app.liora.feature.settings.resources.progression_rep_aims_same_body
import app.liora.feature.settings.resources.progression_rep_aims_weakest
import app.liora.feature.settings.resources.progression_rep_aims_weakest_body
import app.liora.feature.settings.resources.progression_step_up_every
import app.liora.feature.settings.resources.progression_step_up_every_body
import app.liora.feature.settings.resources.progression_step_up_first
import app.liora.feature.settings.resources.progression_step_up_first_body
import app.liora.feature.settings.resources.progression_step_up_total
import app.liora.feature.settings.resources.progression_step_up_total_body
import org.jetbrains.compose.resources.stringResource

// Settings › Progression: each choice's name, and a line showing what it does.

@Composable
internal fun repAimsName(aim: RepAims): String =
    stringResource(
        when (aim) {
            RepAims.WeakestSet -> Res.string.progression_rep_aims_weakest
            RepAims.FirstBelowTop -> Res.string.progression_rep_aims_first
            RepAims.SameAsLastTime -> Res.string.progression_rep_aims_same
            RepAims.EverySet -> Res.string.progression_rep_aims_each
        },
    )

@Composable
internal fun repAimsDescription(aim: RepAims): String =
    stringResource(
        when (aim) {
            RepAims.WeakestSet -> Res.string.progression_rep_aims_weakest_body
            RepAims.FirstBelowTop -> Res.string.progression_rep_aims_first_body
            RepAims.SameAsLastTime -> Res.string.progression_rep_aims_same_body
            RepAims.EverySet -> Res.string.progression_rep_aims_each_body
        },
    )

@Composable
internal fun stepUpName(stepUp: StepUpWhen): String =
    stringResource(
        when (stepUp) {
            StepUpWhen.EverySetAtTop -> Res.string.progression_step_up_every
            StepUpWhen.TotalReps -> Res.string.progression_step_up_total
            StepUpWhen.FirstSetAtTop -> Res.string.progression_step_up_first
        },
    )

@Composable
internal fun stepUpDescription(stepUp: StepUpWhen): String =
    stringResource(
        when (stepUp) {
            StepUpWhen.EverySetAtTop -> Res.string.progression_step_up_every_body
            StepUpWhen.TotalReps -> Res.string.progression_step_up_total_body
            StepUpWhen.FirstSetAtTop -> Res.string.progression_step_up_first_body
        },
    )

@Composable
internal fun belowRangeName(below: BelowRange): String =
    stringResource(
        when (below) {
            BelowRange.TwiceInARow -> Res.string.progression_below_twice
            BelowRange.RightAway -> Res.string.progression_below_once
            BelowRange.Never -> Res.string.progression_below_never
        },
    )

@Composable
internal fun belowRangeDescription(below: BelowRange): String =
    stringResource(
        when (below) {
            BelowRange.TwiceInARow -> Res.string.progression_below_twice_body
            BelowRange.RightAway -> Res.string.progression_below_once_body
            BelowRange.Never -> Res.string.progression_below_never_body
        },
    )

@Composable
internal fun lastTimeName(lastTime: LastTime): String =
    stringResource(
        when (lastTime) {
            LastTime.SameRoutine -> Res.string.progression_last_time_routine
            LastTime.Anywhere -> Res.string.progression_last_time_anywhere
        },
    )

@Composable
internal fun lastTimeDescription(lastTime: LastTime): String =
    stringResource(
        when (lastTime) {
            LastTime.SameRoutine -> Res.string.progression_last_time_routine_body
            LastTime.Anywhere -> Res.string.progression_last_time_anywhere_body
        },
    )
