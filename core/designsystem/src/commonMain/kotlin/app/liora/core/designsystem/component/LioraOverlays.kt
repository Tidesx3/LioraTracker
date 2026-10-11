package app.liora.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.liora.core.designsystem.glass.GlassDefaults
import app.liora.core.designsystem.glass.LocalGlassBackdrop
import app.liora.core.designsystem.glass.glass
import app.liora.core.designsystem.glass.glassRimBrush
import app.liora.core.designsystem.theme.LioraTheme

// Liora's dialogs, sheets, menus, snackbars and bottom bars: Material's, and with Liquid Glass panels of
// dense glass catching the light at their rim. They open in windows of their own, so the glass can't see
// the screen beneath; it's dense enough not to need to. Use these rather than Material's own.

@Composable
fun LioraAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    val glass = LioraTheme.glass
    val shape = AlertDialogDefaults.shape
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = if (glass.enabled) modifier.border(1.dp, glassRimBrush(glass.rim), shape) else modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = shape,
        containerColor = if (glass.enabled) glass.panel else AlertDialogDefaults.containerColor,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LioraBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LioraTheme.glass
    val shape = BottomSheetDefaults.ExpandedShape
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = if (glass.enabled) modifier.border(1.dp, glassRimBrush(glass.rim), shape) else modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = if (glass.enabled) glass.panel else BottomSheetDefaults.ContainerColor,
        content = content,
    )
}

@Composable
fun LioraDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LioraTheme.glass
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = if (glass.enabled) RoundedCornerShape(MENU_CORNER.dp) else MenuDefaults.shape,
        containerColor = if (glass.enabled) glass.panel else MenuDefaults.containerColor,
        border = if (glass.enabled) BorderStroke(1.dp, glassRimBrush(glass.rim)) else null,
        content = content,
    )
}

/** Snackbars, floating at the bottom of the screen: with Liquid Glass, as glass over what's beneath. */
@Composable
fun LioraSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val glass = LioraTheme.glass
    val backdrop = LocalGlassBackdrop.current
    SnackbarHost(hostState, modifier) { data ->
        val shape = RoundedCornerShape(SNACKBAR_CORNER.dp)
        Snackbar(
            snackbarData = data,
            // Material's own sits flush with the screen's edges; glass floats a little off them.
            modifier =
                if (glass.enabled) {
                    Modifier
                        .padding(
                            12.dp,
                        ).glass(backdrop, shape, GlassDefaults.regular())
                } else {
                    Modifier
                },
            shape = if (glass.enabled) shape else SnackbarDefaults.shape,
            containerColor = if (glass.enabled) Color.Transparent else SnackbarDefaults.color,
            contentColor = if (glass.enabled) MaterialTheme.colorScheme.onSurface else SnackbarDefaults.contentColor,
            actionColor = if (glass.enabled) MaterialTheme.colorScheme.primary else SnackbarDefaults.actionColor,
        )
    }
}

/**
 * The bar of actions at the bottom of a screen ([LioraScaffold]'s bottomBar), in [color]. With Liquid
 * Glass it has no color of its own: its buttons float, as glass, over the content scrolling beneath.
 */
@Composable
fun LioraBottomBar(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = if (LioraTheme.glass.enabled) Color.Transparent else color,
        content = content,
    )
}

private const val MENU_CORNER = 20
private const val SNACKBAR_CORNER = 24
