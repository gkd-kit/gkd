package li.songe.gkd.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class AlertDialogOptions(
    val text: @Composable (() -> Unit)? = null,
    val title: @Composable (() -> Unit)? = null,
    val onDismissRequest: (() -> Unit)? = null,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: @Composable (() -> Unit)? = null,
)

fun buildDialogOptions(
    title: String,
    text: String,
    confirmText: String,
    confirmAction: () -> Unit,
    dismissText: String? = null,
    dismissAction: (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
): AlertDialogOptions {
    return AlertDialogOptions(
        text = {
            Text(text = text)
        },
        title = {
            Text(text = title)
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = confirmAction) {
                Text(text = confirmText)
            }
        },
        dismissButton = if (dismissText != null && dismissAction != null) {
            {
                TextButton(onClick = dismissAction) {
                    Text(text = dismissText)
                }
            }
        } else {
            null
        },
    )
}

@Composable
fun BuildDialog(stateFlow: MutableStateFlow<AlertDialogOptions?>) {
    val options by stateFlow.collectAsState()
    options?.let {
        AlertDialog(
            text = it.text,
            title = it.title,
            onDismissRequest = it.onDismissRequest ?: { stateFlow.value = null },
            confirmButton = it.confirmButton,
            dismissButton = it.dismissButton,
        )
    }
}

const val DEFAULT_CONFIRM_TEXT = "确定"
const val DEFAULT_DISMISS_TEXT = "取消"

suspend fun MutableStateFlow<AlertDialogOptions?>.getResult(
    title: String,
    text: String,
    confirmText: String = DEFAULT_CONFIRM_TEXT,
    dismissText: String = DEFAULT_DISMISS_TEXT,
): Boolean {
    return suspendCoroutine { s ->
        this.value = buildDialogOptions(
            title = title,
            text = text,
            onDismissRequest = {},
            confirmText = confirmText,
            confirmAction = {
                s.resume(true)
                this.value = null
            },
            dismissText = dismissText,
            dismissAction = {
                s.resume(false)
                this.value = null
            },
        )
    }
}

suspend fun MutableStateFlow<AlertDialogOptions?>.waitResult(
    title: String,
    text: String,
    confirmText: String = DEFAULT_CONFIRM_TEXT,
    dismissText: String = DEFAULT_DISMISS_TEXT,
) {
    val r = getResult(
        title = title,
        text = text,
        confirmText = confirmText,
        dismissText = dismissText,
    )
    if (!r) {
        coroutineContext[Job]?.cancel()
        yield()
    }
}