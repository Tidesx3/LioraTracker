package app.liora.feature.settings.sync

import androidx.compose.runtime.Composable
import app.liora.core.data.sync.SyncProblem
import app.liora.core.data.sync.SyncState
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
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
import app.liora.feature.settings.resources.sync_space_gb
import app.liora.feature.settings.resources.sync_space_kb
import app.liora.feature.settings.resources.sync_space_mb
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

/** A size the way Drive shows one, in the locale's numbers: "640 KB", "2.4 MB", "1.2 GB". */
@Composable
internal fun storageText(bytes: Long): String {
    val numbers = rememberNumberFormatter()
    return when {
        bytes < MEGABYTE -> {
            // A few bytes still count as one kilobyte, so it never reads as nothing at all.
            val kilobytes = if (bytes == 0L) 0L else maxOf(1L, (bytes + KILOBYTE / 2) / KILOBYTE)
            stringResource(Res.string.sync_space_kb, numbers.format(kilobytes.toInt()))
        }

        bytes < GIGABYTE -> {
            stringResource(Res.string.sync_space_mb, numbers.format(bytes.toDouble() / MEGABYTE, maxFractionDigits = 1))
        }

        else -> {
            stringResource(Res.string.sync_space_gb, numbers.format(bytes.toDouble() / GIGABYTE, maxFractionDigits = 1))
        }
    }
}

// Decimal units, as Drive and Android's file sizes count.
private const val KILOBYTE = 1_000L
private const val MEGABYTE = 1_000_000L
private const val GIGABYTE = 1_000_000_000L

internal fun SyncProblem.text(): StringResource =
    when (this) {
        SyncProblem.Unreachable -> Res.string.sync_problem_unreachable
        SyncProblem.SignInNeeded -> Res.string.sync_problem_sign_in
        SyncProblem.StoreFull -> Res.string.sync_problem_full
        SyncProblem.NewerApp -> Res.string.sync_problem_newer_app
    }
