package li.songe.gkd.service

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.accessRestrictedSettingsShowFlow
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.OnChangeListen
import li.songe.gkd.util.OnDestroy
import li.songe.gkd.util.OnTileClick
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.toast
import li.songe.gkd.util.useLogLifecycle

class GkdTileService : TileService(), OnDestroy, OnChangeListen, OnTileClick {
    override fun onStartListening() {
        super.onStartListening()
        onStartListened()
    }

    override fun onClick() {
        super.onClick()
        onTileClicked()
    }

    override fun onStopListening() {
        super.onStopListening()
        onStopListened()
    }

    override fun onDestroy() {
        super.onDestroy()
        onDestroyed()
    }

    val scope = MainScope().also { scope ->
        onDestroyed { scope.cancel() }
    }

    private val listeningFlow = MutableStateFlow(false).also { listeningFlow ->
        onStartListened { listeningFlow.value = true }
        onStopListened { listeningFlow.value = false }
    }

    init {
        useLogLifecycle()
        scope.launch {
            combine(
                A11yService.isRunning,
                listeningFlow
            ) { v1, v2 -> v1 to v2 }.collect { (running, listening) ->
                if (listening) {
                    qsTile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    qsTile.updateTile()
                }
            }
        }
        onStartListened {
            fixRestartService()
        }
        onTileClicked {
            switchA11yService()
        }
    }
}

private fun getServiceNames(): MutableList<String> {
    val value = try {
        Settings.Secure.getString(
            app.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    } catch (_: Exception) {
        null
    } ?: ""
    if (value.isEmpty()) return mutableListOf()
    return value.split(':').toMutableList()
}

private fun updateServiceNames(names: List<String>) {
    Settings.Secure.putString(
        app.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        names.joinToString(":")
    )
}

private fun enableA11yService() {
    Settings.Secure.putInt(
        app.contentResolver,
        Settings.Secure.ACCESSIBILITY_ENABLED,
        1
    )
}

private val modifyA11yMutex by lazy { Mutex() }
private const val A11Y_AWAIT_START_TIME = 1000L
private const val A11Y_AWAIT_FIX_TIME = 500L

fun switchA11yService() = appScope.launchTry(Dispatchers.IO) {
    modifyA11yMutex.withLock {
        val newEnableService = !A11yService.isRunning.value
        if (A11yService.isRunning.value) {
            A11yService.instance?.disableSelf()
        } else {
            if (!writeSecureSettingsState.updateAndGet()) {
                toast("请先授予「写入安全设置权限」")
                return@launchTry
            }
            val names = getServiceNames()
            enableA11yService()
            if (names.contains(A11yService.a11yClsName)) { // 当前无障碍异常, 重启服务
                names.remove(A11yService.a11yClsName)
                updateServiceNames(names)
                delay(A11Y_AWAIT_FIX_TIME)
            }
            names.add(A11yService.a11yClsName)
            updateServiceNames(names)
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
            val names = getServiceNames()
            val a11yBroken = names.contains(A11yService.a11yClsName)
            if (a11yBroken) {
                // 无障碍出现故障, 重启服务
                names.remove(A11yService.a11yClsName)
                updateServiceNames(names)
                // 必须等待一段时间, 否则概率不会触发系统重启无障碍服务
                delay(A11Y_AWAIT_FIX_TIME)
            }
            names.add(A11yService.a11yClsName)
            updateServiceNames(names)
            delay(A11Y_AWAIT_START_TIME)
            if (!A11yService.isRunning.value) {
                toast("重启无障碍失败")
                accessRestrictedSettingsShowFlow.value = true
                return@launchTry
            }
        }
    }
}

