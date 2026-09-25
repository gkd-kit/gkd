package li.gkd.app.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.data.GithubPoliciesAsset
import li.gkd.app.store.FileStateStore
import li.gkd.app.text.UiStrings
import li.gkd.app.util.Github.uploadFileToGithub
import li.gkd.app.util.GithubCookieException
import li.gkd.app.util.LogUtils
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.util.ToastUtils.toast
import java.io.File

class GithubUploadItem(
    val label: String,
    val getFile: suspend () -> File,
    val releaseFile: suspend (File) -> Unit,
    val onSuccessResult: suspend (GithubPoliciesAsset) -> Unit = {},
    val showHref: (GithubPoliciesAsset) -> String = { it.shortHref },
)

private class GithubUploadRequest(
    var pendingItems: List<GithubUploadItem>,
    val batch: Boolean,
    val onFinished: (() -> Unit)?,
) {
    val links = mutableListOf<String>()
    val failures = mutableListOf<Pair<String, String>>()
}

private sealed interface GithubUploadStatus {
    data class Loading(
        val batch: Boolean,
        val index: Int,
        val total: Int,
        val label: String,
        val progress: Float,
    ) : GithubUploadStatus

    data class Finished(
        val batch: Boolean,
        val links: List<String>,
        val failures: List<Pair<String, String>>,
        val remainingCount: Int,
        val cancelled: Boolean,
        val cookieExpired: Boolean,
    ) : GithubUploadStatus
}

class GithubUploadState(
    private val scope: CoroutineScope,
    private val onOpenCookieHelp: () -> Unit,
) {
    private val cookieFlow by lazy {
        FileStateStore.createTextFlow(
            key = "github_cookie",
            decode = { it ?: "" },
            encode = { it },
            private = true,
            scope = scope,
        )
    }
    private val cookieEditorVisibleFlow = MutableStateFlow(false)
    private val cookieDraftFlow = MutableStateFlow("")
    private val statusFlow = MutableStateFlow<GithubUploadStatus?>(null)
    private var activeRequest: GithubUploadRequest? = null
    private var uploadJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) { cookieFlow.value }
    }

    fun startTask(item: GithubUploadItem): Boolean = start(listOf(item), batch = false)

    fun startBatchTask(
        items: List<GithubUploadItem>,
        onFinished: (() -> Unit)? = null,
    ): Boolean = start(items, batch = true, onFinished = onFinished)

    private fun start(
        items: List<GithubUploadItem>,
        batch: Boolean,
        onFinished: (() -> Unit)? = null,
    ): Boolean {
        if (items.isEmpty() || activeRequest != null || uploadJob != null ||
            statusFlow.value != null || cookieEditorVisibleFlow.value
        ) return false
        val request = GithubUploadRequest(items, batch, onFinished)
        activeRequest = request
        val cookie = cookieFlow.value
        if (cookie.isEmpty()) {
            toast(UiStrings.upload_cookie_required)
            showCookieEditor()
        } else {
            executeRequest(request, cookie)
        }
        return true
    }

    fun editCookie() {
        if (uploadJob == null) showCookieEditor()
    }

    fun openCookieHelp() {
        hideCookieEditor()
        activeRequest = null
        closeUploadStatus()
        onOpenCookieHelp()
    }

    private fun executeRequest(request: GithubUploadRequest, cookie: String) {
        statusFlow.value = GithubUploadStatus.Loading(
            batch = request.batch,
            index = 1,
            total = request.pendingItems.size,
            label = request.pendingItems.first().label,
            progress = 0f,
        )
        uploadJob = scope.launch(Dispatchers.IO) {
            val items = request.pendingItems
            var pendingFromCookie = emptyList<GithubUploadItem>()
            var cookieFailure: Pair<String, String>? = null
            var skippedCount = 0
            var cancelled = false
            for ((index, item) in items.withIndex()) {
                statusFlow.value = GithubUploadStatus.Loading(
                    batch = request.batch,
                    index = index + 1,
                    total = items.size,
                    label = item.label,
                    progress = 0f,
                )
                try {
                    val link = uploadItem(item, cookie) { progress ->
                        statusFlow.value = GithubUploadStatus.Loading(
                            batch = request.batch,
                            index = index + 1,
                            total = items.size,
                            label = item.label,
                            progress = progress,
                        )
                    }
                    request.links.add(link)
                } catch (e: CancellationException) {
                    cancelled = true
                    skippedCount = items.size - index
                    break
                } catch (e: GithubCookieException) {
                    LogUtils.d(e)
                    cookieFailure = item.label to e.message
                    pendingFromCookie = items.drop(index)
                    break
                } catch (e: Exception) {
                    LogUtils.d(e)
                    request.failures.add(item.label to (e.message ?: e.toString()))
                }
            }
            request.pendingItems = pendingFromCookie
            uploadJob = null
            val remainingCount = request.failures.size + pendingFromCookie.size + skippedCount
            if (request.batch && remainingCount == 0) {
                activeRequest = null
                statusFlow.value = null
                request.onFinished?.invoke()
            } else if (!request.batch && cancelled) {
                activeRequest = null
                statusFlow.value = null
            } else {
                statusFlow.value = GithubUploadStatus.Finished(
                    batch = request.batch,
                    links = request.links.toList(),
                    failures = request.failures + listOfNotNull(cookieFailure),
                    remainingCount = remainingCount,
                    cancelled = cancelled,
                    cookieExpired = pendingFromCookie.isNotEmpty(),
                )
            }
        }
    }

    private suspend fun uploadItem(
        item: GithubUploadItem,
        cookie: String,
        onProgress: (Float) -> Unit,
    ): String {
        val file = item.getFile()
        try {
            val asset = uploadFileToGithub(cookie, file, onProgress)
            item.onSuccessResult(asset)
            return item.showHref(asset)
        } finally {
            withContext(NonCancellable) {
                runCatching { item.releaseFile(file) }.onFailure { LogUtils.d(it) }
            }
        }
    }

    private fun showCookieEditor() {
        cookieDraftFlow.value = cookieFlow.value
        cookieEditorVisibleFlow.value = true
    }

    private fun hideCookieEditor() {
        cookieEditorVisibleFlow.value = false
        cookieDraftFlow.value = ""
    }

    private fun updateCookieDraft(value: String) {
        cookieDraftFlow.value = value.filter { it != '\n' && it != '\r' }
    }

    private fun saveCookie() {
        val cookie = cookieDraftFlow.value.trim()
        cookieFlow.value = cookie
        hideCookieEditor()
        toast(UiStrings.update_success)
        activeRequest?.takeIf { it.pendingItems.isNotEmpty() }
            ?.let { executeRequest(it, cookie) }
    }

    private fun dismissCookieEditor() {
        hideCookieEditor()
        if (statusFlow.value == null && uploadJob == null) activeRequest = null
    }

    private fun stopTask() {
        if (statusFlow.value is GithubUploadStatus.Loading) {
            uploadJob?.cancel(UiStrings.upload_cancelled)
        }
    }

    private fun closeUploadStatus() {
        val request = activeRequest
        val finished = statusFlow.value is GithubUploadStatus.Finished
        statusFlow.value = null
        activeRequest = null
        if (finished && request?.batch == true) request.onFinished?.invoke()
    }

    @Composable
    fun Render() {
        val cookieEditorVisible by cookieEditorVisibleFlow.collectAsStateWithLifecycle()
        val cookieDraft by cookieDraftFlow.collectAsStateWithLifecycle()
        val status by statusFlow.collectAsStateWithLifecycle()
        if (cookieEditorVisible) {
            GkAlertDialog(
                properties = DialogProperties(dismissOnClickOutside = false),
                onDismissRequest = ::dismissCookieEditor,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = UiStrings.github_cookie_dialog_title)
                        GkIconButton(
                            imageVector = GkIcons.HelpOutline,
                            onClick = throttle(::openCookieHelp),
                        )
                    }
                },
                text = {
                    OutlinedTextField(
                        value = cookieDraft,
                        onValueChange = ::updateCookieDraft,
                        placeholder = { Text(text = UiStrings.github_cookie_input_hint) },
                        modifier = Modifier.fillMaxWidth().autoFocus(),
                        maxLines = 10,
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = activeRequest == null || cookieDraft.isNotBlank(),
                        onClick = ::saveCookie,
                    ) { Text(text = UiStrings.action_confirm) }
                },
                dismissButton = {
                    TextButton(onClick = ::dismissCookieEditor) {
                        Text(text = UiStrings.action_cancel)
                    }
                },
            )
        }
        when (val visibleStatus = if (cookieEditorVisible) null else status) {
            null -> Unit
            is GithubUploadStatus.Loading -> {
                GkAlertDialog(
                    title = { Text(UiStrings.upload_file_progress) },
                    text = {
                        if (visibleStatus.batch) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(UiStrings.upload_batch_progress(
                                    visibleStatus.index,
                                    visibleStatus.total,
                                ))
                                Text(visibleStatus.label)
                                UploadProgress(visibleStatus.progress)
                            }
                        } else {
                            UploadProgress(visibleStatus.progress)
                        }
                    },
                    onDismissRequest = {},
                    confirmButton = {
                        TextButton(onClick = ::stopTask) { Text(UiStrings.upload_abort) }
                    },
                )
            }
            is GithubUploadStatus.Finished -> {
                GkAlertDialog(
                    properties = DialogProperties(
                        dismissOnClickOutside = visibleStatus.links.isEmpty(),
                    ),
                    title = {
                        Text(when {
                            visibleStatus.cancelled -> UiStrings.upload_cancelled
                            visibleStatus.batch -> UiStrings.upload_batch_result
                            visibleStatus.remainingCount == 0 -> UiStrings.upload_complete
                            else -> UiStrings.upload_failed
                        })
                    },
                    text = {
                        if (visibleStatus.batch) {
                            Column(
                                modifier = Modifier.heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(UiStrings.upload_batch_summary(
                                    visibleStatus.links.size,
                                    visibleStatus.remainingCount,
                                ))
                                if (visibleStatus.links.isNotEmpty()) {
                                    GkCopyTextCard(text = visibleStatus.links.joinToString("\n"))
                                }
                                visibleStatus.failures.take(5).forEach { (label, message) ->
                                    Text("$label: $message", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else if (visibleStatus.remainingCount == 0) {
                            GkCopyTextCard(text = visibleStatus.links.single())
                        } else {
                            Text(visibleStatus.failures.first().second)
                        }
                    },
                    onDismissRequest = ::closeUploadStatus,
                    dismissButton = if (visibleStatus.cookieExpired) {
                        {
                            TextButton(onClick = ::showCookieEditor) {
                                Text(UiStrings.cookie_change)
                            }
                        }
                    } else null,
                    confirmButton = {
                        TextButton(onClick = ::closeUploadStatus) { Text(UiStrings.action_close) }
                    },
                )
            }
        }
    }
}

@Composable
private fun UploadProgress(progress: Float) {
    val showExactProgress = progress > 0f && progress < 1f
    AnimatedContent(showExactProgress) { showExact ->
        if (showExact) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
