package li.songe.gkd

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.TileService
import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.generated.destinations.AdvancedPageDestination
import com.ramcosta.composedestinations.generated.destinations.SnapshotPageDestination
import com.ramcosta.composedestinations.spec.Direction
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.importData
import li.songe.gkd.db.DbSet
import li.songe.gkd.debug.FloatingTileService
import li.songe.gkd.debug.HttpTileService
import li.songe.gkd.debug.SnapshotTileService
import li.songe.gkd.permission.AuthReason
import li.songe.gkd.ui.component.AlertDialogOptions
import li.songe.gkd.ui.component.InputSubsLinkOption
import li.songe.gkd.ui.component.RuleGroupState
import li.songe.gkd.ui.component.UploadOptions
import li.songe.gkd.ui.home.BottomNavItem
import li.songe.gkd.ui.home.appListNav
import li.songe.gkd.ui.home.controlNav
import li.songe.gkd.ui.home.subsNav
import li.songe.gkd.util.LOCAL_SUBS_ID
import li.songe.gkd.util.UpdateStatus
import li.songe.gkd.util.checkUpdate
import li.songe.gkd.util.clearCache
import li.songe.gkd.util.client
import li.songe.gkd.util.componentName
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.openUri
import li.songe.gkd.util.openWeChatScaner
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.subsFolder
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubsMutex
import li.songe.gkd.util.updateSubscription
import java.lang.ref.WeakReference

class MainViewModel : ViewModel() {

    private var navControllerRef: WeakReference<NavHostController>? = null
    var navController: NavHostController
        get() = navControllerRef?.get() ?: error("not found navController")
        set(value) {
            navControllerRef = WeakReference(value)
        }

    val enableDarkThemeFlow = storeFlow.debounce(300).map { s -> s.enableDarkTheme }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        storeFlow.value.enableDarkTheme
    )
    val enableDynamicColorFlow = storeFlow.debounce(300).map { s -> s.enableDynamicColor }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        storeFlow.value.enableDynamicColor
    )

    val dialogFlow = MutableStateFlow<AlertDialogOptions?>(null)
    val authReasonFlow = MutableStateFlow<AuthReason?>(null)

    val updateStatus = UpdateStatus()

    val shizukuErrorFlow = MutableStateFlow(false)

    val uploadOptions = UploadOptions(this)

    val showEditCookieDlgFlow = MutableStateFlow(false)

    val inputSubsLinkOption = InputSubsLinkOption()

    val sheetSubsIdFlow = MutableStateFlow<Long?>(null)

    val showShareDataIdsFlow = MutableStateFlow<Set<Long>?>(null)

    fun addOrModifySubs(
        url: String,
        oldItem: SubsItem? = null,
    ) = viewModelScope.launchTry(Dispatchers.IO) {
        if (updateSubsMutex.mutex.isLocked) return@launchTry
        updateSubsMutex.withLock {
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

    val urlFlow = MutableStateFlow<String?>(null)
    fun openUrl(url: String) {
        if (URLUtil.isNetworkUrl(url)) {
            urlFlow.value = url
        } else {
            openUri(url)
        }
    }

    val appListKeyFlow = MutableStateFlow(0)
    val tabFlow = MutableStateFlow(controlNav)
    private var lastClickTabTime = 0L
    fun updateTab(navItem: BottomNavItem) {
        if (navItem == appListNav && navItem == tabFlow.value) {
            // double click
            if (System.currentTimeMillis() - lastClickTabTime < 500) {
                appListKeyFlow.update { it + 1 }
            }
        }
        tabFlow.value = navItem
        lastClickTabTime = System.currentTimeMillis()
    }

    fun navigatePage(direction: Direction) {
        if (direction.route == navController.currentDestination?.route) {
            return
        }
        navController.navigate(direction.route)
    }

    fun handleGkdUri(uri: Uri) {
        val notFoundToast = { toast("未知URI\n${uri}") }
        when (uri.host) {
            "page" -> when (uri.path) {
                "" -> {}
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

    fun handleIntent(intent: Intent) = viewModelScope.launchTry(Dispatchers.Main) {
        LogUtils.d("handleIntent", intent)
        val uri = intent.data?.normalizeScheme()
        if (uri?.scheme == "gkd") {
            delay(200)
            handleGkdUri(uri)
        } else if (uri != null && intent.getStringExtra("source") == OpenFileActivity::class.qualifiedName) {
            toast("加载导入中...")
            tabFlow.value = subsNav
            withContext(Dispatchers.IO) { importData(uri) }
        } else if (intent.action == TileService.ACTION_QS_TILE_PREFERENCES) {
            val qsTileCpt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME) as ComponentName?
            } ?: return@launchTry
            delay(200)
            if (qsTileCpt == HttpTileService::class.componentName || qsTileCpt == FloatingTileService::class.componentName) {
                navigatePage(AdvancedPageDestination)
            } else if (qsTileCpt == SnapshotTileService::class.componentName) {
                navigatePage(SnapshotPageDestination)
            }
        }
    }

    init {
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

        if (META.updateEnabled && storeFlow.value.autoCheckAppUpdate) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    updateStatus.checkUpdate()
                } catch (e: Exception) {
                    e.printStackTrace()
                    LogUtils.d(e)
                }
            }
        }
    }
}
