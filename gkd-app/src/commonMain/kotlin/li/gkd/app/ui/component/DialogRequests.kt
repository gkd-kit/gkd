package li.gkd.app.ui.component

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_cancel
import li.gkd.app.resources.action_ok
import li.gkd.app.resources.action_understood
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.resume

data class DialogRequest(
    val title: String,
    val text: AnnotatedString,
    val confirmText: String,
    val dismissText: String?,
    val dismissOnRequest: Boolean,
    val error: Boolean,
)

class DialogRequests {
    val currentRequest: StateFlow<DialogRequest?>
        field = MutableStateFlow(null)

    private val requestMutex = Mutex()
    private var currentContinuation: CancellableContinuation<Boolean>? = null

    suspend fun showMessage(
        title: String,
        text: String,
        confirmText: String? = null,
    ) = showMessage(
        title = title,
        text = AnnotatedString(text),
        confirmText = confirmText,
    )

    suspend fun showMessage(
        title: String,
        text: AnnotatedString,
        confirmText: String? = null,
    ) {
        request(
            DialogRequest(
                title = title,
                text = text,
                confirmText = confirmText ?: getString(Res.string.action_understood),
                dismissText = null,
                dismissOnRequest = true,
                error = false,
            )
        )
    }

    suspend fun confirm(
        title: String,
        text: String,
        confirmText: String? = null,
        dismissText: String? = null,
        dismissOnRequest: Boolean = false,
        error: Boolean = false,
    ): Boolean = confirm(
        title = title,
        text = AnnotatedString(text),
        confirmText = confirmText,
        dismissText = dismissText,
        dismissOnRequest = dismissOnRequest,
        error = error,
    )

    suspend fun confirm(
        title: String,
        text: AnnotatedString,
        confirmText: String? = null,
        dismissText: String? = null,
        dismissOnRequest: Boolean = false,
        error: Boolean = false,
    ): Boolean = request(
        DialogRequest(
            title = title,
            text = text,
            confirmText = confirmText ?: getString(Res.string.action_ok),
            dismissText = dismissText ?: getString(Res.string.action_cancel),
            dismissOnRequest = dismissOnRequest,
            error = error,
        )
    )

    private fun confirmCurrent() = completeCurrent(true)

    private fun dismissCurrent() = completeCurrent(false)

    @Composable
    fun Render() {
        val request by currentRequest.collectAsStateWithLifecycle()
        val currentRequest = request
        if (currentRequest != null) {
            GkAlertDialog(
                title = { Text(text = currentRequest.title) },
                text = { Text(text = currentRequest.text) },
                onDismissRequest = {
                    if (currentRequest.dismissOnRequest) {
                        dismissCurrent()
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = ::confirmCurrent,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (currentRequest.error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color.Unspecified
                            },
                        ),
                    ) {
                        Text(text = currentRequest.confirmText)
                    }
                },
                dismissButton = currentRequest.dismissText?.let { dismissText ->
                    {
                        TextButton(onClick = ::dismissCurrent) {
                            Text(text = dismissText)
                        }
                    }
                },
            )
        }
    }

    private suspend fun request(request: DialogRequest): Boolean =
        withContext(Dispatchers.Main.immediate) {
            requestMutex.withLock {
                try {
                    currentRequest.value = request
                    suspendCancellableCoroutine { continuation ->
                        currentContinuation = continuation
                    }
                } finally {
                    currentContinuation = null
                    currentRequest.value = null
                }
            }
        }

    private fun completeCurrent(result: Boolean) {
        val continuation = currentContinuation ?: return
        if (continuation.isActive) {
            continuation.resume(result)
        }
    }
}
