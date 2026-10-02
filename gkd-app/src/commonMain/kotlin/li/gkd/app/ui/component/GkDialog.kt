package li.gkd.app.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.ui.window.Dialog as PlatformDialog

/** Optional host back dispatcher; Android dialogs already own their native back handling. */
val LocalOverlayBackHandler =
    staticCompositionLocalOf<@Composable (() -> Unit) -> Unit> { { _ -> } }

private data class DialogRegistration(
    val order: Long,
    val registrationOrder: Long,
)

private object DialogRegistry {
    private var nextOrder = 0L
    private var nextRegistrationOrder = 0L
    private val registrations = mutableStateMapOf<Any, DialogRegistration>()

    val topToken: Any?
        get() = registrations.maxWithOrNull(
            compareBy<Map.Entry<Any, DialogRegistration>> { it.value.order }
                .thenBy { it.value.registrationOrder }
        )?.key

    fun createOrder(): Long = ++nextOrder

    fun register(token: Any, order: Long) {
        nextOrder = maxOf(nextOrder, order)
        registrations[token] = DialogRegistration(
            order = order,
            registrationOrder = ++nextRegistrationOrder,
        )
    }

    fun unregister(token: Any) {
        registrations.remove(token)
    }
}

@Composable
private fun DialogLayer(content: @Composable () -> Unit) {
    val order = rememberSaveable { DialogRegistry.createOrder() }
    val token = remember { Any() }

    DisposableEffect(Unit) {
        DialogRegistry.register(token, order)
        onDispose {
            DialogRegistry.unregister(token)
        }
    }

    if (DialogRegistry.topToken === token) {
        content()
    }
}

@Composable
fun GkDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    DialogLayer {
        if (properties.dismissOnBackPress) LocalOverlayBackHandler.current(onDismissRequest)
        PlatformDialog(
            onDismissRequest = onDismissRequest,
            properties = properties,
            content = content,
        )
    }
}

@Composable
fun GkModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    sheetGesturesEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    LocalOverlayBackHandler.current(onDismissRequest)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        content = content,
    )
}

@Composable
fun GkAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    DialogLayer {
        if (properties.dismissOnBackPress) LocalOverlayBackHandler.current(onDismissRequest)
        MaterialAlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties,
        )
    }
}
