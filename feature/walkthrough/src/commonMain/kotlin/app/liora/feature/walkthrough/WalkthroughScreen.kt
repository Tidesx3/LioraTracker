package app.liora.feature.walkthrough

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.liora.core.designsystem.component.LioraCard
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.illustration.Illustration
import app.liora.core.designsystem.illustration.LioraIllustration
import app.liora.core.designsystem.layout.currentWindowLayout
import app.liora.core.designsystem.theme.LioraTheme
import app.liora.core.model.WeightUnit
import app.liora.core.ui.CloudConnection
import app.liora.core.ui.LocalCloudConsent
import app.liora.core.ui.rememberFileOpener
import app.liora.feature.walkthrough.resources.Res
import app.liora.feature.walkthrough.resources.cd_page
import app.liora.feature.walkthrough.resources.data_body
import app.liora.feature.walkthrough.resources.data_connect
import app.liora.feature.walkthrough.resources.data_connected
import app.liora.feature.walkthrough.resources.data_failed
import app.liora.feature.walkthrough.resources.data_import
import app.liora.feature.walkthrough.resources.data_second_phone
import app.liora.feature.walkthrough.resources.data_title
import app.liora.feature.walkthrough.resources.finish
import app.liora.feature.walkthrough.resources.logging_body
import app.liora.feature.walkthrough.resources.logging_title
import app.liora.feature.walkthrough.resources.next
import app.liora.feature.walkthrough.resources.progress_body
import app.liora.feature.walkthrough.resources.progress_title
import app.liora.feature.walkthrough.resources.routines_body
import app.liora.feature.walkthrough.resources.routines_title
import app.liora.feature.walkthrough.resources.skip
import app.liora.feature.walkthrough.resources.welcome_body
import app.liora.feature.walkthrough.resources.welcome_kilograms
import app.liora.feature.walkthrough.resources.welcome_pounds
import app.liora.feature.walkthrough.resources.welcome_title
import app.liora.feature.walkthrough.resources.welcome_units
import app.liora.feature.walkthrough.resources.welcome_units_hint
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The first-launch walkthrough: how Liora works in a few pages, the choice of kilograms or pounds, and an
 * offer to sync with Google Drive. [onImport] gets a Hevy or Strong export picked on the last page; the
 * walkthrough finishes as it's handed over.
 */
@Composable
fun WalkthroughScreen(
    viewModel: WalkthroughViewModel,
    onImport: (source: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val consent = LocalCloudConsent.current
    val scope = rememberCoroutineScope()
    var connecting by remember { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val importOpener =
        rememberFileOpener(IMPORT_TYPES) { source ->
            onImport(source)
            viewModel.finish()
        }
    WalkthroughContent(
        state = uiState,
        connection = Connection(connecting = connecting, failed = failed),
        actions =
            WalkthroughActions(
                onWeightUnit = viewModel::chooseWeightUnit,
                onConnect = {
                    scope.launch {
                        connecting = true
                        failed = false
                        val connection = consent.connect()
                        connecting = false
                        when (connection) {
                            is CloudConnection.Connected -> viewModel.connected(connection.account)
                            CloudConnection.Declined -> Unit
                            CloudConnection.Failed -> failed = true
                        }
                    }
                },
                onImport = importOpener::open,
                onFinish = viewModel::finish,
            ),
        modifier = modifier,
    )
}

/** CSV files go by many types, depending on where they're kept. */
private val IMPORT_TYPES =
    listOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")

/** How asking Google for access is going. */
private data class Connection(
    val connecting: Boolean,
    /** Google couldn't be asked. */
    val failed: Boolean,
)

private class WalkthroughActions(
    val onWeightUnit: (WeightUnit) -> Unit,
    val onConnect: () -> Unit,
    val onImport: () -> Unit,
    val onFinish: () -> Unit,
)

/** The pages, in order: three on how Liora works between two that ask something. */
private enum class Page(
    val illustration: Illustration,
    val title: StringResource,
    val body: StringResource,
) {
    Welcome(Illustration.Welcome, Res.string.welcome_title, Res.string.welcome_body),
    Routines(Illustration.Routines, Res.string.routines_title, Res.string.routines_body),
    Logging(Illustration.Workout, Res.string.logging_title, Res.string.logging_body),
    Progress(Illustration.Chart, Res.string.progress_title, Res.string.progress_body),
    Data(Illustration.Sync, Res.string.data_title, Res.string.data_body),
}

@Composable
private fun WalkthroughContent(
    state: WalkthroughUiState,
    connection: Connection,
    actions: WalkthroughActions,
    modifier: Modifier = Modifier,
) {
    val pages = Page.entries
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val goTo = { page: Int -> scope.launch { pager.animateScrollToPage(page) } }
    val last = pages.lastIndex
    // Back steps through the pages; on the first one it leaves Liora as usual, and the walkthrough waits.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = pager.currentPage > 0,
        onBackCompleted = { goTo(pager.currentPage - 1) },
    )
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // Skip goes to the last page: what's there are choices, not tour.
            Box(
                Modifier.walkthroughWidth().height(56.dp).padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (pager.currentPage < last) {
                    TextButton(onClick = { goTo(last) }) { Text(stringResource(Res.string.skip)) }
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                key = { pages[it].name },
            ) { index ->
                PageContent(pages[index], state, connection, actions)
            }
            Row(
                Modifier.walkthroughWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageDots(current = pager.currentPage, count = pages.size, modifier = Modifier.weight(1f))
                if (pager.currentPage < last) {
                    Button(onClick = { goTo(pager.currentPage + 1) }) { Text(stringResource(Res.string.next)) }
                } else {
                    Button(onClick = actions.onFinish) { Text(stringResource(Res.string.finish)) }
                }
            }
        }
    }
}

/**
 * One page: its illustration, title and text, and what it asks or shows. Above the text on a phone; on a
 * wide screen beside it.
 */
@Composable
private fun PageContent(
    page: Page,
    state: WalkthroughUiState,
    connection: Connection,
    actions: WalkthroughActions,
) {
    val wide = currentWindowLayout().twoPane
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Centred when it fits, scrolling when it doesn't (a phone turned sideways).
        val fill = Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState())
        if (wide) {
            Row(
                fill.walkthroughWidth().padding(horizontal = 48.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LioraIllustration(page.illustration, Modifier.weight(1f))
                PageText(page, state, connection, actions, wide = true, modifier = Modifier.weight(1f))
            }
        } else {
            Column(
                fill.padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                LioraIllustration(page.illustration, Modifier.widthIn(max = 280.dp).fillMaxWidth())
                PageText(page, state, connection, actions, wide = false)
            }
        }
    }
}

@Composable
private fun PageText(
    page: Page,
    state: WalkthroughUiState,
    connection: Connection,
    actions: WalkthroughActions,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val align = if (wide) TextAlign.Start else TextAlign.Center
    val alignment = if (wide) Alignment.Start else Alignment.CenterHorizontally
    Column(modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(page.title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = align,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
        )
        when (page) {
            Page.Welcome -> {
                UnitChoice(state.weightUnit, actions.onWeightUnit, alignment, align)
            }

            Page.Logging -> {
                DemoSet(alignment, Modifier.padding(top = 8.dp))
            }

            Page.Data -> {
                DataOffer(state.syncAccount, connection, actions, alignment, align)
            }

            Page.Routines, Page.Progress -> {
                Unit
            }
        }
    }
}

/** Kilograms or pounds, asked first so the demo set shows the right one. */
@Composable
private fun UnitChoice(
    selected: WeightUnit?,
    onChoose: (WeightUnit) -> Unit,
    alignment: Alignment.Horizontal,
    align: TextAlign,
) {
    Column(
        Modifier.padding(top = 8.dp),
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(Res.string.welcome_units), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow {
            WeightUnit.entries.forEachIndexed { index, unit ->
                SegmentedButton(
                    selected = unit == selected,
                    onClick = { onChoose(unit) },
                    shape = SegmentedButtonDefaults.itemShape(index, WeightUnit.entries.size),
                ) {
                    Text(
                        stringResource(
                            when (unit) {
                                WeightUnit.Kilogram -> Res.string.welcome_kilograms
                                WeightUnit.Pound -> Res.string.welcome_pounds
                            },
                        ),
                    )
                }
            }
        }
        Text(
            text = stringResource(Res.string.welcome_units_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
        )
    }
}

/** Google Drive sync, as Settings › Sync offers it, and the way in for a Hevy or Strong history. */
@Composable
private fun DataOffer(
    account: String?,
    connection: Connection,
    actions: WalkthroughActions,
    alignment: Alignment.Horizontal,
    align: TextAlign,
) {
    Column(
        Modifier.padding(top = 8.dp),
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (account == null) {
            FilledTonalButton(onClick = actions.onConnect, enabled = !connection.connecting) {
                Text(stringResource(Res.string.data_connect))
            }
            val colors = MaterialTheme.colorScheme
            Text(
                text = stringResource(if (connection.failed) Res.string.data_failed else Res.string.data_second_phone),
                style = MaterialTheme.typography.bodyMedium,
                color = if (connection.failed) colors.error else colors.onSurfaceVariant,
                textAlign = align,
            )
        } else {
            LioraCard {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(LioraIcons.Check),
                        contentDescription = null,
                        tint = LioraTheme.colors.completed,
                    )
                    Text(
                        text = stringResource(Res.string.data_connected, account),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        TextButton(onClick = actions.onImport) {
            Text(stringResource(Res.string.data_import))
        }
    }
}

/** Where the walkthrough stands, as dots: the current page's is the long one. */
@Composable
private fun PageDots(
    current: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.cd_page, current + 1, count)
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val width by animateDpAsState(if (index == current) DotLength else DotSize)
            Box(
                Modifier
                    .size(width = width, height = DotSize)
                    .background(
                        if (index ==
                            current
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

/** As wide as the window, up to [WideWidth], and centred in anything wider. */
private fun Modifier.walkthroughWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = WideWidth).fillMaxWidth()

private val WideWidth = 840.dp
private val DotSize = 8.dp
private val DotLength = 24.dp
