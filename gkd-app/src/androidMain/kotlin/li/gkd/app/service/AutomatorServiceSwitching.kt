package li.gkd.app.service

import android.content.ComponentName
import android.provider.Settings
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.error.BusinessError
import li.gkd.app.error.ErrorMessage
import li.gkd.app.matching.input.ForegroundIdentity
import li.gkd.app.matching.input.systemRecentCn
import li.gkd.app.matching.input.topTaskAppId
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.AutomationService
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.priv.recoverPrivilegeContext
import li.gkd.app.priv.requestPrivilegeRecovery
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.resources.*
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.store.AppStore.updateEnableAutomator
import li.gkd.app.ui.app.showAccessRestrictedSettingsDialog
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.componentName
import li.gkd.app.util.launchLogged
import li.songe.codeorigin.CallSite

private const val A11Y_AWAIT_START_TIME = 2000L
private const val A11Y_AWAIT_FIX_TIME = 1000L

private fun isA11yBound(component: ComponentName): Boolean =
    A11yService.instance?.let { it::class.componentName } == component

private suspend fun awaitA11yBinding(component: ComponentName): Boolean =
    withTimeoutOrNull(A11Y_AWAIT_START_TIME.milliseconds) {
        A11yService.instanceFlow.first { service ->
            service?.let { it::class.componentName } == component
        }
    } != null

private fun canContinueA11yRequest(component: ComponentName, automatic: Boolean): Boolean {
    val current = settings.value
    return current.useA11y &&
        (!automatic || (current.enableAutomator && !currentAppBlocked)) &&
        component == A11yService.preferredComponent
}

private class A11ySettingsWriteFailure(operation: String, cause: SecurityException? = null) :
    IllegalStateException("Accessibility settings write failed: $operation", cause), BusinessError {
    override val errorCode = "a11y_settings_write_failed"
    override val errorMessage = ErrorMessage.Resource(Res.string.a11y_enable_failed)
}

private inline fun writeA11ySetting(operation: String, write: () -> Boolean) {
    val written =
        try {
            write()
        } catch (error: SecurityException) {
            throw A11ySettingsWriteFailure(operation, error)
        }
    if (!written) throw A11ySettingsWriteFailure(operation)
}

private suspend fun enableA11yService(
    component: ComponentName,
    restartIfEnabled: Boolean,
    automatic: Boolean = false,
): Boolean {
    var names = app.getSecureA11yServices()
    val previousComponent =
        if (component == A11yService.standardComponent) A11yService.privilegedComponent
        else A11yService.standardComponent
    writeA11ySetting("enable ACCESSIBILITY_ENABLED") {
        app.putSecureInt(Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    }
    val previousService = A11yService.instance
    fun submit(operation: String) {
        val write = { app.putSecureA11yServices(names) }
        writeA11ySetting(operation) {
            previousService?.writeReplacement(write) ?: write()
        }
    }
    names.remove(previousComponent)
    val restart = restartIfEnabled && names.contains(component)
    if (restart) {
        // 无障碍出现故障，先移除再添加以触发系统重启服务。
        names.remove(component)
        submit("remove ${component.flattenToShortString()} before restart")
        // 必须等待一段时间，否则概率不会触发系统重启无障碍。
        delay(A11Y_AWAIT_FIX_TIME.milliseconds)
        if (!canContinueA11yRequest(component, automatic)) return false
        names = app.getSecureA11yServices()
        names.remove(previousComponent)
    }
    names.add(component)
    val action = if (restart) "restore after removal" else "enable"
    submit("$action ${component.flattenToShortString()}")
    return true
}

private suspend fun startA11yService() {
    if (!PermissionStates.writeSecureSettings.updateAndGet()) {
        ToastUtils.show(Res.string.secure_settings_permission_required.getSync())
        return
    }
    val component = A11yService.preferredComponent
    if (isA11yBound(component) || !enableA11yService(component, restartIfEnabled = true)) return
    // https://github.com/orgs/gkd-kit/discussions/799
    if (!awaitA11yBinding(component) && canContinueA11yRequest(component, automatic = false)) {
        ToastUtils.show(Res.string.a11y_enable_failed.getSync())
        showAccessRestrictedSettingsDialog()
    }
}

fun switchAutomatorService(@CallSite loc: String = "") =
    launchServiceControl(loc = loc) {
        if (A11yService.isRunning.value || uiAutomationFlow.value != null) {
            updateEnableAutomator(false)
            A11yService.instance?.shutdown()
            uiAutomationFlow.value?.shutdown()
        } else if (settings.value.useA11y) {
            requestPrivilegeRecovery()
            startA11yService()
        } else if (privilegeContextFlow.value == null) {
            appScope.launchLogged(Dispatchers.IO) {
                recoverPrivilegeContext()
                withServiceControl {
                    if (!settings.value.useA11y) AutomationService.tryConnect()
                }
            }
        } else {
            AutomationService.tryConnect()
        }
        CompanionServiceController.update()
    }

private suspend fun fixA11yService() {
    val component = A11yService.preferredComponent
    if (isA11yBound(component) || !PermissionStates.writeSecureSettings.updateAndGet()) return
    if (component in app.getSecureA11yServices()) {
        // A scoped resume may have submitted the component before its bind callback arrives.
        if (awaitA11yBinding(component) || !canContinueA11yRequest(component, automatic = true))
            return
    }
    if (!enableA11yService(component, restartIfEnabled = true, automatic = true)) return
    if (!awaitA11yBinding(component) && canContinueA11yRequest(component, automatic = true)) {
        ToastUtils.show(Res.string.a11y_restart_failed.getSync())
        showAccessRestrictedSettingsDialog()
    }
}

private fun fixAutomationService() {
    if (uiAutomationFlow.value == null && privilegeContextFlow.value != null) {
        AutomationService.tryConnect(true)
    }
}

fun fixRestartAutomatorService(@CallSite loc: String = "") =
    launchServiceControl(loc = loc) {
        if (settings.value.enableAutomator && !currentAppBlocked) {
            requestPrivilegeRecovery()
            if (settings.value.useA11y) {
                fixA11yService()
            } else {
                fixAutomationService()
            }
        }
        CompanionServiceController.update()
    }

fun switchA11yServiceForPrivilegeChange(@CallSite loc: String = "") =
    launchServiceControl(loc = loc) {
        val current = settings.value
        if (!current.enableAutomator || !current.useA11y || currentAppBlocked)
            return@launchServiceControl
        val component = A11yService.preferredComponent
        if (isA11yBound(component) || !PermissionStates.writeSecureSettings.updateAndGet()) {
            return@launchServiceControl
        }
        enableA11yService(component, restartIfEnabled = false)
        CompanionServiceController.update()
    }

val currentAppBlocked
    get() =
        settings.value.enableBlockA11yAppList &&
            topTaskAppId in SettingsRepository.actualBlockA11yAppList

private fun updateScopedService(
    disabled: Boolean,
    @CallSite loc: String = "",
) =
    launchServiceControl(loc = loc) {
        if (!settings.value.enableAutomator || disabled != currentAppBlocked) {
            return@launchServiceControl
        }
        if (disabled) {
            A11yService.instance?.shutdown(true)
            uiAutomationFlow.value?.shutdown(true)
            return@launchServiceControl
        }
        if (settings.value.useA11y) {
            if (!PermissionStates.writeSecureSettings.stateFlow.value) {
                return@launchServiceControl
            }
            val component = A11yService.preferredComponent
            if (!isA11yBound(component)) enableA11yService(component, restartIfEnabled = false)
        } else {
            AutomationService.tryConnect(true)
        }
    }

private const val SERVICE_SCOPE_AWAIT_TIME = 3000L

private var pendingScopeChange: Job? = null

fun onTopTaskAppChanged(task: ForegroundIdentity) {
    appScope.launch(Dispatchers.Main) {
        pendingScopeChange?.cancel()
        CompanionServiceController.update()
        val blocked = currentAppBlocked
        pendingScopeChange = launchUi {
            // 只在最近任务界面延迟恢复带手势能力的无障碍。
            val delayResume =
                !blocked &&
                    task.appId == systemRecentCn.packageName &&
                    task.activityId == systemRecentCn.className &&
                    settings.value.useA11y &&
                    withContext(Dispatchers.IO) {
                        A11yService.preferredComponent == A11yService.standardComponent
                    }
            if (!blocked && !delayResume) {
                updateScopedService(false)
            }
            delay(SERVICE_SCOPE_AWAIT_TIME.milliseconds)
            if (currentAppBlocked) {
                updateScopedService(true)
            } else if (delayResume) {
                updateScopedService(false)
            }
        }
    }
}
