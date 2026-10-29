package li.gkd.app.ui.component

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import li.gkd.app.error.messageResource
import li.gkd.app.resources.*
import li.gkd.app.ui.page.EditorDialog
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkEditorDialogs(
    dialog: EditorDialog?,
    onDismissDialog: () -> Unit,
    onDiscard: () -> Unit,
) {
    when (dialog) {
        EditorDialog.Discard ->
            GkAlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text(stringResource(Res.string.notice_title)) },
                text = { Text(stringResource(Res.string.edit_discard_confirmation)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDismissDialog()
                            onDiscard()
                        },
                    ) {
                        Text(stringResource(Res.string.action_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDialog) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                },
            )
        is EditorDialog.Error -> EditorErrorDialog(dialog.cause.messageResource(), onDismissDialog)
        is EditorDialog.Rejected -> EditorErrorDialog(dialog.message, onDismissDialog)
        null -> Unit
    }
}

@Composable
private fun EditorErrorDialog(message: String, onDismiss: () -> Unit) {
    GkAlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_confirm)) }
        },
    )
}
