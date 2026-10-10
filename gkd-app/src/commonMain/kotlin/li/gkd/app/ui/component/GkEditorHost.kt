package li.gkd.app.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import li.gkd.app.resources.*
import li.gkd.app.ui.text.subscriptionMessageResource
import org.jetbrains.compose.resources.stringResource

/** Shared save/discard/error behavior. Platform adapters supply navigation and keyboard handling. */
@Composable
fun GkEditorHost(
    title: @Composable () -> Unit,
    hasChanges: suspend () -> Boolean,
    onSave: suspend () -> Unit,
    onClose: () -> Unit,
    saveEnabled: Boolean = true,
    onSaved: () -> Unit = onClose,
    beforeClose: () -> Unit = {},
    backHandler: @Composable (() -> Unit) -> Unit = {},
    actionScope: CoroutineScope = rememberCoroutineScope(),
    content: @Composable (PaddingValues) -> Unit,
) {
    GkEditorActions(hasChanges, onSave, onClose, onSaved, beforeClose, backHandler, actionScope) { close, save ->
        GkEditorLayout(title, saveEnabled, close, save, content)
    }
}

/** Shared editor actions for both standalone and inline editor layouts. */
@Composable
fun GkEditorActions(
    hasChanges: suspend () -> Boolean,
    onSave: suspend () -> Unit,
    onClose: () -> Unit,
    onSaved: () -> Unit = onClose,
    beforeClose: () -> Unit = {},
    backHandler: @Composable (() -> Unit) -> Unit = {},
    actionScope: CoroutineScope = rememberCoroutineScope(),
    content: @Composable (onClose: () -> Unit, onSave: () -> Unit) -> Unit,
) {
    var discard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Exception?>(null) }
    val requestClose: () -> Unit = {
        actionScope.launch {
            try {
                beforeClose()
                if (hasChanges()) discard = true else onClose()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            }
        }
    }
    backHandler(requestClose)
    content(requestClose, {
        actionScope.launch {
            try {
                onSave(); beforeClose(); onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            }
        }
    })
    if (discard) {
        GkAlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(stringResource(Res.string.notice_title)) },
            text = { Text(stringResource(Res.string.edit_discard_confirmation)) },
            confirmButton = {
                TextButton(onClick = { discard = false; onClose() }) {
                    Text(
                        stringResource(Res.string.action_confirm)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    discard = false
                }) { Text(stringResource(Res.string.action_cancel)) }
            })
    }
    if (error != null) {
        GkAlertDialog(
            onDismissRequest = { error = null },
            text = { Text(error?.subscriptionMessageResource().orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    error = null
                }) { Text(stringResource(Res.string.action_confirm)) }
            })
    }
}
