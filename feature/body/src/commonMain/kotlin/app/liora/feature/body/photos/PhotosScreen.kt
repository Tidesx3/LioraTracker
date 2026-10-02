package app.liora.feature.body.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liora.core.designsystem.component.BackButton
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.component.LioraIconButton
import app.liora.core.designsystem.component.LioraTopAppBar
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.designsystem.util.rememberDateFormatter
import app.liora.core.model.MeasurementType
import app.liora.core.model.PhotoPose
import app.liora.core.model.ProgressPhoto
import app.liora.core.navigation.LocalPaneRole
import app.liora.core.navigation.PaneRole
import app.liora.core.ui.StoredPhoto
import app.liora.core.ui.label
import app.liora.core.ui.measurementText
import app.liora.core.ui.rememberPhotoSource
import app.liora.feature.body.BodyTags
import app.liora.feature.body.PHOTO_ASPECT
import app.liora.feature.body.resources.Res
import app.liora.feature.body.resources.cd_photo
import app.liora.feature.body.resources.photos_add
import app.liora.feature.body.resources.photos_add_title
import app.liora.feature.body.resources.photos_all
import app.liora.feature.body.resources.photos_choose
import app.liora.feature.body.resources.photos_compare
import app.liora.feature.body.resources.photos_empty_body
import app.liora.feature.body.resources.photos_empty_title
import app.liora.feature.body.resources.photos_failed
import app.liora.feature.body.resources.photos_pose
import app.liora.feature.body.resources.photos_take
import app.liora.feature.body.resources.photos_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Where the gallery leads. */
internal class PhotosNavigation(
    val onBack: () -> Unit,
    val onOpen: (photoId: String) -> Unit,
    val onCompare: () -> Unit,
)

@Composable
internal fun PhotosScreen(
    viewModel: PhotosViewModel,
    navigation: PhotosNavigation,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var choosingSource by rememberSaveable { mutableStateOf(false) }
    // The pose new photos get; it starts as the one being looked at.
    var newPose by rememberSaveable { mutableStateOf<PhotoPose?>(null) }
    val currentPose by rememberUpdatedState(newPose)
    val source = rememberPhotoSource { sources -> viewModel.add(sources, currentPose) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(uiState.failed) {
        if (uiState.failed > 0) {
            snackbar.showSnackbar(getPluralString(Res.plurals.photos_failed, uiState.failed, uiState.failed))
            viewModel.failureShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LioraTopAppBar(
                title = stringResource(Res.string.photos_title),
                // Beside the Body page on wide screens, there is nothing to go back to.
                navigationIcon = {
                    if (LocalPaneRole.current != PaneRole.Detail) BackButton(onClick = navigation.onBack)
                },
                actions = {
                    if (uiState.total > 1) {
                        LioraIconButton(
                            LioraIcons.Compare,
                            stringResource(Res.string.photos_compare),
                            navigation.onCompare,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            val label = stringResource(Res.string.photos_add)
            ExtendedFloatingActionButton(
                onClick = {
                    newPose = uiState.pose
                    choosingSource = true
                },
                icon = { Icon(painterResource(LioraIcons.Add), contentDescription = null) },
                text = { Text(label) },
                // Material clears the text's semantics, which would leave the button unnamed.
                modifier = Modifier.semantics { contentDescription = label },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (uiState.adding > 0) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                uiState.loading -> {
                    Unit
                }

                uiState.total == 0 -> {
                    EmptyState(
                        icon = LioraIcons.Photos,
                        title = stringResource(Res.string.photos_empty_title),
                        body = stringResource(Res.string.photos_empty_body),
                    )
                }

                else -> {
                    PoseFilter(uiState.pose, viewModel::selectPose, Modifier.padding(horizontal = 16.dp))
                    PhotoGrid(uiState.days, navigation.onOpen)
                }
            }
        }
    }
    if (choosingSource) {
        AddPhotoSheet(
            pose = newPose,
            onPose = { newPose = it },
            onTake = {
                choosingSource = false
                source.takePhoto()
            },
            onChoose = {
                choosingSource = false
                source.pickPhotos()
            },
            onDismiss = { choosingSource = false },
        )
    }
}

/** All photos, or one pose. */
@Composable
internal fun PoseFilter(
    pose: PhotoPose?,
    onSelect: (PhotoPose?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = pose == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(Res.string.photos_all)) },
        )
        PhotoPose.entries.forEach { option ->
            FilterChip(
                selected = pose == option,
                onClick = { onSelect(option) },
                label = { Text(stringResource(option.label)) },
            )
        }
    }
}

/** Photos by day, newest first, each day under its date and what the scale said. */
@Composable
private fun PhotoGrid(
    days: List<PhotoDay>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TileWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = FabClearance),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { day ->
            item(key = "day-${day.day}", span = { GridItemSpan(maxLineSpan) }) { DayHeader(day) }
            items(day.photos, key = { it.id }) { photo ->
                PhotoTile(photo, day, onClick = { onOpen(photo.id) })
            }
        }
    }
}

@Composable
private fun DayHeader(
    day: PhotoDay,
    modifier: Modifier = Modifier,
) {
    val date = rememberDateFormatter().longDate(day.day)
    Text(
        text = day.bodyweight?.let { "$date · ${measurementText(MeasurementType.Bodyweight, it)}" } ?: date,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** A photo in its portrait frame, its pose in the corner. */
@Composable
private fun PhotoTile(
    photo: ProgressPhoto,
    day: PhotoDay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(PHOTO_ASPECT)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .testTag(BodyTags.PHOTO),
    ) {
        StoredPhoto(
            path = photo.path,
            contentDescription = stringResource(Res.string.cd_photo, rememberDateFormatter().shortDate(day.day)),
            modifier = Modifier.matchParentSize(),
        )
        photo.pose?.let { pose ->
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.scrim.copy(alpha = BADGE_ALPHA),
                // Over a photo, light or dark theme alike.
                contentColor = Color.White,
            ) {
                Text(
                    text = stringResource(pose.label),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** How new photos come in, and the pose they get. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPhotoSheet(
    pose: PhotoPose?,
    onPose: (PhotoPose?) -> Unit,
    onTake: () -> Unit,
    onChoose: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(Res.string.photos_add_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = stringResource(Res.string.photos_pose),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
            )
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoPose.entries.forEach { option ->
                    FilterChip(
                        selected = pose == option,
                        onClick = { onPose(option.takeUnless { it == pose }) },
                        label = { Text(stringResource(option.label)) },
                    )
                }
            }
            SourceItem(LioraIcons.Camera, stringResource(Res.string.photos_take), onTake)
            SourceItem(LioraIcons.Photos, stringResource(Res.string.photos_choose), onChoose)
        }
    }
}

@Composable
private fun SourceItem(
    icon: DrawableResource,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

private val TileWidth = 104.dp
private val FabClearance = 96.dp
private const val BADGE_ALPHA = 0.6f
