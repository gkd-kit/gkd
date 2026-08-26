package li.songe.gkd.ui.component

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
import li.songe.gkd.util.throttle
import kotlin.coroutines.resume

private const val DEFAULT_MESSAGE_CONFIRM_TEXT = "我知道了"
private const val DEFAULT_CONFIRM_TEXT = "确定"
private const val DEFAULT_DISMISS_TEXT = "取消"

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
        confirmText: String = DEFAULT_MESSAGE_CONFIRM_TEXT,
    ) = showMessage(
        title = title,
        text = AnnotatedString(text),
        confirmText = confirmText,
    )

    suspend fun showMessage(
        title: String,
        text: AnnotatedString,
        confirmText: String = DEFAULT_MESSAGE_CONFIRM_TEXT,
    ) {
        request(
            DialogRequest(
                title = title,
                text = text,
                confirmText = confirmText,
                dismissText = null,
                dismissOnRequest = true,
                error = false,
            )
        )
    }

    suspend fun confirm(
        title: String,
        text: String,
        confirmText: String = DEFAULT_CONFIRM_TEXT,
        dismissText: String = DEFAULT_DISMISS_TEXT,
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
        confirmText: String = DEFAULT_CONFIRM_TEXT,
        dismissText: String = DEFAULT_DISMISS_TEXT,
        dismissOnRequest: Boolean = false,
        error: Boolean = false,
    ): Boolean = request(
        DialogRequest(
            title = title,
            text = text,
            confirmText = confirmText,
            dismissText = dismissText,
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
            AppAlertDialog(
                title = { Text(text = currentRequest.title) },
                text = { Text(text = currentRequest.text) },
                onDismissRequest = {
                    if (currentRequest.dismissOnRequest) {
                        dismissCurrent()
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = throttle(::confirmCurrent),
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
                        TextButton(onClick = throttle(::dismissCurrent)) {
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
