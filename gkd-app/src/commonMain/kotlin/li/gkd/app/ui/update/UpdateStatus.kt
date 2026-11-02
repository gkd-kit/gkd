package li.gkd.app.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.net.URI
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
import li.gkd.app.META
import li.gkd.app.error.displayMessage
import li.gkd.app.model.findOption
import li.gkd.app.network.NewVersion
import li.gkd.app.network.UpdateClient
import li.gkd.app.network.isNetworkRequestAllowed
import li.gkd.app.network.logNetworkFailure
import li.gkd.app.platform.PackageInstallCapability
import li.gkd.app.platform.PlatformResult
import li.gkd.app.platform.installPackagePrivileged
import li.gkd.app.platform.privilegedPackageInstallCapability
import li.gkd.app.platform.requestPackageInstall
import li.gkd.app.resources.*
import li.gkd.app.settings.FileSettingsStorage
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.UpdateChannel
import li.gkd.app.storage.appStorage
import li.gkd.app.ui.component.GkUpgradeDialogs
import li.gkd.app.ui.component.UpdateDownloadState
import li.gkd.app.ui.component.UpdateInstallState
import li.gkd.app.ui.home.importAppBackup
import li.gkd.app.ui.home.saveAppBackup
import li.gkd.app.ui.home.shareAppBackup
import li.gkd.app.ui.page.BackupDialog
import li.gkd.app.ui.page.GkBackupDialogs
import li.gkd.app.ui.platform.LocalUiHost
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString

/** App-owned update workflow; only package installation belongs to the platform. */
class UpdateStatus(
    private val scope: CoroutineScope,
) {
    private val storage by lazy { FileSettingsStorage(appStorage().store) }
    private val directory
        get() = appStorage().sharedCache

    val checkUpdatingFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    private val newVersion = MutableStateFlow<NewVersion?>(null)
    private val downloadStatus = MutableStateFlow<UpdateDownloadState?>(null)
    private var downloadJob: Job? = null
    private var downloaded: File? = null
    private var downloadedVersion: NewVersion? = null
    private val installState = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    private val installCapability = privilegedPackageInstallCapability()
    private var versionUrl = ""
    private var lastManual = false
    private var lastCheckTime = 0L
    private val ignoreMutex = Mutex()

    private fun readIgnored(): Set<Int> =
        storage
            .read("ignore_version_list.json")
            ?.let { Json.decodeFromString<Set<Int>>(it) }
            .orEmpty()

    val canRecheck
        get() = System.currentTimeMillis() - lastCheckTime > 86_400_000L

    fun checkUpdate(manual: Boolean = false) {
        if (checkUpdatingFlow.value) return
        checkUpdatingFlow.value = true
        scope.launch {
            try {
                lastCheckTime = System.currentTimeMillis()
                val ignored =
                    if (manual) emptySet()
                    else ignoreMutex.withLock { withContext(Dispatchers.IO) { readIgnored() } }
                if (!isNetworkRequestAllowed()) {
                    if (manual) ToastUtils.show(getString(Res.string.network_unavailable))
                    return@launch
                }
                val url =
                    UpdateChannel.entries
                        .findOption(SettingsRepository.settings.value.updateChannel)
                        .url
                val result = withContext(Dispatchers.IO) { UpdateClient.fetch(url) }
                if (result.versionCode <= META.versionCode) {
                    if (manual) ToastUtils.show(getString(Res.string.updates_none))
                } else if (manual || result.versionCode !in ignored) {
                    lastManual = manual
                    versionUrl = url
                    newVersion.value = result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logNetworkFailure("Update check failed", e)
                if (manual) ToastUtils.show(e.displayMessage())
            } finally {
                checkUpdatingFlow.value = false
            }
        }
    }

    private fun startDownload(version: NewVersion) {
        if (installState.value is UpdateInstallState.Active) return
        if (downloadJob?.isActive == true) return
        newVersion.value = null
        downloadStatus.value = UpdateDownloadState.Loading(0f)
        downloaded = null
        downloadedVersion = null
        installState.value = UpdateInstallState.Idle
        val file = directory.resolve("gkd-v${version.versionCode}.apk")
        downloadJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    directory.mkdirs()
                    val transfer = currentCoroutineContext()
                    UpdateClient.download(
                        URI(versionUrl).resolve(version.downloadUrl).toString(),
                        file,
                        version.fileSize,
                    ) {
                        if (transfer.isActive)
                            downloadStatus.value = UpdateDownloadState.Loading(it.coerceIn(0f, 1f))
                    }
                }
                ensureActive()
                downloaded = file
                downloadedVersion = version
                downloadStatus.value = UpdateDownloadState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                downloadStatus.value = UpdateDownloadState.Failure(e)
            } finally {
                if (downloaded == null)
                    withContext(NonCancellable + Dispatchers.IO) { file.delete() }
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
                            Json.encodeToString(next),
                        )
                    }
                    if (newVersion.value == version) newVersion.value = null
                    ToastUtils.show(getString(Res.string.update_version_ignored))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToastUtils.show(e.displayMessage())
            }
        }
    }

    private fun installInternally(host: UiHost) {
        if (installState.value is UpdateInstallState.Active) return
        val file = downloaded ?: return
        val version = downloadedVersion ?: return
        installState.value = UpdateInstallState.Active
        scope.launch {
            try {
                when (installPackagePrivileged(file, version.versionCode, host)) {
                    is PlatformResult.Success -> installState.value = UpdateInstallState.Success
                    PlatformResult.Unsupported -> {
                        installState.value = UpdateInstallState.Idle
                        ToastUtils.show(getString(Res.string.platform_action_unsupported))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                installState.value = UpdateInstallState.Failure(e)
            }
        }
    }

    @Composable
    fun UpgradeDialog() {
        val host = LocalUiHost.current
        val version by newVersion.collectAsStateWithLifecycle()
        var backupDialog by remember(version) { mutableStateOf<BackupDialog?>(null) }
        val status by downloadStatus.collectAsStateWithLifecycle()
        val installation by installState.collectAsStateWithLifecycle()
        val capability by
            installCapability.collectAsStateWithLifecycle(PackageInstallCapability.Checking)
        val text = version?.let { value ->
            val logs = value.versionLogs.takeWhile { it.code > META.versionCode }
            ("v${META.versionName} -> v${value.versionName}\n\n" +
                    when {
                        logs.size > 1 -> logs.joinToString("\n\n") { "v${it.name}\n${it.desc}" }
                        logs.isNotEmpty() -> logs.first().desc
                        else -> ""
                    })
                .trimEnd()
        }
        GkUpgradeDialogs(
            text,
            !lastManual,
            status,
            onDownload = { version?.let(::startDownload) },
            onDismissVersion = { newVersion.value = null },
            onIgnore = ::ignoreVersion,
            onBackup = { backupDialog = BackupDialog.Export },
            onCancelDownload = {
                downloadJob?.cancel()
                downloadStatus.value =
                    UpdateDownloadState.Failure(Exception(Res.string.download_abort.getSync()))
            },
            onDismissDownload = { downloadStatus.value = null },
            installCapability = capability,
            installState = installation,
            downloadedVersion = downloadedVersion?.versionName,
            onInternalInstall = { installInternally(host) },
            onInstall = {
                downloaded?.let { file ->
                    try {
                        if (requestPackageInstall(file) == PlatformResult.Unsupported)
                            ToastUtils.show(Res.string.platform_action_unsupported.getSync())
                    } catch (e: Exception) {
                        ToastUtils.show(e.displayMessage())
                    }
                }
            },
        )
        GkBackupDialogs(
            dialog = backupDialog,
            onDismiss = { backupDialog = null },
            onExport = { backupDialog = BackupDialog.Export },
            onImport = host::importAppBackup,
            onShare = host::shareAppBackup,
            onSaveDownloads = host::saveAppBackup,
        )
    }
}
