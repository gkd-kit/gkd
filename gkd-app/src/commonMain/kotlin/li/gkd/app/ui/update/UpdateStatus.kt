package li.gkd.app.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import li.gkd.app.network.NewVersion
import li.gkd.app.network.UpdateClient
import li.gkd.app.platform.PlatformResult
import li.gkd.app.platform.requestPackageInstall
import li.gkd.app.resources.Res
import li.gkd.app.resources.download_abort
import li.gkd.app.resources.network_unavailable
import li.gkd.app.resources.platform_action_unsupported
import li.gkd.app.resources.update_version_ignored
import li.gkd.app.resources.updates_none
import li.gkd.app.settings.FileSettingsStorage
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.storage.appStorage
import li.gkd.app.ui.component.GkUpgradeDialogs
import li.gkd.app.ui.component.UpdateDownloadState
import li.gkd.app.ui.option.UpdateChannelOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.text.displayMessage
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.LogUtils
import org.jetbrains.compose.resources.getString
import java.io.File
import java.net.URI

/** App-owned update workflow; only package installation belongs to the platform. */
class UpdateStatus(
    private val scope: CoroutineScope,
    private val versionCode: Int,
    private val versionName: String,
    private val toast: (String) -> Unit,
    private val networkAvailable: () -> Boolean,
) {
    private val storage by lazy { FileSettingsStorage(appStorage().store) }
    private val directory get() = appStorage().sharedCache

    val checkUpdatingFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    private val newVersion = MutableStateFlow<NewVersion?>(null)
    private val downloadStatus = MutableStateFlow<UpdateDownloadState?>(null)
    private var downloadJob: Job? = null
    private var downloaded: File? = null
    private var versionUrl = ""
    private var lastManual = false
    private var lastCheckTime = 0L
    private val ignoreMutex = Mutex()
    private fun readIgnored(): Set<Int> =
        storage.read("ignore_version_list.json")?.let { Json.decodeFromString<Set<Int>>(it) }
            .orEmpty()
    val canRecheck get() = System.currentTimeMillis() - lastCheckTime > 86_400_000L

    fun checkUpdate(manual: Boolean = false) {
        if (checkUpdatingFlow.value) return
        checkUpdatingFlow.value = true
        scope.launch {
            try {
                lastCheckTime = System.currentTimeMillis()
                val ignored = ignoreMutex.withLock { withContext(Dispatchers.IO) { readIgnored() } }
                check(networkAvailable()) { getString(Res.string.network_unavailable) }
                val url =
                    UpdateChannelOption.objects.findOption(SettingsRepository.settings.value.updateChannel).url
                val result = withContext(Dispatchers.IO) { UpdateClient.fetch(url) }
                if (result.versionCode <= versionCode) {
                    if (manual) toast(getString(Res.string.updates_none))
                } else if (manual || result.versionCode !in ignored) {
                    lastManual = manual; versionUrl = url; newVersion.value = result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.d("Update check failed", e)
                if (manual) toast(e.displayMessage())
            } finally {
                checkUpdatingFlow.value = false
            }
        }
    }

    private fun startDownload(version: NewVersion) {
        if (downloadJob?.isActive == true) return
        newVersion.value = null
        downloadStatus.value = UpdateDownloadState.Loading(0f)
        downloaded = null
        val file = directory.resolve("gkd-v${version.versionCode}.apk")
        downloadJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    directory.mkdirs()
                    val transfer = currentCoroutineContext()
                    UpdateClient.download(
                        URI(versionUrl).resolve(version.downloadUrl).toString(),
                        file,
                        version.fileSize
                    ) {
                        if (transfer.isActive) downloadStatus.value =
                            UpdateDownloadState.Loading(it.coerceIn(0f, 1f))
                    }
                }
                ensureActive()
                downloaded = file
                downloadStatus.value = UpdateDownloadState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                downloadStatus.value = UpdateDownloadState.Failure(e)
            } finally {
                if (downloaded == null) withContext(NonCancellable + Dispatchers.IO) { file.delete() }
            }
        }
    }

    private fun ignoreVersion() {
        val version = newVersion.value ?: return
        scope.launch {
            try {
                ignoreMutex.withLock {
                    withContext(Dispatchers.IO) {
                        val next = readIgnored() + version.versionCode
                        storage.writeAtomically(
                            "ignore_version_list.json",
                            Json.encodeToString(next)
                        )
                    }
                    if (newVersion.value == version) newVersion.value = null
                    toast(getString(Res.string.update_version_ignored))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.displayMessage())
            }
        }
    }

    @Composable
    fun UpgradeDialog() {
        val version by newVersion.collectAsStateWithLifecycle()
        val status by downloadStatus.collectAsStateWithLifecycle()
        val text = version?.let { value ->
            val logs = value.versionLogs.takeWhile { it.code > versionCode }
            ("v$versionName -> v${value.versionName}\n\n" + when {
                logs.size > 1 -> logs.joinToString("\n\n") { "v${it.name}\n${it.desc}" }
                logs.isNotEmpty() -> logs.first().desc
                else -> ""
            }).trimEnd()
        }
        GkUpgradeDialogs(
            text,
            !lastManual,
            status,
            onDownload = { version?.let(::startDownload) },
            onDismissVersion = { newVersion.value = null },
            onIgnore = ::ignoreVersion,
            onCancelDownload = {
                downloadJob?.cancel()
                downloadStatus.value =
                    UpdateDownloadState.Failure(Exception(Res.string.download_abort.getSync()))
            },
            onDismissDownload = { downloadStatus.value = null },
            onInstall = {
                downloaded?.let { file ->
                    try {
                        if (requestPackageInstall(file) == PlatformResult.Unsupported) toast(Res.string.platform_action_unsupported.getSync())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        toast(e.displayMessage())
                    }
                }
            })
    }
}
