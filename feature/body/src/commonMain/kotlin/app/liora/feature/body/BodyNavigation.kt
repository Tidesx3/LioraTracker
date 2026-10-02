package app.liora.feature.body

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.liora.core.designsystem.component.EmptyState
import app.liora.core.designsystem.icon.LioraIcons
import app.liora.core.model.MeasurementType
import app.liora.core.navigation.BodyRoute
import app.liora.core.navigation.ComparePhotosRoute
import app.liora.core.navigation.ListDetail
import app.liora.core.navigation.LogMeasurementsRoute
import app.liora.core.navigation.MeasurementDetailRoute
import app.liora.core.navigation.Navigator
import app.liora.core.navigation.ProgressPhotoRoute
import app.liora.core.navigation.ProgressPhotosRoute
import app.liora.feature.body.detail.MeasurementDetailNavigation
import app.liora.feature.body.detail.MeasurementDetailScreen
import app.liora.feature.body.detail.MeasurementDetailViewModel
import app.liora.feature.body.log.LogMeasurementsScreen
import app.liora.feature.body.log.LogMeasurementsViewModel
import app.liora.feature.body.photos.CompareScreen
import app.liora.feature.body.photos.CompareViewModel
import app.liora.feature.body.photos.PhotoNavigation
import app.liora.feature.body.photos.PhotoScreen
import app.liora.feature.body.photos.PhotoViewModel
import app.liora.feature.body.photos.PhotosNavigation
import app.liora.feature.body.photos.PhotosScreen
import app.liora.feature.body.photos.PhotosViewModel
import app.liora.feature.body.resources.Res
import app.liora.feature.body.resources.body_pick_body
import app.liora.feature.body.resources.body_pick_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

fun EntryProviderScope<NavKey>.bodyEntries(navigator: Navigator) {
    // On wide screens a measurement, the form or the photo gallery opens beside the overview.
    entry<BodyRoute>(
        metadata =
            ListDetail.listPane {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = LioraIcons.Body,
                        title = stringResource(Res.string.body_pick_title),
                        body = stringResource(Res.string.body_pick_body),
                    )
                }
            },
    ) {
        BodyScreen(
            viewModel = koinViewModel(),
            navigation =
                BodyNavigation(
                    // With a measurement open beside it, back closes both.
                    onBack = { navigator.close(BodyRoute) },
                    onOpen = { navigator.openFromList(BodyRoute, MeasurementDetailRoute(it.key)) },
                    onOpenPhotos = { navigator.openFromList(BodyRoute, ProgressPhotosRoute) },
                    onLog = { navigator.navigate(LogMeasurementsRoute()) },
                ),
            selection =
                BodySelection(
                    type =
                        navigator.backStack
                            .filterIsInstance<MeasurementDetailRoute>()
                            .lastOrNull()
                            ?.let { MeasurementType.fromKey(it.typeKey) },
                    photos = ProgressPhotosRoute in navigator.backStack,
                ),
        )
    }
    entry<MeasurementDetailRoute>(metadata = ListDetail.detailPane()) { route ->
        // Keys come from this app's own routes, so an unknown one can't happen in practice.
        val type = MeasurementType.fromKey(route.typeKey) ?: return@entry
        MeasurementDetailScreen(
            viewModel = koinViewModel(key = route.typeKey) { parametersOf(type) },
            navigation =
                MeasurementDetailNavigation(
                    onBack = navigator::goBack,
                    onLog = { day -> navigator.navigate(LogMeasurementsRoute(day?.toString())) },
                ),
        )
    }
    entry<LogMeasurementsRoute>(metadata = ListDetail.detailPane()) { route ->
        LogMeasurementsScreen(
            viewModel = koinViewModel(key = route.toString()) { parametersOf(route) },
            onClose = navigator::goBack,
            onDone = navigator::goBack,
        )
    }
    photoEntries(navigator)
}

private fun EntryProviderScope<NavKey>.photoEntries(navigator: Navigator) {
    entry<ProgressPhotosRoute>(metadata = ListDetail.detailPane()) {
        PhotosScreen(
            viewModel = koinViewModel(),
            navigation =
                PhotosNavigation(
                    onBack = navigator::goBack,
                    onOpen = { navigator.navigate(ProgressPhotoRoute(it)) },
                    onCompare = { navigator.navigate(ComparePhotosRoute()) },
                ),
        )
    }
    // A photo and the comparison take the whole window: photos want the room.
    entry<ProgressPhotoRoute> { route ->
        PhotoScreen(
            viewModel = koinViewModel(key = route.photoId) { parametersOf(route.photoId) },
            navigation =
                PhotoNavigation(
                    onBack = navigator::goBack,
                    onCompare = { navigator.navigate(ComparePhotosRoute(afterId = it)) },
                ),
        )
    }
    entry<ComparePhotosRoute> { route ->
        CompareScreen(
            viewModel = koinViewModel(key = route.toString()) { parametersOf(route) },
            onBack = navigator::goBack,
        )
    }
}

val bodyModule =
    module {
        viewModelOf(::BodyViewModel)
        viewModel { (type: MeasurementType) -> MeasurementDetailViewModel(type, get()) }
        viewModel { (route: LogMeasurementsRoute) -> LogMeasurementsViewModel(route, get(), get(), get()) }
        viewModelOf(::PhotosViewModel)
        viewModel { (photoId: String) -> PhotoViewModel(photoId, get(), get(), get()) }
        viewModel { (route: ComparePhotosRoute) -> CompareViewModel(route, get(), get()) }
    }
