package li.songe.gkd.util

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
import com.blankj.utilcode.util.AppUtils
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
import li.songe.gkd.BuildConfig
import li.songe.gkd.appScope
import java.io.File
import java.net.URI

@Serializable
data class NewVersion(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
    val versionLogs: List<VersionLog> = emptyList(),
    val fileSize: Long? = null,
)

@Serializable
data class VersionLog(
    val name: String,
    val code: Int,
    val desc: String,
)

val checkUpdatingFlow by lazy { MutableStateFlow(false) }
val newVersionFlow by lazy { MutableStateFlow<NewVersion?>(null) }
val downloadStatusFlow by lazy { MutableStateFlow<LoadStatus<String>?>(null) }
suspend fun checkUpdate(): NewVersion? {
    if (checkUpdatingFlow.value) return null
    val isAvailable = withContext(Dispatchers.IO) { NetworkUtils.isAvailable() }
    if (!isAvailable) {
        error("网络不可用")
    }
    checkUpdatingFlow.value = true
    try {
        val newVersion = client.get(UPDATE_URL).body<NewVersion>()
        if (newVersion.versionCode > BuildConfig.VERSION_CODE) {
            newVersionFlow.value =
                newVersion.copy(versionLogs = newVersion.versionLogs.takeWhile { v -> v.code > BuildConfig.VERSION_CODE })
            return newVersion
        } else {
            Log.d("Upgrade", "no new version")
        }
    } finally {
        checkUpdatingFlow.value = false
    }
    return null
}

fun startDownload(newVersion: NewVersion) {
    if (downloadStatusFlow.value is LoadStatus.Loading) return
    downloadStatusFlow.value = LoadStatus.Loading(0f)
    val newApkFile = File(newVersionApkDir, "v${newVersion.versionCode}.apk")
    if (newApkFile.exists()) {
        newApkFile.delete()
    }
    var job: Job? = null
    job = appScope.launch(Dispatchers.IO) {
        try {
            val channel = client.get(URI(UPDATE_URL).resolve(newVersion.downloadUrl).toString()) {
                onDownload { bytesSentTotal, contentLength ->
                    // contentLength 在某些机型上概率错误
                    val downloadStatus = downloadStatusFlow.value
                    if (downloadStatus is LoadStatus.Loading) {
                        downloadStatusFlow.value = LoadStatus.Loading(
                            bytesSentTotal.toFloat() / (newVersion.fileSize ?: contentLength)
                        )
                    } else if (downloadStatus is LoadStatus.Failure) {
                        // 提前终止下载
                        job?.cancel()
                    }
                }
            }.bodyAsChannel()
            if (downloadStatusFlow.value is LoadStatus.Loading) {
                channel.copyAndClose(newApkFile.writeChannel())
                downloadStatusFlow.value = LoadStatus.Success(newApkFile.absolutePath)
            }
        } catch (e: Exception) {
            if (downloadStatusFlow.value is LoadStatus.Loading) {
                downloadStatusFlow.value = LoadStatus.Failure(e)
            }
        }
    }
}

@Composable
fun UpgradeDialog() {
    val newVersion by newVersionFlow.collectAsState()
    newVersion?.let { newVersionVal ->
        AlertDialog(title = {
            Text(text = "检测到新版本")
        }, text = {
            Text(text = "v${BuildConfig.VERSION_NAME} -> v${newVersionVal.versionName}\n\n${
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
                newVersionFlow.value = null
                startDownload(newVersionVal)
            }) {
                Text(text = "下载更新")
            }
        }, dismissButton = {
            TextButton(onClick = { newVersionFlow.value = null }) {
                Text(text = "取消")
            }
        })
    }

    val downloadStatus by downloadStatusFlow.collectAsState()
    downloadStatus?.let { downloadStatusVal ->
        when (downloadStatusVal) {
            is LoadStatus.Loading -> {
                AlertDialog(
                    title = { Text(text = "下载新版本中") },
                    text = {
                        LinearProgressIndicator(
                            progress = { downloadStatusVal.progress },
                        )
                    },
                    onDismissRequest = {},
                    confirmButton = {
                        TextButton(onClick = {
                            downloadStatusFlow.value = LoadStatus.Failure(
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
                    title = { Text(text = "新版本下载失败") },
                    text = {
                        Text(text = downloadStatusVal.exception.let {
                            it.message ?: it.toString()
                        })
                    },
                    onDismissRequest = { downloadStatusFlow.value = null },
                    confirmButton = {
                        TextButton(onClick = {
                            downloadStatusFlow.value = null
                        }) {
                            Text(text = "关闭")
                        }
                    },
                )
            }

            is LoadStatus.Success -> {
                AlertDialog(title = { Text(text = "新版本下载完毕") },
                    onDismissRequest = {},
                    dismissButton = {
                        TextButton(onClick = {
                            downloadStatusFlow.value = null
                        }) {
                            Text(text = "关闭")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            AppUtils.installApp(downloadStatusVal.result)
                        }) {
                            Text(text = "安装")
                        }
                    })
            }
        }
    }
}












