package li.gkd.app.service

import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import li.gkd.app.text.UiStrings
import li.gkd.app.META
import li.gkd.app.a11y.useA11yServiceEnabledFlow
import li.gkd.app.app
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.overlay.KeepAliveOverlayCoordinator
import li.gkd.app.priv.PrivilegeServiceStatus
import li.gkd.app.priv.privilegeServiceStatusFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.store.AppStore.actionCountFlow
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.notif.replaceNotificationTemplate
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.ui.share.statusText
import li.gkd.app.util.IntentUtils
import kotlin.time.Duration.Companion.milliseconds

class StatusService : LifecycleHookService() {

    private val a11yServiceEnabledFlow by lazy { useA11yServiceEnabledFlow(lifecycleScope) }
    private fun statusTriple(): Triple<String, String, String?> {
        val abRunning = A11yService.isRunning.value
        val automationRunning = uiAutomationFlow.value != null
        val store = storeFlow.value
        val ruleSummary = SubscriptionState.ruleSummaryFlow.value
        val count = actionCountFlow.value
        val privilegeServiceStatus = privilegeServiceStatusFlow.value
        val title = if (store.useCustomNotifText) {
            store.customNotifTitle.replaceNotificationTemplate(ruleSummary, count)
        } else {
            META.appName
        }
        return if (PermissionStates.appOpsRestrictedFlow.value) {
            Triple(title, UiStrings.permission_restricted_reauthorize, "gkd://page/3")
        } else if (privilegeServiceStatus == PrivilegeServiceStatus.DisconnectedDesired) {
            Triple(title, UiStrings.privilege_service_connection_lost, "gkd://page/4")
        } else if (!automationRunning && !abRunning) {
            if (currentAppUseA11y) {
                val text = if (a11yServiceEnabledFlow.value) {
                    UiStrings.a11y_fault
                } else if (PermissionStates.writeSecureSettings.updateAndGet()) {
                    if (store.enableAutomator && store.enableBlockA11yAppList && a11yPartDisabledFlow.value) {
                        val name =
                            AppInfoRepository.appInfoMapFlow.value[topAppIdFlow.value]?.name ?: topAppIdFlow.value
                        UiStrings.service_partially_disabled_detail(name)
                    } else {
                        UiStrings.a11y_stopped
                    }
                } else {
                    UiStrings.a11y_unauthorized
                }
                Triple(title, text, defaultStatusNotification.uri)
            } else {
                val text =
                    if (store.enableAutomator && store.enableBlockA11yAppList && a11yPartDisabledFlow.value) {
                        val name =
                            AppInfoRepository.appInfoMapFlow.value[topAppIdFlow.value]?.name ?: topAppIdFlow.value
                        UiStrings.service_partially_disabled_detail(name)
                    } else {
                        UiStrings.automation_stopped
                    }
                Triple(title, text, defaultStatusNotification.uri)
            }
        } else if (!store.enableMatch) {
            Triple(title, UiStrings.rule_matching_pause, "gkd://page?tab=1")
        } else if (store.useCustomNotifText) {
            Triple(
                title,
                store.customNotifText.replaceNotificationTemplate(ruleSummary, count),
                defaultStatusNotification.uri
            )
        } else {
            Triple(title, ruleSummary.statusText(count), defaultStatusNotification.uri)
        }
    }

    init {
        useServicePresence(
            stateFlow = isRunning,
            name = UiStrings.persistent_notification,
            startToastDelayMillis = if (app.justStarted) 1000 else 0,
        )
        onCreated {
            if (!defaultStatusNotification.startForeground()) return@onCreated
            lifecycleScope.launch {
                combine(
                    A11yService.isRunning,
                    KeepAliveOverlayCoordinator.accessibilityAttached,
                ) { a11yRunning, a11yOverlayAttached ->
                    a11yRunning to a11yOverlayAttached
                }.distinctUntilChanged().collectLatest {
                    val (a11yRunning, a11yOverlayAttached) = it
                    if (a11yRunning && a11yOverlayAttached) {
                        KeepAliveOverlayCoordinator.releaseAfterHandoff(
                            source = KeepAliveOverlayCoordinator.Source.Status,
                            owner = this@StatusService,
                        )
                    } else {
                        KeepAliveOverlayCoordinator.acquire(
                            source = KeepAliveOverlayCoordinator.Source.Status,
                            owner = this@StatusService,
                            context = this@StatusService,
                            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        )
                    }
                }
            }
            lifecycleScope.launch {
                combine(
                    A11yService.isRunning,
                    uiAutomationFlow,
                    storeFlow,
                    SubscriptionState.ruleSummaryFlow,
                    privilegeServiceStatusFlow,
                    a11yServiceEnabledFlow,
                    PermissionStates.writeSecureSettings.stateFlow,
                    PermissionStates.appOpsRestrictedFlow,
                    topAppIdFlow,
                    actionCountFlow.debounce(1000L.milliseconds),
                ) {
                    statusTriple()
                }.collect {
                    NotificationCatalog.status(
                        title = it.first,
                        text = it.second,
                        uri = it.third,
                    ).startForeground()
                }
            }
        }
        onDestroyed {
            KeepAliveOverlayCoordinator.release(
                source = KeepAliveOverlayCoordinator.Source.Status,
                owner = this,
            )
        }
    }

    companion object {
        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)

        val needRestart
            get() = storeFlow.value.enableStatusService
                    && !isRunning.value
                    && PermissionStates.notification.updateAndGet()
                    && PermissionStates.foregroundServiceSpecialUse.updateAndGet()

        fun start() = IntentUtils.startForegroundServiceByClass(StatusService::class)
        fun stop() = IntentUtils.stopServiceByClass(StatusService::class)
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
