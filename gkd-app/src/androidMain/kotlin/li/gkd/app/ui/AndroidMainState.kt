package li.gkd.app.ui

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.crash.FileCrashStorage
import li.gkd.app.entry.EntryActivity
import li.gkd.app.entry.OpenFileActivity
import li.gkd.app.permission.PermissionRequests
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.service.ServiceController
import li.gkd.app.priv.AutomationService
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.resources.*
import li.gkd.app.service.A11yService
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.StorageMaintenance
import li.gkd.app.ui.home.BottomNavItem
import li.gkd.app.ui.navigation.GkdLink
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.option.AutomatorModeOption
import li.gkd.app.ui.share.ActivityResultRequests
import li.gkd.app.ui.share.importBackup as importBackupArchive
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.AndroidStorage
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ThreadUtils
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.launchLogged

fun createMainViewModel(): MainViewModel {
    lateinit var vm: MainViewModel
    val permissions = PermissionRequests { vm.navigator.navigate(PrivilegeServiceRoute) }
    vm = MainViewModel(permissions)
    vm.addCloseable("android", AndroidMainState(vm, permissions))
    return vm
}

val MainViewModel.androidState: AndroidMainState
    get() = requireNotNull(getCloseable<AndroidMainState>("android"))

class AndroidMainState(
    private val mainVm: MainViewModel,
    val permissionRequests: PermissionRequests,
) : AutoCloseable {
    /** Returns whether the start request was issued; observe StatusService for running state. */
    suspend fun enableStatusService(): Boolean {
        if (!permissionRequests.ensurePermissions(
                PermissionStates.foregroundServiceSpecialUse,
                PermissionStates.notification,
            )
        ) return false
        ServiceController.setStatusEnabled(true)
        return true
    }

    val activityResults = ActivityResultRequests()

    fun handleGkdUri(uri: Uri) {
        val notFoundToast = { ToastUtils.show(Res.string.uri_unknown.getSync(uri)) }
        when (val link = GkdLink.parse(uri.toString())) {
            is GkdLink.Home -> ThreadUtils.runMainOrPost {
                val tab = link.tab
                if (tab != null && BottomNavItem.allSubObjects.any { it.key == tab }) {
                    mainVm.homeNavigation.selectTab(BottomNavItem.allSubObjects.first { it.key == tab })
                }
                // MainActivity 被复用时，也需要返回首页。
                mainVm.navigator.popToHome()
            }

            is GkdLink.Page -> mainVm.navigator.navigate(link.route)
            GkdLink.WeChatScanner -> IntentUtils.openWeChatScanner()
            null -> notFoundToast()
        }
    }

    fun handleIntent(intent: Intent) = mainVm.scope.launchUi {
        LogUtils.d(intent)
        val uri = intent.data?.normalizeScheme()
        val source = intent.getStringExtra(EntryActivity.activityNavSourceName)
        if (uri?.scheme == "gkd") {
            handleGkdUri(uri)
        } else if (source == OpenFileActivity::class.java.name && uri != null) {
            if (!mainVm.dialogRequests.confirm(
                    title = Res.string.backup_import_label.getSync(),
                    text = Res.string.backup_import_confirmation.getSync(),
                )
            ) {
                return@launchUi
            }
            importBackupArchive(FileSource.Uri(uri.toString()))
        }
    }

    override fun close() { updateAutomatorModeJob?.cancel() }

    private var updateAutomatorModeJob: Job? = null

    private fun applyAutomatorMode(option: AutomatorModeOption) {
        SettingsRepository.updateAutomatorMode(option.value)
        A11yService.instance?.shutdown()
        uiAutomationFlow.value?.shutdown()
    }

    fun updateAutomatorMode(option: AutomatorModeOption) {
        updateAutomatorModeJob?.cancel()
        if (SettingsRepository.settings.value.automatorMode == option.value) return
        if (
            option != AutomatorModeOption.AutomationMode ||
            privilegeContextFlow.value == null
        ) {
            applyAutomatorMode(option)
            return
        }
        updateAutomatorModeJob = mainVm.scope.launch {
            val occupied = try {
                withContext(Dispatchers.IO) {
                    AutomationService.isOtherUiAutomationRunning()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToastUtils.show(Res.string.automation_state_check_failed.getSync(e.message))
                LogUtils.d("detect automation state failed", e)
                return@launch
            }
            if (occupied) {
                AutomationService.showOccupiedWarning()
                return@launch
            }
            applyAutomatorMode(option)
        }
    }

    init {
        mainVm.scope.launchLogged(Dispatchers.IO) {
            // 每次进入删除缓存
            StorageMaintenance.clearExpired(AndroidStorage.storage)
        }

        if (SettingsRepository.termsAccepted.value && mainVm.updateStatus?.canRecheck == true) {
            mainVm.updateStatus.checkUpdate()
        }

        mainVm.scope.launchLogged(Dispatchers.IO) {
            val list = FileCrashStorage.takePending()
            withContext(Dispatchers.Main.immediate) { mainVm.showCrashReports(list) }
        }

    }
}
