package li.songe.gkd.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


private data class DialogParams(
    val title: String,
    val text: String? = null,
    val resolve: () -> Unit,
    val reject: () -> Unit
)

private val dialogParamsFlow = MutableStateFlow<DialogParams?>(null)

@Composable
fun ConfirmDialog() {
    val dialogParams = dialogParamsFlow.collectAsState().value
    if (dialogParams != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(text = dialogParams.title) },
            text = if (dialogParams.text != null) {
                {
                    Text(text = dialogParams.text)
                }
            } else null,
            confirmButton = {
                TextButton(onClick = dialogParams.resolve) {
                    Text(text = "是", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = dialogParams.reject) {
                    Text(text = "否")
                }
            }
        )
    }
    DisposableEffect(key1 = null, effect = {
        onDispose {
            val d = dialogParamsFlow.value
            if (d != null) {
                d.reject.invoke()
                dialogParamsFlow.value = null
            }
        }
    })
}

suspend fun getDialogResult(title: String, text: String? = null): Boolean {
    return suspendCoroutine { s ->
        dialogParamsFlow.value = DialogParams(
            title = title,
            text = text,
            resolve = { s.resume(true);dialogParamsFlow.value = null },
            reject = { s.resume(false);dialogParamsFlow.value = null }
        )
    }
}