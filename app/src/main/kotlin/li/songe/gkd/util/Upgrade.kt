package li.songe.gkd.util

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.NetworkUtils
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.songe.gkd.META
import li.songe.gkd.MainViewModel
import li.songe.gkd.app
import java.io.File
import java.net.URI

@Serializable
data class NewVersion(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
    val fileSize: Long,
    val versionLogs: List<VersionLog> = emptyList(),
)

@Serializable
data class VersionLog(
    val name: String,
    val code: Int,
    val desc: String,
)

class UpdateStatus {
    val checkUpdatingFlow = MutableStateFlow(false)
    val newVersionFlow = MutableStateFlow<NewVersion?>(null)
    val downloadStatusFlow = MutableStateFlow<LoadStatus<File>?>(null)
}

private val UPDATE_URL: String
    get() = UpdateChannelOption.allSubObject.findOption(storeFlow.value.updateChannel).url

suspend fun UpdateStatus.checkUpdate(): NewVersion? {
    if (checkUpdatingFlow.value) return null
    val isAvailable = withContext(Dispatchers.IO) { NetworkUtils.isAvailable() }
    if (!isAvailable) {
        error("网络不可用")
    }
    checkUpdatingFlow.value = true
    try {
        val newVersion = client.get(UPDATE_URL).body<NewVersion>()
        if (newVersion.versionCode > META.versionCode) {
            newVersionFlow.value =
                newVersion.copy(versionLogs = newVersion.versionLogs.takeWhile { v -> v.code > META.versionCode })
            return newVersion
        } else {
            Log.d("Upgrade", "no new version")
        }
    } finally {
        checkUpdatingFlow.value = false
    }
    return null
}

private fun UpdateStatus.startDownload(viewModel: MainViewModel, newVersion: NewVersion) {
    if (downloadStatusFlow.value is LoadStatus.Loading) return
    downloadStatusFlow.value = LoadStatus.Loading(0f)
    val newApkFile = sharedDir.resolve("gkd-v${newVersion.versionCode}.apk").apply {
        if (exists()) {
            delete()
        }
    }
    var job: Job? = null
    job = viewModel.viewModelScope.launch(Dispatchers.IO) {
        try {
            val channel = client.get(URI(UPDATE_URL).resolve(newVersion.downloadUrl).toString()) {
                onDownload { bytesSentTotal, _ ->
                    // contentLength 在某些机型上概率错误
                    val downloadStatus = downloadStatusFlow.value
                    if (downloadStatus is LoadStatus.Loading) {
                        downloadStatusFlow.value = LoadStatus.Loading(
                            bytesSentTotal.toFloat() / (newVersion.fileSize)
                        )
                    } else if (downloadStatus is LoadStatus.Failure) {
                        // 提前终止下载
                        job?.cancel()
                    }
                }
            }.bodyAsChannel()
            if (downloadStatusFlow.value is LoadStatus.Loading) {
                channel.copyAndClose(newApkFile.writeChannel())
                downloadStatusFlow.value = LoadStatus.Success(newApkFile)
            }
        } catch (e: Exception) {
            if (downloadStatusFlow.value is LoadStatus.Loading) {
                downloadStatusFlow.value = LoadStatus.Failure(e)
            }
        }
    }
}

@Composable
fun UpgradeDialog(status: UpdateStatus) {
    val mainVm = LocalMainViewModel.current
    val newVersion by status.newVersionFlow.collectAsState()
    newVersion?.let { newVersionVal ->
        AlertDialog(title = {
            Text(text = "新版本")
        }, text = {
            Text(
                text = "v${META.versionName} -> v${newVersionVal.versionName}\n\n${
                    if (newVersionVal.versionLogs.size > 1) {
                        newVersionVal.versionLogs.joinToString("\n\n") { v -> "v${v.name}\n${v.desc}" }
                    } else if (newVersionVal.versionLogs.isNotEmpty()) {
                        newVersionVal.versionLogs.first().desc
                    } else {
                        ""
                    }
                }".trimEnd(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()))

        }, onDismissRequest = { }, confirmButton = {
            TextButton(onClick = {
                status.newVersionFlow.value = null
                status.startDownload(mainVm, newVersionVal)
            }) {
                Text(text = "下载更新")
            }
        }, dismissButton = {
            TextButton(onClick = { status.newVersionFlow.value = null }) {
                Text(text = "取消")
            }
        })
    }

    val downloadStatus by status.downloadStatusFlow.collectAsState()
    downloadStatus?.let { downloadStatusVal ->
        when (downloadStatusVal) {
            is LoadStatus.Loading -> {
                AlertDialog(
                    title = { Text(text = "下载中") },
                    text = {
                        LinearProgressIndicator(
                            progress = { downloadStatusVal.progress },
                        )
                    },
                    onDismissRequest = {},
                    confirmButton = {
                        TextButton(onClick = {
                            status.downloadStatusFlow.value = LoadStatus.Failure(
                                Exception("终止下载")
                            )
                        }) {
                            Text(text = "终止下载")
                        }
                    },
                )
            }

            is LoadStatus.Failure -> {
                AlertDialog(
                    title = { Text(text = "下载失败") },
                    text = {
                        Text(text = downloadStatusVal.exception.let {
                            it.message ?: it.toString()
                        })
                    },
                    onDismissRequest = { status.downloadStatusFlow.value = null },
                    confirmButton = {
                        TextButton(onClick = {
                            status.downloadStatusFlow.value = null
                        }) {
                            Text(text = "关闭")
                        }
                    },
                )
            }

            is LoadStatus.Success -> {
                AlertDialog(
                    title = { Text(text = "下载完毕") },
                    text = {
                        Text(text = "可继续选择安装新版本")
                    },
                    onDismissRequest = {},
                    dismissButton = {
                        TextButton(onClick = {
                            status.downloadStatusFlow.value = null
                        }) {
                            Text(text = "关闭")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = throttle {
                            installApk(downloadStatusVal.result)
                        }) {
                            Text(text = "安装")
                        }
                    })
            }
        }
    }
}

private fun installApk(file: File) {
    val uri = FileProvider.getUriForFile(
        app, "${app.packageName}.provider", file
    )
    val intent = Intent(Intent.ACTION_VIEW)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.setDataAndType(uri, "application/vnd.android.package-archive")
    app.tryStartActivity(intent)
}
