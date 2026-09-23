package li.gkd.app.ui.component

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
import li.gkd.app.text.UiStrings
import li.gkd.app.data.GithubPoliciesAsset
import li.gkd.app.store.FileStateStore
import li.gkd.app.util.GithubCookieException
import li.gkd.app.util.LoadStatus
import li.gkd.app.util.LogUtils
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.util.Github.uploadFileToGithub
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
            toast(UiStrings.upload_cookie_required)
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
        toast(UiStrings.update_success)
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
            uploadJob?.cancel(UiStrings.upload_cancelled)
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
                        onValueChange = {
                            cookieDraftFlow.value =
                                it.filter { char -> char != '\n' && char != '\r' }
                        },
                        placeholder = { Text(text = UiStrings.github_cookie_input_hint) },
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
                        Text(text = UiStrings.action_confirm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = ::dismissCookieEditor) {
                        Text(text = UiStrings.action_cancel)
                    }
                },
            )
        }
        when (val status = uploadStatus) {
            null -> {}
            is LoadStatus.Loading -> {
                GkAlertDialog(
                    title = { Text(text = UiStrings.upload_file_progress) },
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
                            Text(text = UiStrings.upload_abort)
                        }
                    },
                )
            }

            is LoadStatus.Success -> {
                GkAlertDialog(
                    title = { Text(text = UiStrings.upload_complete) },
                    text = { GkCopyTextCard(text = status.result) },
                    onDismissRequest = {},
                    confirmButton = {
                        TextButton(onClick = ::closeUploadStatus) {
                            Text(text = UiStrings.action_close)
                        }
                    },
                )
            }

            is LoadStatus.Failure -> {
                GkAlertDialog(
                    title = { Text(text = UiStrings.upload_failed) },
                    text = {
                        Text(text = status.exception.message ?: status.exception.toString())
                    },
                    onDismissRequest = ::closeUploadStatus,
                    dismissButton = if (status.exception is GithubCookieException) {
                        {
                            TextButton(onClick = ::replaceCookie) {
                                Text(text = UiStrings.cookie_change)
                            }
                        }
                    } else {
                        null
                    },
                    confirmButton = {
                        TextButton(onClick = ::closeUploadStatus) {
                            Text(text = UiStrings.action_close)
                        }
                    },
                )
            }
        }
    }
}
