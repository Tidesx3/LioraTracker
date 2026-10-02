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
import app.liora.core.navigation.ListDetail
import app.liora.core.navigation.LogMeasurementsRoute
import app.liora.core.navigation.MeasurementDetailRoute
import app.liora.core.navigation.Navigator
import app.liora.feature.body.detail.MeasurementDetailNavigation
import app.liora.feature.body.detail.MeasurementDetailScreen
import app.liora.feature.body.detail.MeasurementDetailViewModel
import app.liora.feature.body.log.LogMeasurementsScreen
import app.liora.feature.body.log.LogMeasurementsViewModel
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
    // On wide screens a measurement, or the form, opens beside the overview.
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
                    onLog = { navigator.navigate(LogMeasurementsRoute()) },
                ),
            selectedType =
                navigator.backStack
                    .filterIsInstance<MeasurementDetailRoute>()
                    .lastOrNull()
                    ?.let { MeasurementType.fromKey(it.typeKey) },
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
}

val bodyModule =
    module {
        viewModelOf(::BodyViewModel)
        viewModel { (type: MeasurementType) -> MeasurementDetailViewModel(type, get()) }
        viewModel { (route: LogMeasurementsRoute) -> LogMeasurementsViewModel(route, get(), get()) }
    }
