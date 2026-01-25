package li.songe.gkd.service

import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.META
import li.songe.gkd.a11y.systemRecentCn
import li.songe.gkd.a11y.topActivityFlow
import li.songe.gkd.accessRestrictedSettingsShowFlow
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.isActivityVisible
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.shizuku.AutomationService
import li.songe.gkd.shizuku.shizukuContextFlow
import li.songe.gkd.shizuku.uiAutomationFlow
import li.songe.gkd.store.actualA11yScopeAppList
import li.songe.gkd.store.actualBlockA11yAppList
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.mapState
import li.songe.gkd.util.runMainPost
import li.songe.gkd.util.toast

class GkdTileService : BaseTileService() {
    override val activeFlow = combine(A11yService.isRunning, uiAutomationFlow) { a11y, automator ->
        a11y || automator != null
    }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        onTileClicked { switchAutomatorService() }
    }
}

private val modifyA11yMutex = Mutex()
private const val A11Y_AWAIT_START_TIME = 2000L
private const val A11Y_AWAIT_FIX_TIME = 1000L

private fun modifyA11yRun(block: suspend () -> Unit) {
    if (modifyA11yMutex.isLocked) return
    appScope.launchTry(Dispatchers.IO) {
        if (modifyA11yMutex.isLocked) return@launchTry
        modifyA11yMutex.withLock { block() }
    }
}

private suspend fun switchA11yService() {
    if (A11yService.isRunning.value) {
        A11yService.instance?.disableSelf()
    } else {
        if (!writeSecureSettingsState.updateAndGet()) {
            if (!writeSecureSettingsState.value) {
                toast("请先授予「写入安全设置权限」")
                return
            }
        }
        val names = app.getSecureA11yServices()
        app.putSecureInt(Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        if (names.contains(A11yService.a11yCn)) { // 当前无障碍异常, 重启服务
            names.remove(A11yService.a11yCn)
            app.putSecureA11yServices(names)
            delay(A11Y_AWAIT_FIX_TIME)
        }
        names.add(A11yService.a11yCn)
        app.putSecureA11yServices(names)
        delay(A11Y_AWAIT_START_TIME)
        // https://github.com/orgs/gkd-kit/discussions/799
        if (!A11yService.isRunning.value) {
            toast("开启无障碍失败")
            accessRestrictedSettingsShowFlow.value = true
            return
        }
    }
}

private fun switchAutomationService() {
    val newEnabled = uiAutomationFlow.value == null
    uiAutomationFlow.value?.shutdown()
    if (newEnabled && shizukuContextFlow.value.ok) {
        AutomationService.tryConnect()
    }
}

fun switchAutomatorService() = modifyA11yRun {
    if (currentAppUseA11y) {
        switchA11yService()
    } else {
        switchAutomationService()
    }
}

private fun skipBlockApp(): Boolean {
    if (storeFlow.value.enableBlockA11yAppList) {
        val topAppId = if (isActivityVisible || app.justStarted) {
            META.appId
        } else {
            shizukuContextFlow.value.topCpn()?.packageName
        }
        if (topAppId != null && topAppId in actualBlockA11yAppList) {
            return true
        }
    }
    return false
}

private suspend fun fixA11yService() {
    if (!A11yService.isRunning.value && writeSecureSettingsState.updateAndGet()) {
        if (skipBlockApp()) return
        val names = app.getSecureA11yServices()
        val a11yBroken = names.contains(A11yService.a11yCn)
        if (a11yBroken) {
            // 无障碍出现故障, 重启服务
            names.remove(A11yService.a11yCn)
            app.putSecureA11yServices(names)
            // 必须等待一段时间, 否则概率不会触发系统重启无障碍
            delay(A11Y_AWAIT_FIX_TIME)
            if (!currentAppUseA11y) return
        }
        names.add(A11yService.a11yCn)
        app.putSecureA11yServices(names)
        delay(A11Y_AWAIT_START_TIME)
        if (currentAppUseA11y && !A11yService.isRunning.value) {
            toast("重启无障碍失败")
            accessRestrictedSettingsShowFlow.value = true
        }
    }
}

private fun fixAutomationService() {
    if (uiAutomationFlow.value == null && shizukuContextFlow.value.ok) {
        if (skipBlockApp()) return
        if (currentAppUseA11y) return
        AutomationService.tryConnect(true)
    }
}

fun fixRestartAutomatorService() = modifyA11yRun {
    if (storeFlow.value.enableAutomator) {
        if (currentAppUseA11y) {
            fixA11yService()
        } else {
            fixAutomationService()
        }
    }
}

val currentAppUseA11y
    get() = storeFlow.value.useA11y || topAppIdFlow.value in actualA11yScopeAppList

val currentAppBlocked
    get() = storeFlow.value.enableBlockA11yAppList && topAppIdFlow.value in actualBlockA11yAppList

private fun innerForcedUpdateA11yService(disabled: Boolean) {
    if (!storeFlow.value.enableAutomator) {
        return
    }
    if (disabled) {
        A11yService.instance?.shutdown(true)
        uiAutomationFlow.value?.shutdown(true)
        return
    }
    if (currentAppUseA11y) {
        if (A11yService.isRunning.value) {
            return
        }
        if (!writeSecureSettingsState.stateFlow.value) {
            return
        }
        val names = app.getSecureA11yServices()
        names.add(A11yService.a11yCn)
        app.putSecureA11yServices(names)
    } else {
        AutomationService.tryConnect(true)
    }
}

private fun forcedUpdateA11yService(disabled: Boolean) = modifyA11yRun {
    innerForcedUpdateA11yService(disabled)
}

const val A11Y_WHITE_APP_AWAIT_TIME = 3000L

@Volatile
private var lastAppIdChangeTime = 0L
val topAppIdFlow = MutableStateFlow("")
val a11yPartDisabledFlow by lazy {
    topAppIdFlow.mapState(appScope) {
        actualBlockA11yAppList.contains(it)
    }
}

fun updateTopTaskAppId(value: String) {
    if (storeFlow.value.enableBlockA11yAppList || actualA11yScopeAppList.isNotEmpty()) {
        topAppIdFlow.value = value
    }
}

fun initA11yWhiteAppList() {
    val actualFlow = topAppIdFlow.drop(1)
    appScope.launch(Dispatchers.Main) {
        actualFlow.collect {
            lastAppIdChangeTime = System.currentTimeMillis()
            if (!currentAppBlocked) {
                if (topActivityFlow.value.sameAs(systemRecentCn) && currentAppUseA11y) {
                    // 切换无障碍会造成卡顿，在最近任务界面时，延迟这个卡顿
                    val tempTime = lastAppIdChangeTime
                    runMainPost(A11Y_WHITE_APP_AWAIT_TIME) {
                        if (tempTime == lastAppIdChangeTime) {
                            forcedUpdateA11yService(false)
                        }
                    }
                } else {
                    // 切换自动化不会卡顿，直接启动
                    forcedUpdateA11yService(false)
                }
            }
        }
    }
    appScope.launch(Dispatchers.Main) {
        actualFlow.debounce(A11Y_WHITE_APP_AWAIT_TIME).collect {
            if (currentAppBlocked) {
                forcedUpdateA11yService(true)
            }
        }
    }
}
