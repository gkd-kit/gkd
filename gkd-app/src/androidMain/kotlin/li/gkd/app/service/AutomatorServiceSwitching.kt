package li.gkd.app.service

import android.provider.Settings
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.matching.input.currentTopActivity
import li.gkd.app.matching.input.systemRecentCn
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.lifecycle.MainActivityVisibility
import li.gkd.app.priv.AutomationService
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.resources.*
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.ui.app.showAccessRestrictedSettingsDialog
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.ThreadUtils
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.mapState
import li.songe.codeorigin.CallSite

private val serviceSwitchMutex = Mutex()
private const val A11Y_AWAIT_START_TIME = 2000L
private const val A11Y_AWAIT_FIX_TIME = 1000L

private fun modifyAutomatorService(
    @CallSite loc: String = "",
    block: suspend () -> Unit,
) {
    appScope.launchUi(Dispatchers.IO, loc = loc) {
        if (!serviceSwitchMutex.tryLock()) return@launchUi
        try {
            block()
        } finally {
            serviceSwitchMutex.unlock()
        }
    }
}

private suspend fun enableA11yService(restartIfEnabled: Boolean): Boolean {
    if (A11yService.isRunning.value) return false
    val names = app.getSecureA11yServices()
    app.putSecureInt(Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    if (restartIfEnabled && names.contains(A11yService.a11yCn)) {
        // 无障碍出现故障，先移除再添加以触发系统重启服务。
        names.remove(A11yService.a11yCn)
        app.putSecureA11yServices(names)
        // 必须等待一段时间，否则概率不会触发系统重启无障碍。
        delay(A11Y_AWAIT_FIX_TIME.milliseconds)
        if (!currentAppUseA11y) return false
    }
    names.add(A11yService.a11yCn)
    app.putSecureA11yServices(names)
    return true
}

private suspend fun startA11yService() {
    if (!PermissionStates.writeSecureSettings.updateAndGet()) {
        if (!PermissionStates.writeSecureSettings.value) {
            ToastUtils.show(Res.string.secure_settings_permission_required.getSync())
            return
        }
    }
    if (!enableA11yService(restartIfEnabled = true)) return
    delay(A11Y_AWAIT_START_TIME.milliseconds)
    // https://github.com/orgs/gkd-kit/discussions/799
    if (!A11yService.isRunning.value) {
        ToastUtils.show(Res.string.a11y_enable_failed.getSync())
        showAccessRestrictedSettingsDialog()
    }
}

fun switchAutomatorService(@CallSite loc: String = "") =
    modifyAutomatorService(loc = loc) {
        if (A11yService.isRunning.value || uiAutomationFlow.value != null) {
            A11yService.instance?.shutdown()
            uiAutomationFlow.value?.shutdown()
        } else if (currentAppUseA11y) {
            startA11yService()
        } else {
            AutomationService.tryConnect()
        }
    }

private fun skipBlockApp(): Boolean {
    if (settings.value.enableBlockA11yAppList) {
        val topAppId =
            if (MainActivityVisibility.isVisible || app.justStarted) {
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
        if (!enableA11yService(restartIfEnabled = true)) return
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

fun fixRestartAutomatorService(@CallSite loc: String = "") =
    modifyAutomatorService(loc = loc) {
        if (settings.value.enableAutomator) {
            if (currentAppUseA11y) {
                fixA11yService()
            } else {
                fixAutomationService()
            }
        }
    }

val currentAppUseA11y
    get() = SettingsRepository.appUsesA11y(topAppIdFlow.value)

val currentAppBlocked
    get() =
        settings.value.enableBlockA11yAppList &&
            topAppIdFlow.value in SettingsRepository.actualBlockA11yAppList

private suspend fun applyScopedService(disabled: Boolean) {
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
        enableA11yService(restartIfEnabled = false)
    } else {
        AutomationService.tryConnect(true)
    }
}

private fun updateScopedService(
    disabled: Boolean,
    @CallSite loc: String = "",
) =
    modifyAutomatorService(loc = loc) {
        applyScopedService(disabled)
    }

private const val SERVICE_SCOPE_AWAIT_TIME = 3000L

@Volatile private var lastAppIdChangeTime = 0L
val topAppIdFlow: StateFlow<String>
    field = MutableStateFlow("")
val a11yPartDisabledFlow by lazy {
    topAppIdFlow.mapState(appScope) {
        SettingsRepository.actualBlockA11yAppList.contains(it)
    }
}

fun updateTopTaskAppId(value: String) {
    topAppIdFlow.value = value
}

fun initServiceScope() {
    val actualFlow = topAppIdFlow.drop(1)
    appScope.launch(Dispatchers.Main) {
        actualFlow.collect { appId ->
            lastAppIdChangeTime = System.currentTimeMillis()
            if (!currentAppBlocked) {
                if (
                    appId == systemRecentCn.packageName &&
                        currentTopActivity.sameAs(
                            systemRecentCn.packageName,
                            systemRecentCn.className,
                        ) &&
                        currentAppUseA11y
                ) {
                    // 切换无障碍会造成卡顿，在最近任务界面时，延迟这个卡顿
                    val tempTime = lastAppIdChangeTime
                    ThreadUtils.runMainOrPost(SERVICE_SCOPE_AWAIT_TIME) {
                        if (tempTime == lastAppIdChangeTime) {
                            updateScopedService(false)
                        }
                    }
                } else {
                    // 切换自动化不会卡顿，直接启动
                    updateScopedService(false)
                }
            }
        }
    }
    appScope.launch(Dispatchers.Main) {
        actualFlow.debounce(SERVICE_SCOPE_AWAIT_TIME.milliseconds).collect {
            if (currentAppBlocked) {
                updateScopedService(true)
            }
        }
    }
}
