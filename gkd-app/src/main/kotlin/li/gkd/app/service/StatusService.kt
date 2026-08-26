package li.gkd.app.service

import android.app.Service
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.gkd.app.META
import li.gkd.app.MainViewModel
import li.gkd.app.a11y.useA11yServiceEnabledFlow
import li.gkd.app.app
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.PrivilegeServiceStatus
import li.gkd.app.priv.privilegeServiceStatusFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.store.actionCountFlow
import li.gkd.app.store.storeFlow
import li.gkd.app.util.DefaultSimpleLifeImpl
import li.gkd.app.util.OnSimpleLife
import li.gkd.app.util.RuleSummary
import li.gkd.app.util.appInfoMapFlow
import li.gkd.app.util.getSubsStatus
import li.gkd.app.util.ruleSummaryFlow
import li.gkd.app.util.startForegroundServiceByClass
import li.gkd.app.util.stopServiceByClass
import kotlin.time.Duration.Companion.milliseconds

class StatusService : Service(), OnSimpleLife by DefaultSimpleLifeImpl() {
    override fun onBind(intent: Intent?) = null
    override fun onCreate() = onCreated()
    override fun onDestroy() = onDestroyed()

    val a11yServiceEnabledFlow = useA11yServiceEnabledFlow()

    fun statusTriple(): Triple<String, String, String?> {
        val abRunning = A11yService.isRunning.value
        val automationRunning = uiAutomationFlow.value != null
        val store = storeFlow.value
        val ruleSummary = ruleSummaryFlow.value
        val count = actionCountFlow.value
        val privilegeServiceStatus = privilegeServiceStatusFlow.value
        val title = if (store.useCustomNotifText) {
            store.customNotifTitle.replaceTemplate(ruleSummary, count)
        } else {
            META.appName
        }
        return if (PermissionStates.appOpsRestrictedFlow.value) {
            Triple(title, "权限受限，请重新授权", "gkd://page/3")
        } else if (privilegeServiceStatus == PrivilegeServiceStatus.DisconnectedDesired) {
            Triple(title, "特权服务连接已中断，请检查", "gkd://page/4")
        } else if (!automationRunning && !abRunning) {
            if (currentAppUseA11y) {
                val text = if (a11yServiceEnabledFlow.value) {
                    "无障碍发生故障"
                } else if (PermissionStates.writeSecureSettings.updateAndGet()) {
                    if (store.enableAutomator && store.enableBlockA11yAppList && a11yPartDisabledFlow.value) {
                        val name =
                            appInfoMapFlow.value[topAppIdFlow.value]?.name ?: topAppIdFlow.value
                        "局部关闭 · $name"
                    } else {
                        "无障碍已关闭"
                    }
                } else {
                    "无障碍未授权"
                }
                Triple(title, text, defaultStatusNotification.uri)
            } else {
                val text =
                    if (store.enableAutomator && store.enableBlockA11yAppList && a11yPartDisabledFlow.value) {
                        val name =
                            appInfoMapFlow.value[topAppIdFlow.value]?.name ?: topAppIdFlow.value
                        "局部关闭 · $name"
                    } else {
                        "自动化已关闭"
                    }
                Triple(title, text, defaultStatusNotification.uri)
            }
        } else if (!store.enableMatch) {
            Triple(title, "暂停规则匹配", "gkd://page?tab=1")
        } else if (store.useCustomNotifText) {
            Triple(
                title,
                store.customNotifText.replaceTemplate(ruleSummary, count),
                defaultStatusNotification.uri
            )
        } else {
            Triple(title, getSubsStatus(ruleSummary, count), defaultStatusNotification.uri)
        }
    }

    init {
        useAliveFlow(isRunning)
        useAliveToast(
            name = "常驻通知",
            delayMillis = if (app.justStarted) 1000 else 0,
        )
        onCreated {
            if (!defaultStatusNotification.startForeground()) return@onCreated
            scope.launch {
                combine(
                    A11yService.isRunning,
                    uiAutomationFlow,
                    storeFlow,
                    ruleSummaryFlow,
                    privilegeServiceStatusFlow,
                    a11yServiceEnabledFlow,
                    PermissionStates.writeSecureSettings.stateFlow,
                    PermissionStates.appOpsRestrictedFlow,
                    topAppIdFlow,
                    actionCountFlow.debounce(1000L.milliseconds),
                ) {
                    statusTriple()
                }
                    .stateIn(
                        scope,
                        SharingStarted.Eagerly,
                        Triple(
                            defaultStatusNotification.title,
                            defaultStatusNotification.text,
                            defaultStatusNotification.uri,
                        )
                    )
                    .collect {
                        NotificationCatalog.status(
                            title = it.first,
                            text = it.second,
                            uri = it.third,
                        ).startForeground()
                    }
            }
        }
    }

    companion object {
        val isRunning = MutableStateFlow(false)
        val needRestart
            get() = storeFlow.value.enableStatusService
                    && !isRunning.value
                    && PermissionStates.notification.updateAndGet()
                    && PermissionStates.foregroundServiceSpecialUse.updateAndGet()

        fun start() = startForegroundServiceByClass(StatusService::class)
        fun stop() = stopServiceByClass(StatusService::class)
        suspend fun requestStart(mainVm: MainViewModel) {
            if (
                !mainVm.permissionRequests.ensurePermissions(
                    PermissionStates.foregroundServiceSpecialUse,
                    PermissionStates.notification,
                )
            ) {
                return
            }
            start()
            storeFlow.update { it.copy(enableStatusService = true) }
        }

        private var lastAutoStart = 0L
        fun autoStart() {
            if (System.currentTimeMillis() - lastAutoStart < 1000) return
            // 重启自动打开通知栏状态服务
            // 需要已有服务或前台才能自主启动，否则报错 startForegroundService() not allowed due to mAllowStartForeground false
            if (needRestart) {
                start()
                lastAutoStart = System.currentTimeMillis()
            }
        }
    }
}

private val defaultStatusNotification by lazy { NotificationCatalog.status() }

private fun String.replaceTemplate(ruleSummary: RuleSummary, count: Long): String {
    return replace($$"${i}", ruleSummary.globalGroups.size.toString())
        .replace($$"${k}", ruleSummary.appSize.toString())
        .replace($$"${u}", ruleSummary.appGroupSize.toString())
        .replace($$"${n}", count.toString())
}
