package li.gkd.app.ui.upload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.network.GithubCookieException
import li.gkd.app.network.GithubPoliciesAsset
import li.gkd.app.network.GithubUploader
import li.gkd.app.resources.Res
import li.gkd.app.resources.github_cookie_expired
import li.gkd.app.resources.update_success
import li.gkd.app.resources.upload_cancelled
import li.gkd.app.resources.upload_cookie_required
import li.gkd.app.settings.GithubCookieStore
import li.gkd.app.ui.component.GithubUploadStatus
import li.gkd.app.ui.component.GkGithubUploadDialogs
import li.gkd.app.ui.text.displayMessage
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.LogUtils
import org.jetbrains.compose.resources.getString
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

class GithubUploadState(
    private val scope: CoroutineScope,
    private val onOpenCookieHelp: () -> Unit,
    private val toast: (String) -> Unit,
) {
    private val cookieEditorVisibleFlow = MutableStateFlow(false)
    private val cookieDraftFlow = MutableStateFlow("")
    private val statusFlow = MutableStateFlow<GithubUploadStatus?>(null)
    private var activeRequest: GithubUploadRequest? = null
    private var uploadJob: Job? = null

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
        val cookie = GithubCookieStore.value.value
        if (cookie.isEmpty()) {
            toast(Res.string.upload_cookie_required.getSync())
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
        uploadJob = scope.launch(start = CoroutineStart.LAZY) {
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
                    cookieFailure = item.label to getString(Res.string.github_cookie_expired)
                    pendingFromCookie = items.drop(index)
                    break
                } catch (e: Exception) {
                    LogUtils.d(e)
                    request.failures.add(item.label to (e.displayMessage()))
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
        uploadJob?.start()
    }

    private suspend fun uploadItem(
        item: GithubUploadItem,
        cookie: String,
        onProgress: (Float) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val file = item.getFile()
        try {
            val asset = GithubUploader.uploadFileToGithub(cookie, file, onProgress)
            item.onSuccessResult(asset)
            item.showHref(asset)
        } finally {
            withContext(NonCancellable) {
                runCatching { item.releaseFile(file) }.onFailure { LogUtils.d(it) }
            }
        }
    }

    private fun showCookieEditor() {
        cookieDraftFlow.value = GithubCookieStore.value.value
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
        scope.launch {
            try {
                GithubCookieStore.save(cookie)
                hideCookieEditor()
                toast(getString(Res.string.update_success))
                activeRequest?.takeIf { it.pendingItems.isNotEmpty() && uploadJob == null }
                    ?.let { executeRequest(it, cookie) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.displayMessage())
            }
        }
    }

    private fun dismissCookieEditor() {
        hideCookieEditor()
        if (statusFlow.value == null && uploadJob == null) activeRequest = null
    }

    private fun stopTask() {
        if (statusFlow.value is GithubUploadStatus.Loading) {
            uploadJob?.cancel(Res.string.upload_cancelled.getSync())
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
        GkGithubUploadDialogs(
            cookieEditorVisible,
            cookieDraft,
            status,
            activeRequest == null || cookieDraft.isNotBlank(),
            ::dismissCookieEditor,
            ::openCookieHelp,
            ::updateCookieDraft,
            ::saveCookie,
            ::stopTask,
            ::closeUploadStatus,
            ::showCookieEditor
        )
    }
}
