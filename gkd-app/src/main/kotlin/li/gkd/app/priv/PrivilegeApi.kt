package li.gkd.app.priv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.permission.PermissionStates
import li.gkd.app.service.ExposeService
import li.gkd.app.service.StatusService
import li.gkd.app.service.currentAppBlocked
import li.gkd.app.service.currentAppUseA11y
import li.gkd.app.service.updateTopTaskAppId
import li.gkd.app.store.storeFlow
import li.gkd.app.util.LogUtils
import li.gkd.app.util.launchTry
import li.gkd.app.util.toast
import priv.kit.core.Privilege
import priv.kit.core.PrivilegeServerInfo
import priv.kit.core.userservice.PrivilegeUserServiceSpec

val currentUserId by lazy { android.os.Process.myUserHandle().hashCode() }

val privilegeContextFlow = MutableStateFlow<PrivilegeContext?>(null)

private val userServiceSpec = PrivilegeUserServiceSpec(
    serviceClassName = UserService::class.java.name,
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
                toast("正在连接特权服务...")
            }
            val userServiceConnection = Privilege.bindUserService(userServiceSpec)
            val privilegeContext = PrivilegeContext.create(serverInfo, userServiceConnection)
            privilegeContextFlow.value = privilegeContext
            privilegeContext.topCpn()?.let { cpn ->
                updateTopTaskAppId(cpn.packageName)
            }
            if (
                storeFlow.value.useAutomation &&
                !currentAppBlocked &&
                !currentAppUseA11y
            ) {
                AutomationService.tryConnect(true)
            }
            PermissionStates.refreshAll()
            if (StatusService.needRestart) {
                privilegeContext.startForegroundService(
                    ExposeService.exposeIntent(expose = -1),
                )
            }
            val delayMillis = if (app.justStarted) 1200L else 0L
            toast("特权服务连接成功", delayMillis = delayMillis)
        } else if (oldContext != null) {
            clearPrivilegeContext(oldContext)
            PermissionStates.refreshAll()
            toast("特权服务已断开")
        }
    }

fun initPrivilege() {
    appScope.launchTry {
        Privilege.serverState.collect { serverInfo ->
            LogUtils.d("Privilege.serverState", serverInfo)
            try {
                updatePrivilegeContext(serverInfo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                LogUtils.d("update PrivilegeContext failed", e)
                toast("特权服务状态更新失败：${e.message}")
            }
        }
    }
}
