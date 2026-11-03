package li.gkd.app.priv

import android.content.Intent
import android.os.Process
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.input.ActivityScene
import li.gkd.app.matching.input.updateTopActivity
import li.gkd.app.permission.PermissionStates
import li.gkd.app.resources.*
import li.gkd.app.service.StatusService
import li.gkd.app.service.currentAppBlocked
import li.gkd.app.service.switchA11yServiceForPrivilegeChange
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.util.AndroidStorage
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.launchLogged
import priv.kit.core.Privilege
import priv.kit.core.PrivilegeConfig
import priv.kit.core.PrivilegeServerInfo
import priv.kit.core.userservice.PrivilegeUserServiceSpec

val currentUserId by lazy { Process.myUserHandle().hashCode() }

val privilegeContextFlow: StateFlow<PrivilegeContext?>
    field = MutableStateFlow(null)

private val privilegeContextMutex = Mutex()

private val userServiceSpec =
    PrivilegeUserServiceSpec(
        serviceClassName = UserService::class.java.name,
        version = 8, // Bump whenever the UserService implementation or its AIDL contract changes.
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
        LogUtils.e("destroy PrivilegeContext failed", e)
    }
}

private suspend fun updatePrivilegeContext(serverInfo: PrivilegeServerInfo?) =
    privilegeContextMutex.withLock {
        if (Privilege.serverState.value != serverInfo) return@withLock
        val oldContext = privilegeContextFlow.value
        if (oldContext?.serverInfo == serverInfo) return@withLock

        if (serverInfo != null) {
            val showToast = !app.justStarted
            if (oldContext != null) {
                clearPrivilegeContext(oldContext)
            }

            if (showToast) {
                ToastUtils.show(Res.string.privilege_service_connecting.getSync())
            }
            val userServiceConnection = Privilege.bindUserService(userServiceSpec)
            val privilegeContext = PrivilegeContext.create(serverInfo, userServiceConnection)
            privilegeContextFlow.value = privilegeContext
            privilegeContext.observeSystemSurface()
            val taskAppChanged =
                privilegeContext.topCpn()?.let { cpn ->
                    updateTopActivity(cpn.packageName, cpn.className, ActivityScene.TaskStack)
                } ?: false
            val settings = SettingsRepository.settings.value
            if (
                !taskAppChanged &&
                    settings.enableAutomator &&
                    !settings.useA11y &&
                    !currentAppBlocked
            ) {
                AutomationService.tryConnect(true)
            }
            switchA11yServiceForPrivilegeChange()
            PermissionStates.refreshAll()
            if (StatusService.needRestart) {
                privilegeContext.startForegroundService(
                    Intent(app, StatusService::class.java),
                )
            }
            if (showToast) {
                ToastUtils.show(Res.string.privilege_service_connect_success.getSync())
            }
        } else if (oldContext != null) {
            clearPrivilegeContext(oldContext)
            switchA11yServiceForPrivilegeChange()
            PermissionStates.refreshAll()
            ToastUtils.show(Res.string.privilege_service_disconnected.getSync())
        }
    }

suspend fun recoverPrivilegeContext() {
    val serverInfo = recoverPrivilege() ?: return
    updatePrivilegeContext(serverInfo)
}

fun initPrivilege() {
    PrivilegeConfig.crashLogDirectory = AndroidStorage.privilegeCrashDirectory
    PrivilegeOwnerLifecycle.initialize()

    appScope.launchLogged(Dispatchers.IO) {
        var wasConnected = false
        Privilege.serverState.collect { serverInfo ->
            val disconnected = wasConnected && serverInfo == null
            wasConnected = serverInfo != null
            LogUtils.i("Privilege.serverState", serverInfo)
            if (serverInfo != null) clearPrivilegeRecoveryCooldown()
            try {
                updatePrivilegeContext(serverInfo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                LogUtils.e("update PrivilegeContext failed", e)
                ToastUtils.show(Res.string.privilege_service_state_update_failed.getSync(e.message))
            }
            if (disconnected) requestPrivilegeRecovery()
        }
    }
}
