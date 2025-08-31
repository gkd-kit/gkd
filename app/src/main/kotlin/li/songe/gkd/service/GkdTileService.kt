package li.songe.gkd.service

import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.accessRestrictedSettingsShowFlow
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.toast

class GkdTileService : BaseTileService() {
    override val activeFlow = A11yService.isRunning

    init {
        onStartListened { fixRestartService() }
        onTileClicked { switchA11yService() }
    }
}

private val modifyA11yMutex by lazy { Mutex() }
private const val A11Y_AWAIT_START_TIME = 2000L
private const val A11Y_AWAIT_FIX_TIME = 500L

fun switchA11yService() = appScope.launchTry(Dispatchers.IO) {
    if (modifyA11yMutex.isLocked) return@launchTry
    modifyA11yMutex.withLock {
        val newEnableService = !A11yService.isRunning.value
        if (A11yService.isRunning.value) {
            A11yService.instance?.disableSelf()
        } else {
            if (!writeSecureSettingsState.updateAndGet()) {
                toast("请先授予「写入安全设置权限」")
                return@launchTry
            }
            val names = app.getSecureA11yServices()
            Settings.Secure.putInt(
                app.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )
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
                return@launchTry
            }
        }
        storeFlow.update { it.copy(enableService = newEnableService) }
    }
}

fun fixRestartService() = appScope.launchTry(Dispatchers.IO) {
    if (modifyA11yMutex.isLocked) return@launchTry
    modifyA11yMutex.withLock {
        // 1. 服务没有运行
        // 2. 用户配置开启了服务
        // 3. 有写入系统设置权限
        if (!A11yService.isRunning.value && storeFlow.value.enableService && writeSecureSettingsState.updateAndGet()) {
            val names = app.getSecureA11yServices()
            val a11yBroken = names.contains(A11yService.a11yClsName)
            if (a11yBroken) {
                // 无障碍出现故障, 重启服务
                names.remove(A11yService.a11yClsName)
                app.putSecureA11yServices(names)
                // 必须等待一段时间, 否则概率不会触发系统重启无障碍服务
                delay(A11Y_AWAIT_FIX_TIME)
            }
            names.add(A11yService.a11yClsName)
            app.putSecureA11yServices(names)
            delay(A11Y_AWAIT_START_TIME)
            if (!A11yService.isRunning.value) {
                toast("重启无障碍失败")
                accessRestrictedSettingsShowFlow.value = true
                return@launchTry
            }
        }
    }
}

