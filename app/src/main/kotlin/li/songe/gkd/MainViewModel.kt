package li.songe.gkd

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.generated.destinations.AdvancedPageDestination
import com.ramcosta.composedestinations.generated.destinations.SnapshotPageDestination
import com.ramcosta.composedestinations.generated.destinations.WebViewPageDestination
import com.ramcosta.composedestinations.spec.Direction
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.a11y.useA11yServiceEnabledFlow
import li.songe.gkd.a11y.useEnabledA11yServicesFlow
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.importData
import li.songe.gkd.db.DbSet
import li.songe.gkd.permission.AuthReason
import li.songe.gkd.permission.canQueryPkgState
import li.songe.gkd.permission.shizukuGrantedState
import li.songe.gkd.service.A11yService
import li.songe.gkd.shizuku.shizukuContextFlow
import li.songe.gkd.shizuku.updateBinderMutex
import li.songe.gkd.store.createTextFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AlertDialogOptions
import li.songe.gkd.ui.component.InputSubsLinkOption
import li.songe.gkd.ui.component.RuleGroupState
import li.songe.gkd.ui.component.UploadOptions
import li.songe.gkd.ui.home.BottomNavItem
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.LOCAL_SUBS_ID
import li.songe.gkd.util.OnSimpleLife
import li.songe.gkd.util.ThrottleTimer
import li.songe.gkd.util.UpdateStatus
import li.songe.gkd.util.appIconMapFlow
import li.songe.gkd.util.clearCache
import li.songe.gkd.util.client
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.openUri
import li.songe.gkd.util.openWeChatScaner
import li.songe.gkd.util.runMainPost
import li.songe.gkd.util.stopCoroutine
import li.songe.gkd.util.subsFolder
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubsMutex
import li.songe.gkd.util.updateSubscription
import rikka.shizuku.Shizuku
import kotlin.reflect.jvm.jvmName

private var tempTermsAccepted = false

class MainViewModel : BaseViewModel(), OnSimpleLife {
    companion object {
        private var _instance: MainViewModel? = null
        val instance get() = _instance!!
    }

    init {
        _instance = this
        addCloseable { _instance = null }
    }

    override val scope: CoroutineScope
        get() = viewModelScope

    private lateinit var navController: NavHostController
    fun updateNavController(navController: NavHostController) {
        this.navController = navController
    }

    private val backThrottleTimer = ThrottleTimer()
    fun popBackStack() {
        if (!backThrottleTimer.expired()) return
        @SuppressLint("RestrictedApi")
        if (navController.currentBackStack.value.size == 1) return
        runMainPost {
            navController.popBackStack()
        }
    }

    fun navigatePage(direction: Direction, builder: (NavOptionsBuilder.() -> Unit)? = null) {
        if (direction.route == navController.currentDestination?.route) {
            return
        }
        runMainPost {
            if (builder != null) {
                navController.navigate(direction.route, builder)
            } else {
                navController.navigate(direction.route)
            }
        }
    }

    fun navigateWebPage(url: String) {
        navigatePage(WebViewPageDestination(url))
    }

    val dialogFlow = MutableStateFlow<AlertDialogOptions?>(null)
    val authReasonFlow = MutableStateFlow<AuthReason?>(null)

    val updateStatus = if (META.updateEnabled) UpdateStatus(viewModelScope) else null

    val shizukuErrorFlow = MutableStateFlow<Throwable?>(null)

    val uploadOptions = UploadOptions(this)

    val showEditCookieDlgFlow = MutableStateFlow(false)

    val inputSubsLinkOption = InputSubsLinkOption()

    val sheetSubsIdFlow = MutableStateFlow<Long?>(null)

    val showShareDataIdsFlow = MutableStateFlow<Set<Long>?>(null)

    val appOrderListFlow = DbSet.actionLogDao.queryLatestUniqueAppIds().stateInit(emptyList())
    val appVisitOrderMapFlow = DbSet.appVisitLogDao.query().map {
        it.mapIndexed { i, appId -> appId to i }.toMap()
    }.debounce(500).stateInit(emptyMap())

    fun addOrModifySubs(
        url: String,
        oldItem: SubsItem? = null,
    ) = viewModelScope.launchTry(Dispatchers.IO) {
        if (updateSubsMutex.mutex.isLocked) return@launchTry
        updateSubsMutex.withStateLock {
            val subItems = subsItemsFlow.value
            val text = try {
                client.get(url).bodyAsText()
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                toast("下载订阅文件失败\n${e.message}".trimEnd())
                return@launchTry
            }
            val newSubsRaw = try {
                RawSubscription.parse(text)
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                toast("解析订阅文件失败\n${e.message}".trimEnd())
                return@launchTry
            }
            if (oldItem == null) {
                if (subItems.any { it.id == newSubsRaw.id }) {
                    toast("订阅已存在")
                    return@launchTry
                }
            } else {
                if (oldItem.id != newSubsRaw.id) {
                    toast("订阅id不对应")
                    return@launchTry
                }
            }
            if (newSubsRaw.id < 0) {
                toast("订阅id不可为${newSubsRaw.id}\n负数id为内部使用")
                return@launchTry
            }
            val newItem = oldItem?.copy(updateUrl = url) ?: SubsItem(
                id = newSubsRaw.id,
                updateUrl = url,
                order = if (subItems.isEmpty()) 1 else (subItems.maxBy { it.order }.order + 1)
            )
            updateSubscription(newSubsRaw)
            if (oldItem == null) {
                DbSet.subsItemDao.insert(newItem)
                toast("成功添加订阅")
            } else {
                DbSet.subsItemDao.update(newItem)
                toast("成功修改订阅")
            }
        }
    }

    val ruleGroupState = RuleGroupState(this)

    val textFlow = MutableStateFlow<String?>(null)
    fun openUrl(url: String) {
        if (URLUtil.isNetworkUrl(url)) {
            textFlow.value = url
        } else {
            openUri(url)
        }
    }

    val appListKeyFlow = MutableStateFlow(0)
    val tabFlow = MutableStateFlow(BottomNavItem.Control.key)
    private var lastClickTabTime = 0L
    fun updateTab(navItem: BottomNavItem) {
        if (navItem == BottomNavItem.AppList && navItem.key == tabFlow.value) {
            // double click
            if (System.currentTimeMillis() - lastClickTabTime < 500) {
                appListKeyFlow.update { it + 1 }
            }
        }
        tabFlow.value = navItem.key
        lastClickTabTime = System.currentTimeMillis()
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

                "/1" -> navigatePage(AdvancedPageDestination)
                "/2" -> navigatePage(SnapshotPageDestination())
                else -> notFoundToast()
            }

            "invoke" -> when (uri.path) {
                "/1" -> openWeChatScaner()
                else -> notFoundToast()
            }

            else -> notFoundToast()
        }
    }

    fun handleIntent(intent: Intent) = viewModelScope.launchTry {
        LogUtils.d("handleIntent", intent)
        val uri = intent.data?.normalizeScheme()
        val source = intent.getStringExtra(activityNavSourceName)
        if (uri?.scheme == "gkd") {
            handleGkdUri(uri)
        } else if (source == OpenFileActivity::class.jvmName && uri != null) {
            toast("加载导入中...")
            tabFlow.value = BottomNavItem.SubsManage.key
            withContext(Dispatchers.IO) { importData(uri) }
        }
    }

    val termsAcceptedFlow by lazy {
        if (tempTermsAccepted) {
            MutableStateFlow(true)
        } else {
            createTextFlow(
                key = "terms_accepted",
                decode = { it == "true" },
                encode = {
                    tempTermsAccepted = it
                    it.toString()
                },
                scope = viewModelScope,
            ).apply {
                tempTermsAccepted = value
            }
        }
    }

    val githubCookieFlow by lazy {
        createTextFlow(
            key = "github_cookie",
            decode = { it ?: "" },
            encode = { it },
            private = true,
            scope = viewModelScope,
        )
    }

    fun switchEnableShizuku(value: Boolean) {
        if (updateBinderMutex.mutex.isLocked) {
            toast("正在连接中，请稍后")
            return
        }
        storeFlow.update { s -> s.copy(enableShizuku = value) }
    }

    fun requestShizuku() {
        if (shizukuContextFlow.value.ok) return
        if (updateBinderMutex.mutex.isLocked) {
            toast("正在连接中，请稍后")
            return
        }
        try {
            Shizuku.requestPermission(Activity.RESULT_OK)
        } catch (e: Throwable) {
            shizukuErrorFlow.value = e
        }
    }

    suspend fun guardShizukuContext() {
        if (shizukuContextFlow.value.ok) return
        if (!storeFlow.value.enableShizuku) {
            storeFlow.update { it.copy(enableShizuku = true) }
        }
        if (!shizukuGrantedState.updateAndGet()) {
            requestShizuku()
            stopCoroutine()
        }
        if (shizukuContextFlow.value.ok) return
        stopCoroutine()
    }

    private val a11yServicesFlow = useEnabledA11yServicesFlow()
    val a11yServiceEnabledFlow = useA11yServiceEnabledFlow(a11yServicesFlow)
    val hasOtherA11yFlow = a11yServicesFlow.mapNew { list ->
        list.any { it != A11yService.a11yCn }
    }

    init {
        // preload
        appIconMapFlow.value
        viewModelScope.launchTry(Dispatchers.IO) {
            val subsItems = DbSet.subsItemDao.queryAll()
            if (!subsItems.any { s -> s.id == LOCAL_SUBS_ID }) {
                if (!subsFolder.resolve("${LOCAL_SUBS_ID}.json").exists()) {
                    updateSubscription(
                        RawSubscription(
                            id = LOCAL_SUBS_ID,
                            name = "本地订阅",
                            version = 0
                        )
                    )
                }
                DbSet.subsItemDao.insert(
                    SubsItem(
                        id = LOCAL_SUBS_ID,
                        order = subsItems.minByOrNull { it.order }?.order ?: 0,
                    )
                )
            }
        }

        viewModelScope.launchTry(Dispatchers.IO) {
            // 每次进入删除缓存
            clearCache()
        }

        if (updateStatus != null && termsAcceptedFlow.value) {
            updateStatus.checkUpdate()
        }

        viewModelScope.launch(Dispatchers.IO) {
            // preload
            githubCookieFlow.value
        }

        canQueryPkgState.stateFlow.launchOnChange {
            appListKeyFlow.update { it + 1 }
        }

        // for OnSimpleLife
        onCreated()
        addCloseable { onDestroyed() }
    }
}
