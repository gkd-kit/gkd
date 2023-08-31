package li.songe.gkd.util

import android.os.Parcelable
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.PathUtils
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import li.songe.gkd.BuildConfig
import li.songe.gkd.appScope
import java.io.File
import java.net.URI

@Serializable
@Parcelize
data class NewVersion(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
) : Parcelable

sealed class LoadStatus<out T> {
    data class Loading(val progress: Float) : LoadStatus<Nothing>()
    data class Failure(val exception: Exception) : LoadStatus<Nothing>()
    data class Success<T>(val result: T) : LoadStatus<T>()
}

private const val UPDATE_URL = "https://registry.npmmirror.com/@gkd-kit/app/latest/files/index.json"

val checkUpdatingFlow by lazy { MutableStateFlow(false) }
val newVersionFlow by lazy { MutableStateFlow<NewVersion?>(null) }
val downloadStatusFlow by lazy { MutableStateFlow<LoadStatus<String>?>(null) }
suspend fun checkUpdate(): NewVersion? {
    if (checkUpdatingFlow.value || newVersionFlow.value != null || downloadStatusFlow.value != null) return null
    checkUpdatingFlow.value = true
    try {
        val newVersion = Singleton.client.get(UPDATE_URL).body<NewVersion>()
        if (newVersion.versionCode > BuildConfig.VERSION_CODE) {
            newVersionFlow.value = newVersion
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
    val newApkFile = File(PathUtils.getExternalAppCachePath() + "/v${newVersion.versionCode}.apk")
    if (newApkFile.exists()) {
        newApkFile.delete()
    }
    appScope.launch {
        try {
            val channel =
                Singleton.client.get(URI(UPDATE_URL).resolve(newVersion.downloadUrl).toString()) {
                    onDownload { bytesSentTotal, contentLength ->
                        downloadStatusFlow.value =
                            LoadStatus.Loading(bytesSentTotal.toFloat() / contentLength)
                    }
                }.bodyAsChannel()
            channel.copyAndClose(newApkFile.writeChannel())
            downloadStatusFlow.value = LoadStatus.Success(newApkFile.absolutePath)
        } catch (e: Exception) {
            downloadStatusFlow.value = LoadStatus.Failure(e)
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
            Text(text = "v${BuildConfig.VERSION_NAME} -> v${newVersionVal.versionName}\n\n${newVersionVal.changelog}".trimEnd())
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
                Dialog(onDismissRequest = { }) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "下载新版本中,稍等片刻", fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(progress = downloadStatusVal.progress)
                    }
                }
            }

            is LoadStatus.Failure -> {
                AlertDialog(
                    title = { Text(text = "下载失败") },
                    text = { Text(text = downloadStatusVal.exception.toString()) },
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
                AlertDialog(title = { Text(text = "下载完毕") },
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

fun initUpgrade() {
    if (storeFlow.value.autoCheckAppUpdate) {
        appScope.launch {
            try {
                checkUpdate()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}












