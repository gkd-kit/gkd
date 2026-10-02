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
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_cancel
import li.gkd.app.resources.action_confirm
import li.gkd.app.resources.edit_discard_confirmation
import li.gkd.app.resources.notice_title
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
    GkEditorLayout(title, saveEnabled, requestClose, onSave = {
        actionScope.launch {
            try {
                onSave(); beforeClose(); onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            }
        }
    }, content = content)
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
