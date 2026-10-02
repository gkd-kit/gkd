package li.gkd.app.ui

import android.content.Intent
import android.net.Uri
import androidx.annotation.MainThread
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.META
import li.gkd.app.a11y.useA11yServiceEnabledFlow
import li.gkd.app.a11y.useEnabledA11yServicesFlow
import li.gkd.app.backup.BackupManager
import li.gkd.app.crash.CrashData
import li.gkd.app.crash.FileCrashStorage
import li.gkd.app.entry.EntryActivity
import li.gkd.app.entry.OpenFileActivity
import li.gkd.app.network.AppLinks
import li.gkd.app.network.NetworkAvailability
import li.gkd.app.permission.PermissionRequests
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.service.ServiceController
import li.gkd.app.priv.AutomationService
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.resources.Res
import li.gkd.app.resources.automation_state_check_failed
import li.gkd.app.resources.backup_import_confirmation
import li.gkd.app.resources.backup_import_label
import li.gkd.app.resources.backup_import_progress
import li.gkd.app.resources.import_success
import li.gkd.app.resources.uri_unknown
import li.gkd.app.service.A11yService
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.StorageMaintenance
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.component.ShareLogState
import li.gkd.app.ui.component.TextDialogState
import li.gkd.app.ui.home.BottomNavItem
import li.gkd.app.ui.home.HomeNavigation
import li.gkd.app.ui.navigation.CrashReportRoute
import li.gkd.app.ui.navigation.GkdLink
import li.gkd.app.ui.navigation.HomeRoute
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.option.AutomatorModeOption
import li.gkd.app.ui.share.ActivityResultRequests
import li.gkd.app.ui.share.DeletionTarget
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.ui.subscription.RuleControlDialogState
import li.gkd.app.ui.subscription.RuleGroupState
import li.gkd.app.ui.subscription.SubsLinkDialogState
import li.gkd.app.ui.subscription.SubsSheetHost
import li.gkd.app.ui.subscription.SubsSheetState
import li.gkd.app.ui.text.getSync
import li.gkd.app.ui.update.UpdateStatus
import li.gkd.app.ui.upload.GithubUploadState
import li.gkd.app.util.AndroidStorage
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.JsonUtils
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ThreadUtils
import li.gkd.app.util.ThrottleTimer
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.launchLogged
import li.songe.codeorigin.CallSite
import java.nio.file.Files
import kotlin.time.Duration.Companion.days

class MainViewModel : BaseViewModel() {
    companion object {
        private var currentInstance: MainViewModel? = null

        /** Only available to the main UI after its Activity has bound the request hosts. */
        @MainThread
        fun requireCurrent(): MainViewModel = checkNotNull(currentInstance) {
            "MainViewModel is not registered; a bound MainActivity is required"
        }
    }

    init {
        LogUtils.d("MainViewModel:init")
        addCloseable {
            if (currentInstance === this) {
                currentInstance = null
            }
            LogUtils.d("MainViewModel:close")
        }
    }

    /** Called by MainActivity after binding permission and Activity Result hosts. */
    @MainThread
    fun registerCurrent() {
        currentInstance = this
    }

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
    val permissionRequests = PermissionRequests {
        navigatePage(PrivilegeServiceRoute)
    }

    val backStack: NavBackStack<NavKey> = NavBackStack(HomeRoute)
    val topRoute get() = backStack.last()

    private val backThrottleTimer = ThrottleTimer()

    fun popPage(@CallSite loc: String = "") = ThreadUtils.runMainOrPost {
        if (backThrottleTimer.expired() && backStack.size > 1) {
            val old = backStack.last()
            backStack.removeAt(backStack.lastIndex)
            LogUtils.d("popPage", "$old -> ${backStack.last()}", loc = loc)
        }
    }

    fun navigatePage(
        navKey: NavKey,
        replaced: Boolean = false,
        @CallSite loc: String = "",
    ) = ThreadUtils.runMainOrPost {
        if (navKey != backStack.last()) {
            val old = backStack.last()
            if (replaced) {
                backStack[backStack.lastIndex] = navKey
            } else {
                backStack.add(navKey)
            }
            LogUtils.d("navigatePage", "$old -> ${backStack.last()}", loc = loc)
        }
    }

    fun navigateWebPage(url: String) = navigatePage(WebViewRoute(url))

    val dialogRequests = DialogRequests()

    fun confirmDelete(
        title: String,
        text: String,
        targets: () -> Set<DeletionTarget> = { emptySet() },
        dismiss: () -> Unit = {},
        delete: suspend () -> Unit,
    ) = scope.launchUi {
        if (!dialogRequests.confirm(title = title, text = text, error = true)) return@launchUi
        val deletedTargets = targets()
        dismiss()
        subsSheet.dismissForDeletion(deletedTargets)
        ruleGroupState.dismissForDeletion(deletedTargets)
        ruleControlDialog.dismissForDeletion(deletedTargets)
        // Remove the owning page and its descendants synchronously, without the back-button throttle.
        val firstOwned = backStack.indexOfFirst { route -> deletedTargets.any { it.owns(route) } }
        if (firstOwned > 0) {
            while (backStack.size > firstOwned) backStack.removeAt(backStack.lastIndex)
        }
        delete()
    }

    val updateStatus = if (META.updateEnabled) UpdateStatus(
        scope,
        META.versionCode,
        META.versionName,
        { ToastUtils.show(it) },
        NetworkAvailability::canResolveProbeHost,
    ) else null

    val githubUpload = GithubUploadState(
        scope = scope,
        onOpenCookieHelp = { navigateWebPage(AppLinks.CookieHelp) },
        toast = { ToastUtils.show(it) },
    )

    val shareLog = ShareLogState(
        scope = scope,
        githubUpload = githubUpload,
    )

    val subsLinkDialog = SubsLinkDialogState(
        toast = { ToastUtils.show(it) },
        onOpenHelp = { navigateWebPage(AppLinks.SubscriptionHelp) },
        requestLocalNetworkPermission = {
            permissionRequests.ensurePermissions(PermissionStates.localNetwork)
        },
    )

    val subsSheet: SubsSheetState = SubsSheetState {
        SubsSheetHost(
            META.appName,
            { navigatePage(it) },
            ::openUrl,
            { subsLinkDialog.request(it) },
            { ToastUtils.show(it) },
            { title, text, targets, dismiss, delete ->
                confirmDelete(
                    title,
                    text,
                    targets,
                    dismiss,
                    delete
                )
            },
        )
    }

    val ruleGroupState = RuleGroupState()
    val ruleControlDialog = RuleControlDialogState()

    val textDialog = TextDialogState(IntentUtils::openUri)

    fun openUrl(url: String) {
        textDialog.showUrl(url)
    }

    val homeNavigation = HomeNavigation()

    fun handleGkdUri(uri: Uri) {
        val notFoundToast = { ToastUtils.show(Res.string.uri_unknown.getSync(uri)) }
        when (val link = GkdLink.parse(uri.toString())) {
            is GkdLink.Home -> ThreadUtils.runMainOrPost {
                val tab = link.tab
                if (tab != null && BottomNavItem.allSubObjects.any { it.key == tab }) {
                    homeNavigation.selectTab(BottomNavItem.allSubObjects.first { it.key == tab })
                }
                // MainActivity 被复用时，也需要返回首页。
                backStack.subList(1, backStack.size).clear()
            }

            is GkdLink.Page -> navigatePage(link.route)
            GkdLink.WeChatScanner -> IntentUtils.openWeChatScanner()
            null -> notFoundToast()
        }
    }

    fun handleIntent(intent: Intent) = scope.launchUi {
        LogUtils.d(intent)
        val uri = intent.data?.normalizeScheme()
        val source = intent.getStringExtra(EntryActivity.activityNavSourceName)
        if (uri?.scheme == "gkd") {
            handleGkdUri(uri)
        } else if (source == OpenFileActivity::class.java.name && uri != null) {
            if (!dialogRequests.confirm(
                    title = Res.string.backup_import_label.getSync(),
                    text = Res.string.backup_import_confirmation.getSync(),
                )
            ) {
                return@launchUi
            }
            ToastUtils.show(Res.string.backup_import_progress.getSync())
            withContext(Dispatchers.IO) { BackupManager.importData(FileSource.Uri(uri.toString())) }
            ToastUtils.show(Res.string.import_success.getSync())
        }
    }


    private val a11yServicesFlow = useEnabledA11yServicesFlow(scope)
    val a11yServiceEnabledFlow = useA11yServiceEnabledFlow(scope, a11yServicesFlow)


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
        updateAutomatorModeJob = scope.launch {
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

    private var tempCrashDataList = emptyList<CrashData>()

    fun takeCrashDataList(): List<CrashData> = tempCrashDataList.also {
        tempCrashDataList = emptyList()
    }

    init {
        scope.launchLogged(Dispatchers.IO) {
            // 每次进入删除缓存
            StorageMaintenance.clearExpired(AndroidStorage.storage)
        }

        if (SettingsRepository.termsAccepted.value && updateStatus?.canRecheck == true) {
            updateStatus.checkUpdate()
        }

        scope.launchLogged(Dispatchers.IO) {
            FileCrashStorage.trim()
            val list = (AndroidStorage.storage.crashTemp.listFiles() ?: emptyArray()).mapNotNull {
                try {
                    JsonUtils.default.decodeFromString<CrashData>(it.readText())
                } catch (e: Exception) {
                    LogUtils.d("解析崩溃日志失败: ${it.name}", e)
                    null
                }
            }.sortedBy { -it.mtime }
            AndroidStorage.storage.crashTemp.deleteRecursively()
            val t = System.currentTimeMillis()
            AndroidStorage.storage.crash.listFiles()?.filter {
                val name = it.name
                !list.any { f -> name == f.filename }
            }?.forEach {
                val mtime = Files.getLastModifiedTime(it.toPath()).toMillis()
                if (t - mtime > 30.days.inWholeMilliseconds) {
                    it.delete()
                }
            }
            tempCrashDataList = list
            if (list.isNotEmpty()) {
                navigatePage(CrashReportRoute)
            }
        }

    }
}
