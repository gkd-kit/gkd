package li.gkd.app.util

import li.gkd.app.text.UiStrings
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.util.TimeUtils.throttle

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.store.FileStateStore
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.ui.component.GkAlertDialog
import li.songe.codeorigin.CallSite
import java.io.File
import java.net.URI
import kotlin.time.Duration.Companion.days


private val UPDATE_URL: String
    get() = UpdateChannelOption.objects.findOption(storeFlow.value.updateChannel).url

@Serializable
data class NewVersion(
    val versionCode: Int,
    val versionName: String,
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

private var lastCheckTime = 0L

class UpdateStatus(val scope: CoroutineScope) {
    private val checkUpdatingMutex = MutexState()
    val checkUpdatingFlow
        get() = checkUpdatingMutex.state
    private val newVersionFlow = MutableStateFlow<NewVersion?>(null)
    private val downloadStatusFlow = MutableStateFlow<LoadStatus<File>?>(null)
    private var downloadJob: Job? = null

    private val ignoreVersionListFlow by lazy {
        FileStateStore.createJsonFlow(
            key = "ignore_version_list",
            default = { emptySet<Int>() },
            scope = scope,
        )
    }
    private var lastManual = false

    val canRecheck get() = System.currentTimeMillis() - lastCheckTime > 1.days.inWholeMilliseconds

    fun checkUpdate(
        manual: Boolean = false,
        @CallSite loc: String = "",
    ) {
        scope.launchLogged(Dispatchers.IO, loc = loc) {
            try {
                lastManual = manual
                checkUpdatingMutex.tryWithStateLock {
                    lastCheckTime = System.currentTimeMillis()
                    if (!NetworkUtils.isAvailable()) {
                        error(UiStrings.network_unavailable)
                    }
                    val newVersion = client.get(UPDATE_URL).body<NewVersion>()
                    if (newVersion.versionCode <= META.versionCode) {
                        if (manual) toast(UiStrings.updates_none, loc = loc)
                        return@tryWithStateLock
                    }
                    if (
                        !manual &&
                        ignoreVersionListFlow.value.contains(newVersion.versionCode)
                    ) return@tryWithStateLock
                    newVersionFlow.value = newVersion
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) {
                    toast(e.message ?: e.stackTraceToString(), loc = "")
                }
                throw e
            }
        }
    }

    private fun startDownload(newVersion: NewVersion) {
        if (downloadStatusFlow.value is LoadStatus.Loading) return
        downloadStatusFlow.value = LoadStatus.Loading(0f)
        val apkFile = FolderUtils.sharedDir.resolve("gkd-v${newVersion.versionCode}.apk").apply {
            if (exists()) {
                delete()
            }
        }
        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                val channel =
                    client.get(URI(UPDATE_URL).resolve(newVersion.downloadUrl).toString()) {
                        onDownload { bytesSentTotal, _ ->
                            val downloadStatus = downloadStatusFlow.value
                            if (downloadStatus is LoadStatus.Loading) {
                                downloadStatusFlow.value = LoadStatus.Loading(
                                    bytesSentTotal.toFloat() / (newVersion.fileSize)
                                )
                            } else if (downloadStatus is LoadStatus.Failure) {
                                // 提前终止下载
                                downloadJob?.cancel()
                            }
                        }
                    }.bodyAsChannel()
                if (downloadStatusFlow.value is LoadStatus.Loading) {
                    channel.copyAndClose(apkFile.writeChannel())
                    downloadStatusFlow.value = LoadStatus.Success(apkFile)
                }
            } catch (e: Exception) {
                if (downloadStatusFlow.value is LoadStatus.Loading) {
                    downloadStatusFlow.value = LoadStatus.Failure(e)
                }
            } finally {
                downloadJob = null
            }
        }
    }

    @Composable
    fun UpgradeDialog() {
        newVersionFlow.collectAsStateWithLifecycle().value?.let { newVersionVal ->
            val text = remember {
                val logs = newVersionVal.versionLogs.takeWhile { v ->
                    v.code > META.versionCode
                }
                "v${META.versionName} -> v${newVersionVal.versionName}\n\n${
                    if (logs.size > 1) {
                        logs.joinToString("\n\n") { v -> "v${v.name}\n${v.desc}" }
                    } else if (logs.isNotEmpty()) {
                        logs.first().desc
                    } else {
                        ""
                    }
                }".trimEnd()
            }
            val scrollState = rememberScrollState()
            GkAlertDialog(
                title = {
                    Text(text = UiStrings.update_new_version)
                },
                text = {
                    Text(
                        text = text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(scrollState)
                    )
                },
                onDismissRequest = { },
                confirmButton = {
                    TextButton(onClick = {
                        newVersionFlow.value = null
                        startDownload(newVersionVal)
                    }) {
                        Text(text = UiStrings.update_download)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { newVersionFlow.value = null }) {
                        Text(text = UiStrings.action_cancel)
                    }
                    if (!lastManual) {
                        TextButton(onClick = {
                            newVersionFlow.value = null
                            ignoreVersionListFlow.update {
                                it + newVersionVal.versionCode
                            }
                            toast(UiStrings.update_version_ignored)
                        }) {
                            Text(text = UiStrings.action_ignore)
                        }
                    }
                },
            )
        }

        downloadStatusFlow.collectAsStateWithLifecycle().value?.let { downloadStatusVal ->
            when (downloadStatusVal) {
                is LoadStatus.Loading -> {
                    GkAlertDialog(
                        title = { Text(text = UiStrings.image_downloading) },
                        text = {
                            LinearProgressIndicator(
                                progress = { downloadStatusVal.progress },
                            )
                        },
                        onDismissRequest = {},
                        confirmButton = {
                            TextButton(onClick = {
                                downloadStatusFlow.value = LoadStatus.Failure(
                                    Exception(UiStrings.download_abort)
                                )
                            }) {
                                Text(text = UiStrings.download_abort)
                            }
                        },
                    )
                }

                is LoadStatus.Failure -> {
                    GkAlertDialog(
                        title = { Text(text = UiStrings.download_failed) },
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
                                Text(text = UiStrings.action_close)
                            }
                        },
                    )
                }

                is LoadStatus.Success -> {
                    GkAlertDialog(
                        title = { Text(text = UiStrings.download_complete) },
                        text = {
                            Text(text = UiStrings.update_ready_to_install)
                        },
                        onDismissRequest = {},
                        dismissButton = {
                            TextButton(onClick = {
                                downloadStatusFlow.value = null
                            }) {
                                Text(text = UiStrings.action_close)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = throttle {
                                installApk(downloadStatusVal.result)
                            }) {
                                Text(text = UiStrings.action_install)
                            }
                        })
                }
            }
        }
    }
}


private fun installApk(file: File) {
    val uri = FileProvider.getUriForFile(
        app,
        "${app.packageName}.provider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setDataAndType(uri, "application/vnd.android.package-archive")
    }
    app.tryStartActivity(intent)
}
