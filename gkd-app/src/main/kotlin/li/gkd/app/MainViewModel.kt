package li.gkd.app

import android.content.Intent
import android.net.Uri
import androidx.annotation.MainThread
import li.gkd.app.platform.service.ServiceController
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.text.UiStrings
import li.gkd.app.a11y.useA11yServiceEnabledFlow
import li.gkd.app.a11y.useEnabledA11yServicesFlow
import li.gkd.app.data.CrashData
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.data.backup.BackupManager
import li.gkd.app.data.trimCrashDataFiles
import li.gkd.app.entry.EntryActivity
import li.gkd.app.entry.OpenFileActivity
import li.gkd.app.priv.AutomationService
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.permission.PermissionRequests
import li.gkd.app.permission.PermissionStates
import li.gkd.app.service.A11yService
import li.gkd.app.store.AppStore
import li.gkd.app.store.FileStateStore
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.feature.settings.AdvancedPageRoute
import li.gkd.app.ui.CrashReportRoute
import li.gkd.app.ui.PrivilegeServiceRoute
import li.gkd.app.feature.snapshot.SnapshotPageRoute
import li.gkd.app.ui.WebViewRoute
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.component.GithubUploadState
import li.gkd.app.feature.subscription.RuleGroupState
import li.gkd.app.ui.component.ShareLogState
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.app.feature.subscription.SubsLinkDialogState
import li.gkd.app.feature.subscription.SubsSheetState
import li.gkd.app.ui.component.TextDialogState
import li.gkd.app.ui.home.BottomNavItem
import li.gkd.app.ui.home.HomeRoute
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.ui.share.ActivityResultRequests
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.share.DeletionTarget
import li.gkd.app.util.AutomatorModeOption
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.util.ThrottleTimer
import li.gkd.app.util.UpdateStatus
import li.gkd.app.util.FolderUtils
import li.gkd.app.util.findOption
import li.gkd.app.util.json
import li.gkd.app.util.launchLogged
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.runMainPost
import li.gkd.app.util.ToastUtils.toast
import li.gkd.db.Db
import li.songe.codeorigin.CallSite
import java.nio.file.Files
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration.Companion.days

data class PageScrollResetRequest(
    val id: Long,
    val navItem: BottomNavItem,
)

class MainViewModel : BaseViewModel() {
    companion object {
        private var tempTermsAccepted = false
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

    val termsStepFlow: StateFlow<Int>
        field = MutableStateFlow(0)

    fun acceptTermsStep(lastStep: Int) {
        if (termsStepFlow.value < lastStep) {
            termsStepFlow.value++
        } else {
            termsAcceptedFlow.value = true
        }
    }

    val activityResults = ActivityResultRequests()
    val permissionRequests = PermissionRequests {
        navigatePage(PrivilegeServiceRoute)
    }

    val backStack: NavBackStack<NavKey> = NavBackStack(HomeRoute)
    val topRoute get() = backStack.last()

    private val backThrottleTimer = ThrottleTimer()

    fun popPage(@CallSite loc: String = "") = runMainPost {
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
    ) = runMainPost {
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

    val updateStatus = if (META.updateEnabled) UpdateStatus(scope) else null

    val githubUpload = GithubUploadState(
        scope = scope,
        onOpenCookieHelp = { navigateWebPage(ShortUrlSet.URL1) },
    )

    val shareLog = ShareLogState(
        scope = scope,
        githubUpload = githubUpload,
    )

    val subsLinkDialog = SubsLinkDialogState(
        onOpenHelp = { navigateWebPage(ShortUrlSet.URL5) },
        requestLocalNetworkPermission = {
            permissionRequests.ensurePermissions(PermissionStates.localNetwork)
        },
    )

    val subsSheet = SubsSheetState()

    val appOrderListState = Db.actionLogDao.queryLatestUniqueAppIds().stateLoadable()
    val appVisitOrderMapState = Db.appLastVisitDao.query().map {
        it.mapIndexed { i, appId -> appId to i }.toMap()
    }.debounce(500).stateLoadable()

    val ruleGroupState = RuleGroupState(this)
    val ruleControlDialog = li.gkd.app.feature.subscription.RuleControlDialogState()

    fun showRuleGroup(
        subscriptionId: Long,
        appId: String?,
        group: RawSubscription.RawGroupProps,
        pageAppId: String? = appId,
    ) {
        scope.launch(Dispatchers.Default) {
            group.cacheStr
            runMainPost {
                ruleGroupState.showGroup(
                    when (group) {
                        is RawSubscription.RawAppGroup -> RuleGroupTarget.App(
                            subsId = subscriptionId,
                            appId = appId ?: error("require appId"),
                            groupKey = group.key,
                        )

                        is RawSubscription.RawGlobalGroup -> RuleGroupTarget.Global(
                            subsId = subscriptionId,
                            groupKey = group.key,
                            pageAppId = pageAppId,
                        )
                    },
                )
            }
        }
    }

    val textDialog = TextDialogState()

    fun openUrl(url: String) {
        textDialog.showUrl(url)
    }

    val tabFlow: StateFlow<Int>
        field = MutableStateFlow(BottomNavItem.Dashboard.key)
    val pageScrollResetRequestFlow: StateFlow<PageScrollResetRequest?>
        field = MutableStateFlow(null)
    private var nextPageScrollResetRequestId = 0L
    private var lastClickTabTime = 0L
    fun handleClickTab(navItem: BottomNavItem) {
        val t = System.currentTimeMillis()
        if (navItem.key != tabFlow.value) {
            pageScrollResetRequestFlow.value = null
        }
        // double click
        if (navItem.key == tabFlow.value && t - lastClickTabTime < 500) {
            pageScrollResetRequestFlow.value = PageScrollResetRequest(
                id = ++nextPageScrollResetRequestId,
                navItem = navItem,
            )
        }
        tabFlow.value = navItem.key
        lastClickTabTime = t
    }

    fun consumePageScrollResetRequest(request: PageScrollResetRequest) {
        pageScrollResetRequestFlow.compareAndSet(request, null)
    }

    fun handleGkdUri(uri: Uri) {
        val notFoundToast = { toast(UiStrings.uri_unknown(uri)) }
        when (uri.host) {
            "page" -> when (uri.path) {
                "" -> runMainPost {
                    val tab = uri.getQueryParameter("tab")?.toIntOrNull()
                    if (tab != null && BottomNavItem.allSubObjects.any { it.key == tab }) {
                        tabFlow.value = tab
                    }
                    // When MainActivity is reused, it also needs to return to the home page.
                    backStack.subList(1, backStack.size).clear()
                }

                "/1" -> navigatePage(AdvancedPageRoute)
                "/2" -> navigatePage(SnapshotPageRoute)
                "/3", "/4" -> navigatePage(PrivilegeServiceRoute)
                else -> notFoundToast()
            }

            "invoke" -> when (uri.path) {
                "/1" -> IntentUtils.openWeChatScaner()
                else -> notFoundToast()
            }

            else -> notFoundToast()
        }
    }

    fun handleIntent(intent: Intent) = scope.launchUi {
        LogUtils.d(intent)
        val uri = intent.data?.normalizeScheme()
        val source = intent.getStringExtra(EntryActivity.activityNavSourceName)
        if (uri?.scheme == "gkd") {
            handleGkdUri(uri)
        } else if (source == OpenFileActivity::class.jvmName && uri != null) {
            if (!dialogRequests.confirm(
                    title = UiStrings.backup_import_label,
                    text = UiStrings.backup_import_confirmation,
                )
            ) {
                return@launchUi
            }
            toast(UiStrings.backup_import_progress)
            withContext(Dispatchers.IO) { BackupManager.importData(uri) }
            toast(UiStrings.import_success)
        }
    }

    val termsAcceptedFlow: StateFlow<Boolean>
        field: MutableStateFlow<Boolean> = if (tempTermsAccepted) {
            MutableStateFlow(true)
        } else {
            FileStateStore.createTextFlow(
                key = "terms_accepted",
                decode = { it == "true" },
                encode = {
                    tempTermsAccepted = it
                    it.toString()
                },
                scope = scope,
            ).apply {
                tempTermsAccepted = value
            }
        }

    private val a11yServicesFlow = useEnabledA11yServicesFlow(scope)
    val a11yServiceEnabledFlow = useA11yServiceEnabledFlow(scope, a11yServicesFlow)

    val automatorModeFlow = storeFlow.mapNew {
        AutomatorModeOption.objects.findOption(it.automatorMode)
    }

    private var updateAutomatorModeJob: Job? = null

    private fun applyAutomatorMode(option: AutomatorModeOption) {
        AppStore.updateAutomatorMode(option.value)
        A11yService.instance?.shutdown()
        uiAutomationFlow.value?.shutdown()
    }

    fun updateAutomatorMode(option: AutomatorModeOption) {
        updateAutomatorModeJob?.cancel()
        if (automatorModeFlow.value == option) return
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
                toast(UiStrings.automation_state_check_failed(e.message))
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
        // preload
        AppInfoRepository.appIconMapFlow.value
        scope.launchLogged(Dispatchers.IO) {
            // Clear cache every time the app enters
            FolderUtils.clearCache()
        }

        if (termsAcceptedFlow.value && updateStatus?.canRecheck == true) {
            updateStatus.checkUpdate()
        }

        scope.launchLogged(Dispatchers.IO) {
            trimCrashDataFiles()
            val list = (FolderUtils.crashTempFolder.listFiles() ?: emptyArray()).mapNotNull {
                try {
                    json.decodeFromString<CrashData>(it.readText())
                } catch (e: Exception) {
                    LogUtils.d("Failed to parse crash log: ${it.name}", e)
                    null
                }
            }.sortedBy { -it.mtime }
            FolderUtils.crashTempFolder.deleteRecursively()
            val t = System.currentTimeMillis()
            FolderUtils.crashFolder.listFiles()?.filter {
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
