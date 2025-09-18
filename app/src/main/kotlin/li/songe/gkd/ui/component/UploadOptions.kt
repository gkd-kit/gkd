package li.songe.gkd.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.MainViewModel
import li.songe.gkd.data.GithubPoliciesAsset
import li.songe.gkd.util.GithubCookieException
import li.songe.gkd.util.LoadStatus
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.toast
import li.songe.gkd.util.uploadFileToGithub
import java.io.File

class UploadOptions(
    private val mainVm: MainViewModel,
) {
    private val statusFlow = MutableStateFlow<LoadStatus<GithubPoliciesAsset>?>(null)
    private var job: Job? = null
    private fun buildTask(
        cookie: String,
        getFile: suspend () -> File,
        onSuccessResult: (suspend (GithubPoliciesAsset) -> Unit)?
    ) = mainVm.viewModelScope.launchTry(Dispatchers.IO) {
        statusFlow.value = LoadStatus.Loading()
        try {
            val policiesAsset = uploadFileToGithub(cookie, getFile()) {
                if (statusFlow.value is LoadStatus.Loading) {
                    statusFlow.value = LoadStatus.Loading(it)
                }
            }
            statusFlow.value = LoadStatus.Success(policiesAsset)
            onSuccessResult?.invoke(policiesAsset)
        } catch (e: Exception) {
            LogUtils.d(e)
            statusFlow.value = LoadStatus.Failure(e)
        } finally {
            job = null
        }
    }


    private var showHref: (GithubPoliciesAsset) -> String = { it.shortHref }
    fun startTask(
        getFile: suspend () -> File,
        showHref: (GithubPoliciesAsset) -> String = { it.shortHref },
        onSuccessResult: (suspend (GithubPoliciesAsset) -> Unit)? = null
    ) {
        val cookie = mainVm.githubCookieFlow.value
        if (cookie.isEmpty()) {
            toast("请先设置 cookie 后再上传")
            mainVm.showEditCookieDlgFlow.value = true
            return
        }
        if (job != null || statusFlow.value is LoadStatus.Loading) {
            return
        }
        this.showHref = showHref
        job = buildTask(cookie, getFile, onSuccessResult)
    }

    private fun stopTask() {
        if (statusFlow.value is LoadStatus.Loading && job != null) {
            job?.cancel("上传已取消")
            job = null
        }
    }


    @Composable
    fun ShowDialog() {
        when (val status = statusFlow.collectAsState().value) {
            null -> {}
            is LoadStatus.Loading -> {
                AlertDialog(
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
                    onDismissRequest = { },
                    confirmButton = {
                        TextButton(onClick = {
                            stopTask()
                        }) {
                            Text(text = "终止上传")
                        }
                    },
                )
            }

            is LoadStatus.Success -> {
                val href = showHref(status.result)
                AlertDialog(
                    title = { Text(text = "上传完成") },
                    text = { CopyTextCard(text = href) },
                    onDismissRequest = {},
                    confirmButton = {
                        TextButton(onClick = {
                            statusFlow.value = null
                        }) {
                            Text(text = "关闭")
                        }
                    }
                )
            }

            is LoadStatus.Failure -> {
                AlertDialog(
                    title = { Text(text = "上传失败") },
                    text = {
                        Text(text = status.exception.let {
                            it.message ?: it.toString()
                        })
                    },
                    onDismissRequest = { statusFlow.value = null },
                    dismissButton = if (status.exception is GithubCookieException) ({
                        TextButton(onClick = {
                            statusFlow.value = null
                            mainVm.showEditCookieDlgFlow.value = true
                        }) {
                            Text(text = "更换 Cookie")
                        }
                    }) else {
                        null
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            statusFlow.value = null
                        }) {
                            Text(text = "关闭")
                        }
                    },
                )
            }
        }
    }
}
