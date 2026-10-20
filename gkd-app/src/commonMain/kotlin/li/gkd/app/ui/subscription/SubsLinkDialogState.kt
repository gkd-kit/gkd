package li.gkd.app.ui.subscription

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.gkd.app.network.AppLinks
import li.gkd.app.network.LocalNetworkUrls
import li.gkd.app.permission.AppPermission
import li.gkd.app.permission.PermissionRequester
import li.gkd.app.resources.*
import li.gkd.app.subscription.SubscriptionAddStage
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.subscription.SubscriptionResult
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.navigation.AppNavigator
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.copyText
import org.jetbrains.compose.resources.stringResource

private data class SubsLinkDialogRequest(
    val initialValue: String,
    val existingUrls: Set<String>,
    val value: String,
    val stage: SubscriptionAddStage? = null,
    val error: String? = null,
)

class SubsLinkDialogState(
    private val navigator: AppNavigator,
    private val permissions: PermissionRequester,
    private val showText: (String) -> Unit,
) {
    private val requestFlow = MutableStateFlow<SubsLinkDialogRequest?>(null)
    private val requestMutex = Mutex()
    private var requestScope: CoroutineScope? = null
    private var addingJob: Job? = null
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
        requestFlow.value = request.copy(value = value.trim(), error = null)
    }

    private fun submit(request: SubsLinkDialogRequest) {
        val value = request.value
        if (!LocalNetworkUrls.isNetworkUrl(value)) {
            ToastUtils.show(Res.string.link_invalid.getSync())
            return
        }
        if (value in request.existingUrls) {
            ToastUtils.show(Res.string.subscription_duplicate_link.getSync())
            return
        }
        if (request.initialValue.isNotEmpty()) {
            complete(value)
            return
        }
        if (addingJob?.isActive == true) return
        requestFlow.value = request.copy(stage = SubscriptionAddStage.Checking, error = null)
        addingJob = requestScope?.launch {
            if (LocalNetworkUrls.isLocalNetworkUrl(value) &&
                !permissions.ensurePermissions(AppPermission.LocalNetwork)
            ) {
                requestFlow.value = request.copy(stage = null)
                return@launch
            }
            val result = SubscriptionRepository.addOrModifyRemote(value) { stage ->
                withContext(Dispatchers.Main.immediate) {
                    requestFlow.value = requestFlow.value?.copy(stage = stage)
                }
            }
            if (result is SubscriptionResult.Success) {
                result.message()?.let { ToastUtils.show(it) }
                complete(value)
            } else {
                requestFlow.value = request.copy(error = result.message())
            }
        }
    }

    private fun cancel() {
        if (requestFlow.value?.stage == SubscriptionAddStage.Saving) return
        addingJob?.cancel()
        complete(null)
    }

    private fun openHelp() {
        cancel()
        navigator.openWebPage(AppLinks.SubscriptionHelp)
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
                    coroutineScope {
                        requestScope = this
                        suspendCancellableCoroutine { continuation ->
                            currentContinuation = continuation
                        }
                    }
                } finally {
                    addingJob?.cancel()
                    addingJob = null
                    requestScope = null
                    currentContinuation = null
                    requestFlow.value = null
                }
            }
        }
        return value
    }

    @Composable
    fun Render() {
        val request by requestFlow.collectAsStateWithLifecycle()
        val currentRequest = request
        if (currentRequest != null) {
            val busy = currentRequest.stage != null
            val saving = currentRequest.stage == SubscriptionAddStage.Saving
            val editing = currentRequest.initialValue.isNotEmpty()
            GkAlertDialog(
                textToButtonsSpacing = if (editing) null else 4.dp,
                properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = !saving),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (editing) {
                                stringResource(Res.string.subscription_link)
                            } else {
                                stringResource(Res.string.subscription_add)
                            },
                        )
                        GkIconButton(
                            imageVector = GkIcons.HelpOutline,
                            contentDescription = stringResource(Res.string.subscription_help),
                            enabled = !busy,
                            onClick = ::openHelp,
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            enabled = !busy,
                            value = currentRequest.value,
                            onValueChange = ::updateValue,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (editing) Modifier else Modifier.autoFocus()),
                            placeholder = {
                                Text(text = stringResource(Res.string.subscription_link_input_hint))
                            },
                            isError = currentRequest.value.isNotEmpty() &&
                                    !LocalNetworkUrls.isNetworkUrl(currentRequest.value),
                        )
                        if (!editing) {
                            val lineHeight = with(LocalDensity.current) {
                                LocalTextStyle.current.lineHeight.toDp()
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth().height(lineHeight),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (busy) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(lineHeight), strokeWidth = 2.dp)
                                        AnimatedContent(
                                            targetState = currentRequest.stage,
                                            transitionSpec = {
                                                (slideInVertically { it } + fadeIn()) togetherWith
                                                    (slideOutVertically { -it } + fadeOut())
                                            },
                                        ) { stage ->
                                            Text(
                                                text = stringResource(when (stage) {
                                                    SubscriptionAddStage.Downloading -> Res.string.subscription_adding_download
                                                    SubscriptionAddStage.Parsing -> Res.string.subscription_adding_parse
                                                    SubscriptionAddStage.Saving -> Res.string.subscription_adding_save
                                                    else -> Res.string.subscription_adding_check
                                                }),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                } else {
                                    currentRequest.error?.let { error ->
                                        Text(
                                            text = error.replace('\n', ' ').replace('\r', ' '),
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                showText(error)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                onDismissRequest = ::cancel,
                confirmButton = {
                    TextButton(
                        enabled = !busy && currentRequest.value.isNotEmpty() &&
                                currentRequest.value != currentRequest.initialValue,
                        onClick = { submit(currentRequest) },
                    ) {
                        Text(text = stringResource(if (editing) Res.string.action_save else Res.string.action_ok))
                    }
                },
                dismissButton = {
                    Row {
                        if (editing) {
                            TextButton(
                                enabled = currentRequest.value.isNotEmpty(),
                                onClick = { copyText(currentRequest.value) },
                            ) {
                                Text(text = stringResource(Res.string.action_copy))
                            }
                        }
                        TextButton(enabled = !saving, onClick = ::cancel) {
                            Text(text = stringResource(Res.string.action_cancel))
                        }
                    }
                },
            )
        }
    }
}
