package app.liora.feature.body.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.theme.tabularNumbers
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.domain.TimeApart
import app.liora.core.model.MeasurementType
import app.liora.core.model.PhotoPose
import app.liora.core.model.ProgressPhoto
import app.liora.core.ui.StoredPhoto
import app.liora.core.ui.measurementChangeText
import app.liora.core.ui.measurementText
import app.liora.feature.body.PHOTO_ASPECT
import app.liora.feature.body.resources.Res
import app.liora.feature.body.resources.cd_photo
import app.liora.feature.body.resources.compare_after
import app.liora.feature.body.resources.compare_before
import app.liora.feature.body.resources.compare_days_apart
import app.liora.feature.body.resources.compare_hint
import app.liora.feature.body.resources.compare_months_apart
import app.liora.feature.body.resources.compare_need_two
import app.liora.feature.body.resources.compare_same_day
import app.liora.feature.body.resources.compare_title
import app.liora.feature.body.resources.compare_weeks_apart
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CompareScreen(
    viewModel: CompareViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.compare_title),
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        if (!uiState.loading) {
            CompareContent(
                state = uiState,
                onSelectPose = viewModel::selectPose,
                onActivate = viewModel::activate,
                onChoose = viewModel::choose,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun CompareContent(
    state: CompareUiState,
    onSelectPose: (PhotoPose?) -> Unit,
    onActivate: (CompareSide) -> Unit,
    onChoose: (ProgressPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    // On wide windows the photos stop growing before they leave the screen, everything centred with them.
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = MaxContentWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PoseFilter(state.pose, onSelectPose, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        // Two portrait photos side by side.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Side(CompareSide.Before, state.before, state.active == CompareSide.Before, onActivate, Modifier.weight(1f))
            Side(CompareSide.After, state.after, state.active == CompareSide.After, onActivate, Modifier.weight(1f))
        }
        Summary(state, Modifier.padding(horizontal = 16.dp))
        Text(
            text = stringResource(Res.string.compare_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Choices(state, onChoose)
    }
}

/** One side: the photo, which side it is, its day and what the scale said. Tapping makes it the one to swap. */
@Composable
private fun Side(
    side: CompareSide,
    compared: ComparedPhoto?,
    active: Boolean,
    onActivate: (CompareSide) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(PHOTO_ASPECT)
                    .clip(MaterialTheme.shapes.medium)
                    .border(activeBorder(active), MaterialTheme.shapes.medium)
                    .semantics { if (active) selected = true }
                    .clickable { onActivate(side) }
                    .testTag(CompareTags.side(side)),
        ) {
            compared?.let {
                StoredPhoto(
                    path = it.photo.path,
                    contentDescription = stringResource(Res.string.cd_photo, dates.shortDate(it.day)),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Text(
            text =
                stringResource(
                    if (side ==
                        CompareSide.Before
                    ) {
                        Res.string.compare_before
                    } else {
                        Res.string.compare_after
                    },
                ),
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        compared?.let {
            Text(dates.shortDate(it.day), style = MaterialTheme.typography.bodyMedium)
            it.bodyweight?.let { kg ->
                Text(
                    text = measurementText(MeasurementType.Bodyweight, kg),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** How far apart the two are, and how the scale moved: "7 weeks apart · −2.2 kg". */
@Composable
private fun Summary(
    state: CompareUiState,
    modifier: Modifier = Modifier,
) {
    val text =
        when (val apart = state.apart) {
            null -> {
                stringResource(Res.string.compare_need_two)
            }

            else -> {
                listOfNotNull(
                    apartText(apart),
                    state.weightChange?.let { measurementChangeText(MeasurementType.Bodyweight, it) },
                ).joinToString(" · ")
            }
        }
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Composable
private fun apartText(apart: TimeApart): String =
    when {
        apart.count == 0 -> {
            stringResource(Res.string.compare_same_day)
        }

        else -> {
            val plural =
                when (apart.scale) {
                    TimeApart.Scale.Days -> Res.plurals.compare_days_apart
                    TimeApart.Scale.Weeks -> Res.plurals.compare_weeks_apart
                    TimeApart.Scale.Months -> Res.plurals.compare_months_apart
                }
            pluralStringResource(plural, apart.count, apart.count)
        }
    }

/** The photos to put on the active side, oldest first; the two being compared are outlined. */
@Composable
private fun Choices(
    state: CompareUiState,
    onChoose: (ProgressPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    val chosen = setOfNotNull(state.before?.photo?.id, state.after?.photo?.id)
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.choices, key = { it.id }) { photo ->
            StoredPhoto(
                path = photo.path,
                contentDescription =
                    stringResource(
                        Res.string.cd_photo,
                        dates.shortDate(photo.takenAt.toLocalDateTime(TimeZone.currentSystemDefault()).date),
                    ),
                modifier =
                    Modifier
                        .width(ChoiceWidth)
                        .aspectRatio(PHOTO_ASPECT)
                        .clip(MaterialTheme.shapes.small)
                        .border(activeBorder(photo.id in chosen), MaterialTheme.shapes.small)
                        .clickable { onChoose(photo) },
            )
        }
    }
}

@Composable
private fun activeBorder(active: Boolean): BorderStroke =
    BorderStroke(BorderWidth, if (active) MaterialTheme.colorScheme.primary else Color.Transparent)

private val MaxContentWidth = 720.dp
private val ChoiceWidth = 64.dp
private val BorderWidth = 3.dp

/** Test tags for the comparison. */
object CompareTags {
    fun side(side: CompareSide) = "compare.${side.name.lowercase()}"
}
