package li.gkd.app.service

import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import li.gkd.app.META
import li.gkd.app.a11y.currentTopActivity
import li.gkd.app.a11y.systemRecentCn
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.lifecycle.MainActivityVisibility
import li.gkd.app.priv.AutomationService
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.resources.Res
import li.gkd.app.resources.a11y_enable_failed
import li.gkd.app.resources.a11y_restart_failed
import li.gkd.app.resources.secure_settings_permission_required
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.ui.app.showAccessRestrictedSettingsDialog
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.ThreadUtils
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.mapState
import li.songe.codeorigin.CallSite
import kotlin.time.Duration.Companion.milliseconds

class GkdTileService : BaseTileService() {
    override val activeFlow = combine(A11yService.isRunning, uiAutomationFlow) { a11y, automator ->
        a11y || automator != null
    }

    override fun onTileClick() = switchAutomatorService()
}

private val modifyA11yMutex = Mutex()
private const val A11Y_AWAIT_START_TIME = 2000L
private const val A11Y_AWAIT_FIX_TIME = 1000L

private fun modifyA11yRun(
    @CallSite loc: String = "",
    block: suspend () -> Unit,
) {
    appScope.launchUi(Dispatchers.IO, loc = loc) {
        if (!modifyA11yMutex.tryLock()) return@launchUi
        try {
            block()
        } finally {
            modifyA11yMutex.unlock()
        }
    }
}

private suspend fun switchA11yService() {
    if (A11yService.isRunning.value) {
        A11yService.instance?.disableSelf()
    } else {
        if (!PermissionStates.writeSecureSettings.updateAndGet()) {
            if (!PermissionStates.writeSecureSettings.value) {
                ToastUtils.show(Res.string.secure_settings_permission_required.getSync())
                return
            }
        }
        val names = app.getSecureA11yServices()
        app.putSecureInt(Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        if (names.contains(A11yService.a11yCn)) { // 当前无障碍异常, 重启服务
            names.remove(A11yService.a11yCn)
            app.putSecureA11yServices(names)
            delay(A11Y_AWAIT_FIX_TIME.milliseconds)
        }
        names.add(A11yService.a11yCn)
        app.putSecureA11yServices(names)
        delay(A11Y_AWAIT_START_TIME.milliseconds)
        // https://github.com/orgs/gkd-kit/discussions/799
        if (!A11yService.isRunning.value) {
            ToastUtils.show(Res.string.a11y_enable_failed.getSync())
            showAccessRestrictedSettingsDialog()
            return
        }
    }
}

private fun switchAutomationService() {
    val newEnabled = uiAutomationFlow.value == null
    uiAutomationFlow.value?.shutdown()
    if (newEnabled && privilegeContextFlow.value != null) {
        AutomationService.tryConnect()
    }
}

fun switchAutomatorService(@CallSite loc: String = "") = modifyA11yRun(loc = loc) {
    if (currentAppUseA11y) {
        switchA11yService()
    } else {
        switchAutomationService()
    }
}

private fun skipBlockApp(): Boolean {
    if (settings.value.enableBlockA11yAppList) {
        val topAppId = if (MainActivityVisibility.isVisible || app.justStarted) {
            META.appId
        } else {
            privilegeContextFlow.value?.run { topCpn()?.packageName }
        }
        if (topAppId != null && topAppId in SettingsRepository.actualBlockA11yAppList) {
            return true
        }
    }
    return false
}

private suspend fun fixA11yService() {
    if (!A11yService.isRunning.value && PermissionStates.writeSecureSettings.updateAndGet()) {
        if (skipBlockApp()) return
        val names = app.getSecureA11yServices()
        val a11yBroken = names.contains(A11yService.a11yCn)
        if (a11yBroken) {
            // 无障碍出现故障, 重启服务
            names.remove(A11yService.a11yCn)
            app.putSecureA11yServices(names)
            // 必须等待一段时间, 否则概率不会触发系统重启无障碍
            delay(A11Y_AWAIT_FIX_TIME.milliseconds)
            if (!currentAppUseA11y) return
        }
        names.add(A11yService.a11yCn)
        app.putSecureA11yServices(names)
        delay(A11Y_AWAIT_START_TIME.milliseconds)
        if (currentAppUseA11y && !A11yService.isRunning.value) {
            ToastUtils.show(Res.string.a11y_restart_failed.getSync())
            showAccessRestrictedSettingsDialog()
        }
    }
}

private fun fixAutomationService() {
    if (uiAutomationFlow.value == null && privilegeContextFlow.value != null) {
        if (skipBlockApp()) return
        if (currentAppUseA11y) return
        AutomationService.tryConnect(true)
    }
}

fun fixRestartAutomatorService(@CallSite loc: String = "") = modifyA11yRun(loc = loc) {
    if (settings.value.enableAutomator) {
        if (currentAppUseA11y) {
            fixA11yService()
        } else {
            fixAutomationService()
        }
    }
}

val currentAppUseA11y
    get() = settings.value.useA11y || topAppIdFlow.value in SettingsRepository.actualA11yScopeAppList

val currentAppBlocked
    get() = settings.value.enableBlockA11yAppList && topAppIdFlow.value in SettingsRepository.actualBlockA11yAppList

private fun innerForcedUpdateA11yService(disabled: Boolean) {
    if (!settings.value.enableAutomator) {
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
        if (!PermissionStates.writeSecureSettings.stateFlow.value) {
            return
        }
        val names = app.getSecureA11yServices()
        names.add(A11yService.a11yCn)
        app.putSecureA11yServices(names)
    } else {
        AutomationService.tryConnect(true)
    }
}

private fun forcedUpdateA11yService(
    disabled: Boolean,
    @CallSite loc: String = "",
) = modifyA11yRun(loc = loc) {
    innerForcedUpdateA11yService(disabled)
}

const val A11Y_WHITE_APP_AWAIT_TIME = 3000L

@Volatile
private var lastAppIdChangeTime = 0L
val topAppIdFlow: StateFlow<String>
    field = MutableStateFlow("")
val a11yPartDisabledFlow by lazy {
    topAppIdFlow.mapState(appScope) {
        SettingsRepository.actualBlockA11yAppList.contains(it)
    }
}

fun updateTopTaskAppId(value: String) {
    if (settings.value.enableBlockA11yAppList || SettingsRepository.actualA11yScopeAppList.isNotEmpty()) {
        topAppIdFlow.value = value
    }
}

fun initA11yWhiteAppList() {
    val actualFlow = topAppIdFlow.drop(1)
    appScope.launch(Dispatchers.Main) {
        actualFlow.collect {
            lastAppIdChangeTime = System.currentTimeMillis()
            if (!currentAppBlocked) {
                if (currentTopActivity.sameAs(
                        systemRecentCn.packageName,
                        systemRecentCn.className
                    ) && currentAppUseA11y
                ) {
                    // 切换无障碍会造成卡顿，在最近任务界面时，延迟这个卡顿
                    val tempTime = lastAppIdChangeTime
                    ThreadUtils.runMainOrPost(A11Y_WHITE_APP_AWAIT_TIME) {
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
        actualFlow.debounce(A11Y_WHITE_APP_AWAIT_TIME.milliseconds).collect {
            if (currentAppBlocked) {
                forcedUpdateA11yService(true)
            }
        }
    }
}
