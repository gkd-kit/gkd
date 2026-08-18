package li.songe.gkd.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import li.songe.gkd.data.GithubPoliciesAsset
import li.songe.gkd.store.createTextFlow
import li.songe.gkd.util.GithubCookieException
import li.songe.gkd.util.LoadStatus
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import li.songe.gkd.util.uploadFileToGithub
import java.io.File

private class GithubUploadRequest(
    val getFile: suspend () -> File,
    val showHref: (GithubPoliciesAsset) -> String,
    val onSuccessResult: (suspend (GithubPoliciesAsset) -> Unit)?,
) {
    var preparedFile: File? = null
}

class GithubUploadState(
    private val scope: CoroutineScope,
    private val onOpenCookieHelp: () -> Unit,
) {
    private val cookieFlow by lazy {
        createTextFlow(
            key = "github_cookie",
            decode = { it ?: "" },
            encode = { it },
            private = true,
            scope = scope,
        )
    }
    private val cookieEditorVisibleFlow = MutableStateFlow(false)
    private val cookieDraftFlow = MutableStateFlow("")
    private val uploadStatusFlow = MutableStateFlow<LoadStatus<String>?>(null)
    private var activeRequest: GithubUploadRequest? = null
    private var uploadJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            cookieFlow.value
        }
    }

    fun startTask(
        getFile: suspend () -> File,
        showHref: (GithubPoliciesAsset) -> String = { it.shortHref },
        onSuccessResult: (suspend (GithubPoliciesAsset) -> Unit)? = null,
    ) {
        if (
            activeRequest != null ||
            uploadJob != null ||
            uploadStatusFlow.value != null ||
            cookieEditorVisibleFlow.value
        ) {
            return
        }
        val request = GithubUploadRequest(
            getFile = getFile,
            showHref = showHref,
            onSuccessResult = onSuccessResult,
        )
        activeRequest = request
        val cookie = cookieFlow.value
        if (cookie.isEmpty()) {
            toast("请先设置 cookie 后再上传")
            showCookieEditor()
        } else {
            executeRequest(request, cookie)
        }
    }

    fun editCookie() {
        if (uploadJob == null) {
            showCookieEditor()
        }
    }

    fun openCookieHelp() {
        hideCookieEditor()
        closeUploadStatus()
        onOpenCookieHelp()
    }

    private fun executeRequest(request: GithubUploadRequest, cookie: String) {
        uploadJob = scope.launch(Dispatchers.IO) {
            uploadStatusFlow.value = LoadStatus.Loading()
            try {
                val file = request.preparedFile ?: request.getFile().also {
                    request.preparedFile = it
                }
                val policiesAsset = uploadFileToGithub(cookie, file) { progress ->
                    if (uploadStatusFlow.value is LoadStatus.Loading) {
                        uploadStatusFlow.value = LoadStatus.Loading(progress)
                    }
                }
                request.onSuccessResult?.invoke(policiesAsset)
                uploadStatusFlow.value = LoadStatus.Success(request.showHref(policiesAsset))
                activeRequest = null
            } catch (e: CancellationException) {
                uploadStatusFlow.value = null
                activeRequest = null
                throw e
            } catch (e: GithubCookieException) {
                LogUtils.d(e)
                uploadStatusFlow.value = LoadStatus.Failure(e)
            } catch (e: Exception) {
                LogUtils.d(e)
                uploadStatusFlow.value = LoadStatus.Failure(e)
                activeRequest = null
            } finally {
                uploadJob = null
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

    private fun saveCookie() {
        val cookie = cookieDraftFlow.value.trim()
        cookieFlow.value = cookie
        hideCookieEditor()
        toast("更新成功")
        activeRequest?.let { executeRequest(it, cookie) }
    }

    private fun dismissCookieEditor() {
        hideCookieEditor()
        if (uploadStatusFlow.value == null && uploadJob == null) {
            activeRequest = null
        }
    }

    private fun replaceCookie() {
        showCookieEditor()
    }

    private fun stopTask() {
        if (uploadStatusFlow.value is LoadStatus.Loading) {
            uploadJob?.cancel("上传已取消")
        }
    }

    private fun closeUploadStatus() {
        uploadStatusFlow.value = null
        activeRequest = null
    }

    @Composable
    fun Render() {
        val cookieEditorVisible by cookieEditorVisibleFlow.collectAsStateWithLifecycle()
        val cookieDraft by cookieDraftFlow.collectAsStateWithLifecycle()
        val uploadStatus by uploadStatusFlow.collectAsStateWithLifecycle()
        if (cookieEditorVisible) {
            val cookieRequired = activeRequest != null
            AppAlertDialog(
                properties = DialogProperties(dismissOnClickOutside = false),
                onDismissRequest = ::dismissCookieEditor,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Github Cookie")
                        PerfIconButton(
                            imageVector = PerfIcon.HelpOutline,
                            onClick = throttle(::openCookieHelp),
                        )
                    }
                },
                text = {
                    OutlinedTextField(
                        value = cookieDraft,
                        onValueChange = {
                            cookieDraftFlow.value =
                                it.filter { char -> char != '\n' && char != '\r' }
                        },
                        placeholder = { Text(text = "请输入 Github Cookie") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .autoFocus(),
                        maxLines = 10,
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !cookieRequired || cookieDraft.isNotBlank(),
                        onClick = ::saveCookie,
                    ) {
                        Text(text = "确认")
                    }
                },
                dismissButton = {
                    TextButton(onClick = ::dismissCookieEditor) {
                        Text(text = "取消")
                    }
                },
            )
        }
        when (val status = uploadStatus) {
            null -> {}
            is LoadStatus.Loading -> {
                AppAlertDialog(
                    title = { Text(text = "上传文件中") },
                    text = {
                        val showExactProgress = 0f < status.progress && status.progress < 1f
                        AnimatedContent(showExactProgress) { showExact ->
                            if (showExact) {
                                LinearProgressIndicator(
                                    progress = { status.progress },
                                )
                            } else {
                                LinearProgressIndicator()
                            }
                        }
                    },
                    onDismissRequest = {},
                    confirmButton = {
                        TextButton(onClick = ::stopTask) {
                            Text(text = "终止上传")
                        }
                    },
                )
            }

            is LoadStatus.Success -> {
                AppAlertDialog(
                    title = { Text(text = "上传完成") },
                    text = { CopyTextCard(text = status.result) },
                    onDismissRequest = {},
                    confirmButton = {
                        TextButton(onClick = ::closeUploadStatus) {
                            Text(text = "关闭")
                        }
                    },
                )
            }

            is LoadStatus.Failure -> {
                AppAlertDialog(
                    title = { Text(text = "上传失败") },
                    text = {
                        Text(text = status.exception.message ?: status.exception.toString())
                    },
                    onDismissRequest = ::closeUploadStatus,
                    dismissButton = if (status.exception is GithubCookieException) {
                        {
                            TextButton(onClick = ::replaceCookie) {
                                Text(text = "更换 Cookie")
                            }
                        }
                    } else {
                        null
                    },
                    confirmButton = {
                        TextButton(onClick = ::closeUploadStatus) {
                            Text(text = "关闭")
                        }
                    },
                )
            }
        }
    }
}
