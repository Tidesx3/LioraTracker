package app.liora.feature.progress.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraScaffold
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.component.Stat
import app.liora.core.designsystem.component.StatRow
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.designsystem.util.rememberNumberFormatter
import app.liora.core.domain.MonthTotals
import app.liora.core.navigation.LocalPaneRole
import app.liora.core.navigation.PaneRole
import app.liora.core.ui.RecordsCard
import app.liora.core.ui.RecordsCardEntry
import app.liora.core.ui.currentLanguage
import app.liora.core.ui.durationLabel
import app.liora.core.ui.volumeText
import app.liora.feature.progress.resources.Res
import app.liora.feature.progress.resources.cd_next_month
import app.liora.feature.progress.resources.cd_previous_month
import app.liora.feature.progress.resources.report_days
import app.liora.feature.progress.resources.report_last_month
import app.liora.feature.progress.resources.report_muscles
import app.liora.feature.progress.resources.report_none
import app.liora.feature.progress.resources.report_records_count
import app.liora.feature.progress.resources.report_sets
import app.liora.feature.progress.resources.report_time
import app.liora.feature.progress.resources.report_title
import app.liora.feature.progress.resources.report_top
import app.liora.feature.progress.resources.report_volume
import app.liora.feature.progress.resources.report_workouts
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MonthlyReportScreen(
    viewModel: MonthlyReportViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = currentLanguage()
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MonthlyReportContent(
        state = uiState as? MonthlyReportUiState.Loaded,
        onBack = onBack,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        modifier = modifier,
    )
}

@Composable
private fun MonthlyReportContent(
    state: MonthlyReportUiState.Loaded?,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LioraScaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.report_title),
                // Beside the overview there is nothing to go back to.
                navigationIcon = { if (LocalPaneRole.current != PaneRole.Detail) BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        if (state == null) return@LioraScaffold
        val report = state.report
        LazyColumn(
            modifier = Modifier.readableWidth(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp),
        ) {
            item(
                key = "month",
            ) { MonthSwitcher(state, onPreviousMonth, onNextMonth, Modifier.padding(horizontal = 8.dp)) }
            if (report.totals.workouts == 0) {
                item(key = "none") {
                    Text(
                        text = stringResource(Res.string.report_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
                return@LazyColumn
            }
            item(key = "totals") { Totals(report.totals, report.previous, Modifier.padding(horizontal = 20.dp)) }
            item(key = "calendar") { MonthDays(report, Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) }
            if (report.records.isNotEmpty()) {
                item(key = "records") {
                    val count = report.records.sumOf { it.records.size }
                    RecordsCard(
                        title = pluralStringResource(Res.plurals.report_records_count, count, count),
                        entries =
                            report.records.map {
                                RecordsCardEntry(
                                    state.nameOf(it.exerciseId),
                                    state.trackingTypeOf(it.exerciseId),
                                    it.records,
                                )
                            },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item(key = "top") {
                SectionHeader(stringResource(Res.string.report_top))
                TopExercises(state, Modifier.padding(horizontal = 20.dp))
            }
            item(key = "muscles") {
                SectionHeader(stringResource(Res.string.report_muscles))
                MonthMuscles(report, Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
private fun MonthSwitcher(
    state: MonthlyReportUiState.Loaded,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Arrows only where there is somewhere to go: back to training, forward up to this month.
        Box(Modifier.size(ArrowSize)) {
            if (state.hasEarlier) {
                LioraIconButton(LioraIcons.ChevronLeft, stringResource(Res.string.cd_previous_month), onPreviousMonth)
            }
        }
        Text(
            text = rememberDateFormatter().month(state.report.month),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(ArrowSize)) {
            if (!state.isCurrent) {
                LioraIconButton(LioraIcons.ChevronRight, stringResource(Res.string.cd_next_month), onNextMonth)
            }
        }
    }
}

/** The month's numbers, two to a row, each with last month's beneath it. */
@Composable
private fun Totals(
    totals: MonthTotals,
    previous: MonthTotals,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberNumberFormatter()

    @Composable
    fun lastMonth(value: String) = stringResource(Res.string.report_last_month, value)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StatRow {
            Stat(
                numbers.format(totals.workouts),
                stringResource(Res.string.report_workouts),
                Modifier.weight(1f),
                detail = lastMonth(numbers.format(previous.workouts)),
            )
            Stat(
                numbers.format(totals.trainingDays),
                stringResource(Res.string.report_days),
                Modifier.weight(1f),
                detail = lastMonth(numbers.format(previous.trainingDays)),
            )
        }
        StatRow {
            Stat(
                durationLabel(totals.duration),
                stringResource(Res.string.report_time),
                Modifier.weight(1f),
                detail = lastMonth(durationLabel(previous.duration)),
            )
            Stat(
                volumeText(totals.volumeKg),
                stringResource(Res.string.report_volume),
                Modifier.weight(1f),
                detail = lastMonth(volumeText(previous.volumeKg)),
            )
        }
        StatRow {
            Stat(
                numbers.format(totals.sets),
                stringResource(Res.string.report_sets),
                Modifier.weight(1f),
                detail = lastMonth(numbers.format(previous.sets)),
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

private val ArrowSize = 48.dp
