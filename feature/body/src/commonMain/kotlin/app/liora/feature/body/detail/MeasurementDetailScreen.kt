package app.liora.feature.body.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.domain.DayValue
import app.liora.core.model.MeasurementType
import app.liora.core.navigation.LocalPaneRole
import app.liora.core.navigation.PaneRole
import app.liora.core.ui.label
import app.liora.core.ui.measurementChangeText
import app.liora.core.ui.measurementText
import app.liora.feature.body.MeasurementChart
import app.liora.feature.body.MeasurementSummary
import app.liora.feature.body.resources.Res
import app.liora.feature.body.resources.body_log
import app.liora.feature.body.resources.detail_change
import app.liora.feature.body.resources.detail_empty
import app.liora.feature.body.resources.detail_entries
import app.liora.feature.body.resources.detail_latest
import app.liora.feature.body.resources.detail_one_day
import app.liora.feature.body.resources.detail_since
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Where a measurement's page leads: back, and to the form for a day (null for today). */
internal class MeasurementDetailNavigation(
    val onBack: () -> Unit,
    val onLog: (day: LocalDate?) -> Unit,
)

@Composable
internal fun MeasurementDetailScreen(
    viewModel: MeasurementDetailViewModel,
    navigation: MeasurementDetailNavigation,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MeasurementDetailContent(
        type = viewModel.type,
        state = uiState,
        navigation = navigation,
        modifier = modifier,
    )
}

@Composable
private fun MeasurementDetailContent(
    type: MeasurementType,
    state: MeasurementDetailUiState,
    navigation: MeasurementDetailNavigation,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(type.label),
                // Beside the Body page on wide screens, there is nothing to go back to.
                navigationIcon = {
                    if (LocalPaneRole.current != PaneRole.Detail) BackButton(onClick = navigation.onBack)
                },
                actions = {
                    LioraIconButton(LioraIcons.Add, stringResource(Res.string.body_log), { navigation.onLog(null) })
                },
            )
        },
    ) { padding ->
        val summary = (state as? MeasurementDetailUiState.Loaded)?.summary
        // Newest first, each with the day before it.
        val entries =
            remember(summary) {
                summary
                    ?.days
                    .orEmpty()
                    .let { days ->
                        days.mapIndexed { i, day -> day to days.getOrNull(i - 1) }
                    }.reversed()
            }
        LazyColumn(
            modifier = Modifier.readableWidth(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp),
        ) {
            when {
                state == MeasurementDetailUiState.Loading -> {
                    Unit
                }

                summary == null -> {
                    item {
                        Text(
                            text = stringResource(Res.string.detail_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }

                else -> {
                    item(key = "overview") {
                        Overview(summary, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    item(key = "entries") {
                        SectionHeader(
                            pluralStringResource(Res.plurals.detail_entries, summary.days.size, summary.days.size),
                        )
                    }
                    items(entries, key = { (day, _) -> day.day.toString() }) { (day, previous) ->
                        EntryRow(type = type, day = day, previous = previous, onClick = { navigation.onLog(day.day) })
                    }
                }
            }
        }
    }
}

/** The latest value and how it moved, over the chart. */
@Composable
private fun Overview(
    summary: MeasurementSummary,
    modifier: Modifier = Modifier,
) {
    val type = summary.type
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Stat(
                label = stringResource(Res.string.detail_latest),
                value = measurementText(type, summary.latest.value),
                detail = rememberDateFormatter().shortDate(summary.latest.day),
            )
            summary.change?.let { change ->
                Stat(
                    label = stringResource(Res.string.detail_change),
                    value = measurementChangeText(type, change.amount),
                    detail = stringResource(Res.string.detail_since, rememberDateFormatter().shortDate(change.since)),
                )
            }
        }
        if (summary.days.size > 1) {
            MeasurementChart(type, summary.days, Modifier.fillMaxWidth().height(ChartHeight))
        } else {
            Text(
                text = stringResource(Res.string.detail_one_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge.tabularNumbers())
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A day's value, and how far it moved from the day before it was taken. Tapping corrects it. */
@Composable
private fun EntryRow(
    type: MeasurementType,
    day: DayValue,
    previous: DayValue?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rememberDateFormatter().longDate(day.day),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(measurementText(type, day.value), style = MaterialTheme.typography.bodyLarge.tabularNumbers())
            if (previous != null) {
                Text(
                    text = measurementChangeText(type, day.value - previous.value),
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val ChartHeight = 200.dp
