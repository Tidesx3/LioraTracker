package app.liora.core.ui

import androidx.compose.runtime.Composable

/**
 * Tells the platform the screen Liora opens on shows its content once [ready] is true. On Android that's the
 * activity's fully drawn report, which the startup benchmark measures (benchmark/); a screen that isn't
 * where the app starts never calls it.
 */
@Composable
expect fun ReportDrawnWhen(ready: () -> Boolean)
