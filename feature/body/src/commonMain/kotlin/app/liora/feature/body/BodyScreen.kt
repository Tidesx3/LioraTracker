package app.liora.feature.body

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.component.SectionHeader
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.layout.readableWidth
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.model.MeasurementType
import app.liora.core.ui.label
import app.liora.core.ui.measurementText
import app.liora.feature.body.resources.Res
import app.liora.feature.body.resources.body_empty_body
import app.liora.feature.body.resources.body_empty_title
import app.liora.feature.body.resources.body_log
import app.liora.feature.body.resources.body_measurements
import app.liora.feature.body.resources.body_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Where the Body page leads. */
internal class BodyNavigation(
    val onBack: () -> Unit,
    val onOpen: (MeasurementType) -> Unit,
    val onLog: () -> Unit,
)

@Composable
internal fun BodyScreen(
    viewModel: BodyViewModel,
    navigation: BodyNavigation,
    selectedType: MeasurementType?,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BodyContent(state = uiState, navigation = navigation, selectedType = selectedType, modifier = modifier)
}

@Composable
private fun BodyContent(
    state: BodyUiState,
    navigation: BodyNavigation,
    selectedType: MeasurementType?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.body_title),
                navigationIcon = { BackButton(onClick = navigation.onBack) },
            )
        },
        floatingActionButton = {
            val label = stringResource(Res.string.body_log)
            ExtendedFloatingActionButton(
                onClick = navigation.onLog,
                icon = { Icon(painterResource(LioraIcons.Add), contentDescription = null) },
                text = { Text(label) },
                // Material clears the text's semantics, which would leave the button unnamed.
                modifier = Modifier.semantics { contentDescription = label },
            )
        },
    ) { padding ->
        when (state) {
            BodyUiState.Loading -> {
                Unit
            }

            BodyUiState.Empty -> {
                EmptyState(
                    icon = LioraIcons.Body,
                    title = stringResource(Res.string.body_empty_title),
                    body = stringResource(Res.string.body_empty_body),
                    modifier = Modifier.padding(padding),
                )
            }

            is BodyUiState.Loaded -> {
                Measurements(
                    state = state,
                    selectedType = selectedType,
                    onOpen = navigation.onOpen,
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = FabClearance),
                )
            }
        }
    }
}

@Composable
private fun Measurements(
    state: BodyUiState.Loaded,
    selectedType: MeasurementType?,
    onOpen: (MeasurementType) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val bodyweight = state.summaries.firstOrNull { it.type == MeasurementType.Bodyweight }
    val others = state.summaries.filter { it.type != MeasurementType.Bodyweight }
    LazyColumn(modifier = modifier.readableWidth(), contentPadding = contentPadding) {
        if (bodyweight != null) {
            item(key = "bodyweight") {
                BodyweightCard(
                    summary = bodyweight,
                    selected = selectedType == MeasurementType.Bodyweight,
                    onClick = { onOpen(MeasurementType.Bodyweight) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (others.isNotEmpty()) {
            item(key = "header") { SectionHeader(stringResource(Res.string.body_measurements)) }
            items(others, key = { it.type.key }) { summary ->
                MeasurementRow(
                    summary = summary,
                    selected = summary.type == selectedType,
                    onClick = { onOpen(summary.type) },
                )
            }
        }
    }
}

/** Bodyweight up front: the latest, how it moved over the month, and its curve. */
@Composable
private fun BodyweightCard(
    summary: MeasurementSummary,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { if (selected) this.selected = true },
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
            ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(summary.type.label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = measurementText(summary.type, summary.latest.value),
                style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
            )
            Text(
                text = summary.change?.let { changeSinceText(summary.type, it) } ?: shortDate(summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.days.size > 1) {
                MeasurementChart(
                    type = summary.type,
                    days = summary.days,
                    modifier = Modifier.fillMaxWidth().height(CardChartHeight).padding(top = 8.dp),
                )
            }
        }
    }
}

/** A measurement's latest value, when it was taken and how it moved. */
@Composable
private fun MeasurementRow(
    summary: MeasurementSummary,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { if (selected) this.selected = true }
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                ).clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(summary.type.label), style = MaterialTheme.typography.titleSmall)
            Text(
                text = summary.change?.let { changeSinceText(summary.type, it) } ?: shortDate(summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = measurementText(summary.type, summary.latest.value),
            style = MaterialTheme.typography.titleMedium.tabularNumbers(),
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun shortDate(summary: MeasurementSummary): String = rememberDateFormatter().shortDate(summary.latest.day)

private val CardChartHeight = 160.dp

/** Room under the list so the last row can scroll out from under the button. */
private val FabClearance = 96.dp
