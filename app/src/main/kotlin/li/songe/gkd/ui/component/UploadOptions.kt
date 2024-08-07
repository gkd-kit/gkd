package li.songe.gkd.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.blankj.utilcode.util.ClipboardUtils
import io.ktor.client.call.body
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.data.GithubPoliciesAsset
import li.songe.gkd.data.RpcError
import li.songe.gkd.util.FILE_UPLOAD_URL
import li.songe.gkd.util.LoadStatus
import li.songe.gkd.util.client
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.toast
import java.io.File

class UploadOptions(
    private val scope: CoroutineScope
) {
    private val statusFlow = MutableStateFlow<LoadStatus<GithubPoliciesAsset>?>(null)
    private var job: Job? = null
    private fun buildTask(file: File) = scope.launchTry(Dispatchers.IO) {
        statusFlow.value = LoadStatus.Loading()
        try {
            val response =
                client.submitFormWithBinaryData(url = FILE_UPLOAD_URL, formData = formData {
                    append("\"file\"", file.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, "application/x-zip-compressed")
                        append(HttpHeaders.ContentDisposition, "filename=\"file.zip\"")
                    })
                }) {
                    onUpload { bytesSentTotal, contentLength ->
                        if (statusFlow.value is LoadStatus.Loading) {
                            statusFlow.value =
                                LoadStatus.Loading(bytesSentTotal / contentLength.toFloat())
                        }
                    }
                }
            if (response.headers["X_RPC_OK"] == "true") {
                val policiesAsset = response.body<GithubPoliciesAsset>()
                statusFlow.value = LoadStatus.Success(policiesAsset)
            } else if (response.headers["X_RPC_OK"] == "false") {
                statusFlow.value = LoadStatus.Failure(response.body<RpcError>())
            } else {
                statusFlow.value = LoadStatus.Failure(Exception(response.bodyAsText()))
            }
        } catch (e: Exception) {
            statusFlow.value = LoadStatus.Failure(e)
        } finally {
            job = null
        }
    }

    fun startTask(file: File) {
        if (job != null || statusFlow.value is LoadStatus.Loading) {
            return
        }
        job = buildTask(file)
    }

    private fun stopTask() {
        if (statusFlow.value is LoadStatus.Loading && job != null) {
            job?.cancel(CancellationException("您取消了上传"))
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
                        LinearProgressIndicator(
                            progress = { status.progress },
                        )
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
                AlertDialog(title = { Text(text = "上传完成") }, text = {
                    Text(text = status.result.shortHref)
                }, onDismissRequest = {}, dismissButton = {
                    TextButton(onClick = {
                        statusFlow.value = null
                    }) {
                        Text(text = "关闭")
                    }
                }, confirmButton = {
                    TextButton(onClick = {
                        ClipboardUtils.copyText(status.result.shortHref)
                        toast("复制成功")
                        statusFlow.value = null
                    }) {
                        Text(text = "复制并关闭")
                    }
                })
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
