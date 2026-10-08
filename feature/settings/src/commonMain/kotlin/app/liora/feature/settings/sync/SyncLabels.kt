package app.liora.feature.settings.sync

import androidx.compose.runtime.Composable
import app.liora.core.data.sync.SyncProblem
import app.liora.core.data.sync.SyncState
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.sync_problem_full
import app.liora.feature.settings.resources.sync_problem_newer_app
import app.liora.feature.settings.resources.sync_problem_sign_in
import app.liora.feature.settings.resources.sync_problem_unreachable
import app.liora.feature.settings.resources.sync_row_never
import app.liora.feature.settings.resources.sync_row_off
import app.liora.feature.settings.resources.sync_row_on
import app.liora.feature.settings.resources.sync_row_running
import app.liora.feature.settings.resources.sync_row_today
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Where sync stands in a few words: off, running, a problem, or when it last went through. */
@Composable
internal fun syncStatusText(
    state: SyncState,
    today: LocalDate,
): String {
    val problem = state.problem
    return when {
        state.account == null -> stringResource(Res.string.sync_row_off)
        state.running -> stringResource(Res.string.sync_row_running)
        problem != null -> stringResource(problem.text())
        else -> lastSyncedText(state, today)
    }
}

/** When sync last went through: "Synced today at 18:42", or the day before that. */
@Composable
internal fun lastSyncedText(
    state: SyncState,
    today: LocalDate,
): String {
    val dates = rememberDateFormatter()
    val last = state.lastSynced?.toLocalDateTime(TimeZone.currentSystemDefault())
    return when {
        last == null -> stringResource(Res.string.sync_row_never)
        last.date == today -> stringResource(Res.string.sync_row_today, dates.time(last.time))
        else -> stringResource(Res.string.sync_row_on, dates.longDate(last.date), dates.time(last.time))
    }
}

internal fun SyncProblem.text(): StringResource =
    when (this) {
        SyncProblem.Unreachable -> Res.string.sync_problem_unreachable
        SyncProblem.SignInNeeded -> Res.string.sync_problem_sign_in
        SyncProblem.StoreFull -> Res.string.sync_problem_full
        SyncProblem.NewerApp -> Res.string.sync_problem_newer_app
    }
