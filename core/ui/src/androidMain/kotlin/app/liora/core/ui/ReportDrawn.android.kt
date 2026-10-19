package app.liora.core.ui

import androidx.compose.runtime.Composable

/** Through the activity's `FullyDrawnReporter`; nothing outside an activity (previews, screenshot tests). */
@Composable
actual fun ReportDrawnWhen(ready: () -> Boolean) = androidx.activity.compose.ReportDrawnWhen(predicate = ready)
