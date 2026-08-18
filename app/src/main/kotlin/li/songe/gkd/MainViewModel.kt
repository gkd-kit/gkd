package li.songe.gkd

import android.content.Intent
import android.net.Uri
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.a11y.useA11yServiceEnabledFlow
import li.songe.gkd.a11y.useEnabledA11yServicesFlow
import li.songe.gkd.data.CrashData
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.db.DbSet
import li.songe.gkd.entry.EntryActivity
import li.songe.gkd.entry.OpenFileActivity
import li.songe.gkd.priv.AutomationService
import li.songe.gkd.priv.privilegeContextFlow
import li.songe.gkd.priv.uiAutomationFlow
import li.songe.gkd.permission.PermissionRequests
import li.songe.gkd.service.A11yService
import li.songe.gkd.store.createTextFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.AdvancedPageRoute
import li.songe.gkd.ui.CrashReportRoute
import li.songe.gkd.ui.PrivilegeServiceRoute
import li.songe.gkd.ui.SnapshotPageRoute
import li.songe.gkd.ui.WebViewRoute
import li.songe.gkd.ui.component.DialogRequests
import li.songe.gkd.ui.component.GithubUploadState
import li.songe.gkd.ui.component.RuleGroupState
import li.songe.gkd.ui.component.ShareLogState
import li.songe.gkd.ui.component.ShowGroupState
import li.songe.gkd.ui.component.SubsLinkDialogState
import li.songe.gkd.ui.component.SubsSheetState
import li.songe.gkd.ui.component.TextDialogState
import li.songe.gkd.ui.home.BottomNavItem
import li.songe.gkd.ui.home.HomeRoute
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.ActivityResultRequests
import li.songe.gkd.util.AutomatorModeOption
import li.songe.gkd.util.BackupUtils
import li.songe.gkd.util.DefaultSimpleLifeImpl
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.OnSimpleLife
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.ThrottleTimer
import li.songe.gkd.util.UpdateStatus
import li.songe.gkd.util.appIconMapFlow
import li.songe.gkd.util.clearCache
import li.songe.gkd.util.crashFolder
import li.songe.gkd.util.crashTempFolder
import li.songe.gkd.util.findOption
import li.songe.gkd.util.json
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.openWeChatScaner
import li.songe.gkd.util.runMainPost
import li.songe.gkd.util.toast
import li.songe.loc.Loc
import java.nio.file.Files
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration.Companion.days

data class PageScrollResetRequest(
    val id: Long,
    val navItem: BottomNavItem,
)

class MainViewModel : BaseViewModel(), OnSimpleLife by DefaultSimpleLifeImpl() {
    companion object {
        private var tempTermsAccepted = false
    }

    init {
        LogUtils.d("MainViewModel:init")
        addCloseable {
            LogUtils.d("MainViewModel:close")
        }
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

    override val scope get() = super.scope

    val activityResults = ActivityResultRequests()
    val permissionRequests = PermissionRequests {
        navigatePage(PrivilegeServiceRoute)
    }

    val backStack: NavBackStack<NavKey> = NavBackStack(HomeRoute)
    val topRoute get() = backStack.last()

    private val backThrottleTimer = ThrottleTimer()

    fun popPage(@Loc loc: String = "") = runMainPost {
        if (backThrottleTimer.expired() && backStack.size > 1) {
            val old = backStack.last()
            backStack.removeAt(backStack.lastIndex)
            LogUtils.d("popPage", "$old -> ${backStack.last()}", loc = loc)
        }
    }

    fun navigatePage(
        navKey: NavKey,
        replaced: Boolean = false,
        @Loc loc: String = "",
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
    )

    val subsSheet = SubsSheetState()

    val appOrderListState = DbSet.actionLogDao.queryLatestUniqueAppIds().stateLoadable()
    val appVisitOrderMapState = DbSet.appVisitLogDao.query().map {
        it.mapIndexed { i, appId -> appId to i }.toMap()
    }.debounce(500).stateLoadable()

    val ruleGroupState = RuleGroupState(this)

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
                    ShowGroupState(
                        subsId = subscriptionId,
                        appId = if (group is RawSubscription.RawAppGroup) appId else null,
                        groupKey = group.key,
                        pageAppId = pageAppId,
                    ),
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
        val notFoundToast = { toast("未知URI\n${uri}") }
        when (uri.host) {
            "page" -> when (uri.path) {
                "" -> {
                    val tab = uri.getQueryParameter("tab")?.toIntOrNull()
                    if (tab != null && BottomNavItem.allSubObjects.any { it.key == tab }) {
                        tabFlow.value = tab
                    }
                }

                "/1" -> navigatePage(AdvancedPageRoute)
                "/2" -> navigatePage(SnapshotPageRoute)
                "/3", "/4" -> navigatePage(PrivilegeServiceRoute)
                else -> notFoundToast()
            }

            "invoke" -> when (uri.path) {
                "/1" -> openWeChatScaner()
                else -> notFoundToast()
            }

            else -> notFoundToast()
        }
    }

    fun handleIntent(intent: Intent) = scope.launchTry {
        LogUtils.d(intent)
        val uri = intent.data?.normalizeScheme()
        val source = intent.getStringExtra(EntryActivity.activityNavSourceName)
        if (uri?.scheme == "gkd") {
            handleGkdUri(uri)
        } else if (source == OpenFileActivity::class.jvmName && uri != null) {
            withContext(Dispatchers.IO) { BackupUtils.importBackUpData(uri) }
        }
    }

    val termsAcceptedFlow: StateFlow<Boolean>
        field: MutableStateFlow<Boolean> = if (tempTermsAccepted) {
            MutableStateFlow(true)
        } else {
            createTextFlow(
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

    private val a11yServicesFlow = useEnabledA11yServicesFlow()
    val a11yServiceEnabledFlow = useA11yServiceEnabledFlow(a11yServicesFlow)

    val automatorModeFlow = storeFlow.mapNew {
        AutomatorModeOption.objects.findOption(it.automatorMode)
    }

    private var updateAutomatorModeJob: Job? = null

    private fun applyAutomatorMode(option: AutomatorModeOption) {
        storeFlow.update { it.copy(automatorMode = option.value, enableAutomator = false) }
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
                toast("自动化状态检测失败：${e.message}")
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
        appIconMapFlow.value
        scope.launchTry(Dispatchers.IO) {
            // 每次进入删除缓存
            clearCache()
        }

        if (termsAcceptedFlow.value && updateStatus?.canRecheck == true) {
            updateStatus.checkUpdate()
        }

        scope.launchTry(Dispatchers.IO) {
            val list = (crashTempFolder.listFiles() ?: emptyArray()).mapNotNull {
                try {
                    json.decodeFromString<CrashData>(it.readText())
                } catch (e: Exception) {
                    LogUtils.d("解析崩溃日志失败: ${it.name}", e)
                    null
                }
            }.sortedBy { -it.mtime }
            crashTempFolder.deleteRecursively()
            val t = System.currentTimeMillis()
            crashFolder.listFiles()?.filter {
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

        // for OnSimpleLife
        onCreated()
        addCloseable { onDestroyed() }
    }
}
