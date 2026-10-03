package li.gkd.app.priv

import android.os.Process
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.permission.PermissionStates
import li.gkd.app.resources.Res
import li.gkd.app.resources.privilege_service_connect_success
import li.gkd.app.resources.privilege_service_connecting
import li.gkd.app.resources.privilege_service_disconnected
import li.gkd.app.resources.privilege_service_state_update_failed
import li.gkd.app.service.ExposeService
import li.gkd.app.service.StatusService
import li.gkd.app.service.currentAppBlocked
import li.gkd.app.service.currentAppUseA11y
import li.gkd.app.service.updateTopTaskAppId
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.launchLogged
import priv.kit.core.Privilege
import priv.kit.core.PrivilegeServerInfo
import priv.kit.core.userservice.PrivilegeUserServiceSpec

val currentUserId by lazy { Process.myUserHandle().hashCode() }

val privilegeContextFlow: StateFlow<PrivilegeContext?>
    field = MutableStateFlow(null)

private val userServiceSpec = PrivilegeUserServiceSpec(
    serviceClassName = UserService::class.java.name,
    version = 2, // Bump whenever the UserService implementation or its AIDL contract changes.
    embedded = true,
)

private suspend fun clearPrivilegeContext(context: PrivilegeContext) {
    if (!privilegeContextFlow.compareAndSet(context, null)) return
    uiAutomationFlow.value?.shutdown(true)
    try {
        context.destroy()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LogUtils.d("destroy PrivilegeContext failed", e)
    }
}

private suspend fun updatePrivilegeContext(serverInfo: PrivilegeServerInfo?) =
    withContext(Dispatchers.IO) {
        val oldContext = privilegeContextFlow.value
        if (oldContext?.serverInfo == serverInfo) return@withContext

        if (serverInfo != null) {
            if (oldContext != null) {
                clearPrivilegeContext(oldContext)
            }

            if (!app.justStarted) {
                ToastUtils.show(Res.string.privilege_service_connecting.getSync())
            }
            val userServiceConnection = Privilege.bindUserService(userServiceSpec)
            val privilegeContext = PrivilegeContext.create(serverInfo, userServiceConnection)
            privilegeContextFlow.value = privilegeContext
            privilegeContext.topCpn()?.let { cpn ->
                updateTopTaskAppId(cpn.packageName)
            }
            if (
                SettingsRepository.settings.value.enableAutomator &&
                SettingsRepository.settings.value.useAutomation &&
                !currentAppBlocked &&
                !currentAppUseA11y
            ) {
                AutomationService.tryConnect(true)
            }
            PermissionStates.refreshAll()
            if (StatusService.needRestart) {
                privilegeContext.startForegroundService(
                    ExposeService.exposeIntent(expose = ExposeService.ACTION_START_STATUS),
                )
            }
            val delayMillis = if (app.justStarted) 1200L else 0L
            ToastUtils.show(
                Res.string.privilege_service_connect_success.getSync(),
                delayMillis = delayMillis
            )
        } else if (oldContext != null) {
            clearPrivilegeContext(oldContext)
            PermissionStates.refreshAll()
            ToastUtils.show(Res.string.privilege_service_disconnected.getSync())
        }
    }

fun initPrivilege() {
    var configuredEnableAutomator = SettingsRepository.settings.value.enableAutomator
    PrivilegeOwnerLifecycle.configure(configuredEnableAutomator)
    appScope.launchLogged(Dispatchers.IO) {
        SettingsRepository.settings.collect { settings ->
            if (settings.enableAutomator != configuredEnableAutomator) {
                configuredEnableAutomator = settings.enableAutomator
                PrivilegeOwnerLifecycle.configure(configuredEnableAutomator)
            }
        }
    }

    appScope.launchLogged {
        Privilege.serverState.collect { serverInfo ->
            LogUtils.d("Privilege.serverState", serverInfo)
            try {
                updatePrivilegeContext(serverInfo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                LogUtils.d("update PrivilegeContext failed", e)
                ToastUtils.show(Res.string.privilege_service_state_update_failed.getSync(e.message))
            }
        }
    }
}
