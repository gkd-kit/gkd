package li.songe.gkd.service

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.app
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.util.OnChangeListen
import li.songe.gkd.util.OnDestroy
import li.songe.gkd.util.OnTileClick
import li.songe.gkd.util.componentName
import li.songe.gkd.util.lastRestartA11yServiceTimeFlow
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.toast

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

fun switchA11yService(): Boolean {
    if (!writeSecureSettingsState.updateAndGet()) {
        toast("请先授予[写入安全设置]权限")
        return false
    }
    val names = getServiceNames()
    storeFlow.update { it.copy(enableService = !A11yService.isRunning.value) }
    if (A11yService.isRunning.value) {
        names.remove(a11yClsName)
        updateServiceNames(names)
        toast("关闭无障碍")
    } else {
        enableA11yService()
        if (names.contains(a11yClsName)) { // 当前无障碍异常, 重启服务
            names.remove(a11yClsName)
            updateServiceNames(names)
        }
        names.add(a11yClsName)
        updateServiceNames(names)
        toast("开启无障碍")
    }
    return true
}

fun fixRestartService(): Boolean {
    // 1. 服务没有运行
    // 2. 用户配置开启了服务
    // 3. 有写入系统设置权限
    if (!A11yService.isRunning.value && storeFlow.value.enableService && writeSecureSettingsState.updateAndGet()) {
        val t = System.currentTimeMillis()
        if (t - lastRestartA11yServiceTimeFlow.value < 10_000) return false
        lastRestartA11yServiceTimeFlow.value = t
        val names = getServiceNames()
        val a11yBroken = names.contains(a11yClsName)
        if (a11yBroken) {
            // 无障碍出现故障, 重启服务
            names.remove(a11yClsName)
            updateServiceNames(names)
        }
        names.add(a11yClsName)
        updateServiceNames(names)
        toast("重启无障碍")
        return true
    }
    return false
}

val a11yClsName by lazy { A11yService::class.componentName.flattenToShortString() }
