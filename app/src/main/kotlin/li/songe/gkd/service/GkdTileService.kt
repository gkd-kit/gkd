package li.songe.gkd.service

import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.META
import li.songe.gkd.a11y.launcherAppId
import li.songe.gkd.a11y.topAppIdFlow
import li.songe.gkd.accessRestrictedSettingsShowFlow
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.isActivityVisible
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.shizuku.safeGetTopCpn
import li.songe.gkd.store.blockA11yAppListFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.mapState
import li.songe.gkd.util.systemUiAppId
import li.songe.gkd.util.toast

class GkdTileService : BaseTileService() {
    override val activeFlow = A11yService.isRunning

    init {
        onTileClicked { switchA11yService() }
    }
}

private val modifyA11yMutex = Mutex()
private const val A11Y_AWAIT_START_TIME = 2000L
private const val A11Y_AWAIT_FIX_TIME = 500L

private fun modifyA11yRun(block: suspend () -> Unit) {
    appScope.launchTry(Dispatchers.IO) {
        if (modifyA11yMutex.isLocked) return@launchTry
        modifyA11yMutex.withLock { block() }
    }
}

fun switchA11yService() = modifyA11yRun {
    val newEnableService = !A11yService.isRunning.value
    if (A11yService.isRunning.value) {
        A11yService.instance?.disableSelf()
    } else {
        if (!writeSecureSettingsState.updateAndGet()) {
            writeSecureSettingsState.grantSelf?.invoke()
            if (!writeSecureSettingsState.updateAndGet()) {
                toast("请先授予「写入安全设置权限」")
                return@modifyA11yRun
            }
        }
        val names = app.getSecureA11yServices()
        app.putSecureInt(Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        if (names.contains(A11yService.a11yClsName)) { // 当前无障碍异常, 重启服务
            names.remove(A11yService.a11yClsName)
            app.putSecureA11yServices(names)
            delay(A11Y_AWAIT_FIX_TIME)
        }
        names.add(A11yService.a11yClsName)
        app.putSecureA11yServices(names)
        delay(A11Y_AWAIT_START_TIME)
        // https://github.com/orgs/gkd-kit/discussions/799
        if (!A11yService.isRunning.value) {
            toast("开启无障碍失败")
            accessRestrictedSettingsShowFlow.value = true
            return@modifyA11yRun
        }
    }
    storeFlow.update { it.copy(enableService = newEnableService) }
}

fun fixRestartService() = modifyA11yRun {
    if (!A11yService.isRunning.value && storeFlow.value.enableService && writeSecureSettingsState.updateAndGet()) {
        if (storeFlow.value.enableBlockA11yAppList) {
            val topAppId = if (isActivityVisible() || app.justStarted) {
                META.appId
            } else {
                safeGetTopCpn()?.packageName
            }
            if (topAppId != null && topAppId in blockA11yAppListFlow.value) {
                return@modifyA11yRun
            }
        }
        val names = app.getSecureA11yServices()
        val a11yBroken = names.contains(A11yService.a11yClsName)
        if (a11yBroken) {
            // 无障碍出现故障, 重启服务
            names.remove(A11yService.a11yClsName)
            app.putSecureA11yServices(names)
            // 必须等待一段时间, 否则概率不会触发系统重启无障碍
            delay(A11Y_AWAIT_FIX_TIME)
        }
        names.add(A11yService.a11yClsName)
        app.putSecureA11yServices(names)
        delay(A11Y_AWAIT_START_TIME)
        if (!A11yService.isRunning.value) {
            toast("重启无障碍失败")
            accessRestrictedSettingsShowFlow.value = true
        }
    }
}

private fun forcedUpdateA11yService(disabled: Boolean) = modifyA11yRun {
    if (!storeFlow.value.enableService) {
        return@modifyA11yRun
    }
    if (!storeFlow.value.enableBlockA11yAppList) {
        return@modifyA11yRun
    }
    if (!writeSecureSettingsState.updateAndGet()) {
        return@modifyA11yRun
    }
    val names = app.getSecureA11yServices()
    val hasA11y = names.contains(A11yService.a11yClsName)
    if (disabled == !hasA11y) {
        return@modifyA11yRun
    }
    if (disabled) {
        A11yService.instance?.apply {
            willDestroyByBlock = true
            disableSelf()
        }
    } else {
        names.add(A11yService.a11yClsName)
        app.putSecureA11yServices(names)
    }
}

private const val A11Y_WHITE_APP_AWAIT_TIME = 3000L

val a11yPartDisabledFlow by lazy {
    topAppIdFlow.mapState(appScope) {
        blockA11yAppListFlow.value.contains(it)
    }
}


fun initA11yWhiteAppList() {
    val actualFlow = a11yPartDisabledFlow.drop(1)
    appScope.launch(Dispatchers.Main) {
        actualFlow.collect { disabled ->
            if (!disabled) {
                val appId = topAppIdFlow.value
                if (appId == launcherAppId || appId == systemUiAppId) {
                    // 检测最近任务界面，开启或关闭无障碍会造成卡顿
                    appScope.launch {
                        delay(A11Y_WHITE_APP_AWAIT_TIME)
                        if (appId == topAppIdFlow.value) {
                            forcedUpdateA11yService(false)
                        }
                    }
                } else {
                    forcedUpdateA11yService(false)
                }
            }
        }
    }
    appScope.launch(Dispatchers.Main) {
        actualFlow.debounce(A11Y_WHITE_APP_AWAIT_TIME).collect { disabled ->
            if (disabled) {
                forcedUpdateA11yService(true)
            }
        }
    }
}
