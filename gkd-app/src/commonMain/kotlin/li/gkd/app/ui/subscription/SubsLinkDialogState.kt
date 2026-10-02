package li.gkd.app.ui.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.gkd.app.network.LocalNetworkUrls
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_cancel
import li.gkd.app.resources.action_ok
import li.gkd.app.resources.link_invalid
import li.gkd.app.resources.subscription_add
import li.gkd.app.resources.subscription_duplicate_link
import li.gkd.app.resources.subscription_edit
import li.gkd.app.resources.subscription_help
import li.gkd.app.resources.subscription_link_input_hint
import li.gkd.app.resources.unchanged
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.text.getSync
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.resume

private data class SubsLinkDialogRequest(
    val initialValue: String,
    val existingUrls: Set<String>,
    val value: String,
)

class SubsLinkDialogState(
    private val toast: (String) -> Unit,
    private val onOpenHelp: () -> Unit,
    private val requestLocalNetworkPermission: suspend () -> Boolean,
) {
    private val requestFlow = MutableStateFlow<SubsLinkDialogRequest?>(null)
    private val requestMutex = Mutex()
    private var currentContinuation: CancellableContinuation<String?>? = null

    private fun complete(value: String?) {
        val continuation = currentContinuation ?: return
        if (continuation.isActive) {
            requestFlow.value = null
            continuation.resume(value)
        }
    }

    private fun updateValue(value: String) {
        val request = requestFlow.value ?: return
        requestFlow.value = request.copy(value = value.trim())
    }

    private fun submit(request: SubsLinkDialogRequest) {
        val value = request.value
        if (!LocalNetworkUrls.isNetworkUrl(value)) {
            toast(Res.string.link_invalid.getSync())
            return
        }
        if (request.initialValue.isNotEmpty() && request.initialValue == value) {
            toast(Res.string.unchanged.getSync())
            complete(null)
            return
        }
        if (value in request.existingUrls) {
            toast(Res.string.subscription_duplicate_link.getSync())
            return
        }
        complete(value)
    }

    private fun cancel() = complete(null)

    private fun openHelp() {
        cancel()
        onOpenHelp()
    }

    suspend fun request(initialValue: String = ""): String? {
        val existingUrls = withContext(Dispatchers.IO) {
            SubscriptionRepository.existingUpdateUrls()
        }
        val value = withContext(Dispatchers.Main.immediate) {
            requestMutex.withLock {
                try {
                    requestFlow.value = SubsLinkDialogRequest(
                        initialValue = initialValue,
                        existingUrls = existingUrls,
                        value = initialValue,
                    )
                    suspendCancellableCoroutine { continuation ->
                        currentContinuation = continuation
                    }
                } finally {
                    currentContinuation = null
                    requestFlow.value = null
                }
            }
        }
        if (value != null && LocalNetworkUrls.isLocalNetworkUrl(value) && !requestLocalNetworkPermission()) {
            return null
        }
        return value
    }

    @Composable
    fun Render() {
        val request by requestFlow.collectAsStateWithLifecycle()
        val currentRequest = request
        if (currentRequest != null) {
            GkAlertDialog(
                properties = DialogProperties(dismissOnClickOutside = false),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (currentRequest.initialValue.isNotEmpty()) {
                                stringResource(Res.string.subscription_edit)
                            } else {
                                stringResource(Res.string.subscription_add)
                            },
                        )
                        GkIconButton(
                            imageVector = GkIcons.HelpOutline,
                            contentDescription = stringResource(Res.string.subscription_help),
                            onClick = ::openHelp,
                        )
                    }
                },
                text = {
                    OutlinedTextField(
                        value = currentRequest.value,
                        onValueChange = ::updateValue,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .autoFocus(),
                        placeholder = {
                            Text(text = stringResource(Res.string.subscription_link_input_hint))
                        },
                        isError = currentRequest.value.isNotEmpty() &&
                                !LocalNetworkUrls.isNetworkUrl(currentRequest.value),
                    )
                },
                onDismissRequest = ::cancel,
                confirmButton = {
                    TextButton(
                        enabled = currentRequest.value.isNotEmpty(),
                        onClick = {
                            submit(currentRequest)
                        },
                    ) {
                        Text(text = stringResource(Res.string.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = ::cancel) {
                        Text(text = stringResource(Res.string.action_cancel))
                    }
                },
            )
        }
    }
}
