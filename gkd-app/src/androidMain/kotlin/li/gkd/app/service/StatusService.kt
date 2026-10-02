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
import li.gkd.app.META
import li.gkd.app.a11y.useA11yServiceEnabledFlow
import li.gkd.app.app
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.notif.replaceNotificationTemplate
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.overlay.KeepAliveOverlayCoordinator
import li.gkd.app.priv.PrivilegeServiceStatus
import li.gkd.app.priv.privilegeServiceStatusFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.resources.Res
import li.gkd.app.resources.a11y_fault
import li.gkd.app.resources.a11y_stopped
import li.gkd.app.resources.a11y_unauthorized
import li.gkd.app.resources.automation_stopped
import li.gkd.app.resources.permission_restricted_reauthorize
import li.gkd.app.resources.persistent_notification
import li.gkd.app.resources.privilege_service_connection_lost
import li.gkd.app.resources.rule_matching_pause
import li.gkd.app.resources.service_partially_disabled_detail
import li.gkd.app.settings.SettingsRepository.actionCount
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.ui.share.statusText
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.IntentUtils
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.milliseconds

class StatusService : LifecycleHookService() {

    private val a11yServiceEnabledFlow by lazy { useA11yServiceEnabledFlow(lifecycleScope) }
    private suspend fun statusTriple(): Triple<String, String, String?> {
        val abRunning = A11yService.isRunning.value
        val automationRunning = uiAutomationFlow.value != null
        val store = settings.value
        val ruleSummary = SubscriptionState.ruleSummaryFlow.value
        val count = actionCount.value
        val privilegeServiceStatus = privilegeServiceStatusFlow.value
        val title = if (store.useCustomNotifText) {
            store.customNotifTitle.replaceNotificationTemplate(ruleSummary, count)
        } else {
            META.appName
        }
        return if (PermissionStates.appOpsRestrictedFlow.value) {
            Triple(title, getString(Res.string.permission_restricted_reauthorize), "gkd://page/3")
        } else if (privilegeServiceStatus == PrivilegeServiceStatus.DisconnectedDesired) {
            Triple(title, getString(Res.string.privilege_service_connection_lost), "gkd://page/4")
        } else if (!automationRunning && !abRunning) {
            if (currentAppUseA11y) {
                val text = if (a11yServiceEnabledFlow.value) {
                    getString(Res.string.a11y_fault)
                } else if (PermissionStates.writeSecureSettings.updateAndGet()) {
                    if (store.enableAutomator && store.enableBlockA11yAppList && a11yPartDisabledFlow.value) {
                        val name =
                            AppInfoRepository.snapshot?.apps?.get(topAppIdFlow.value)?.name
                                ?: topAppIdFlow.value
                        getString(Res.string.service_partially_disabled_detail, name)
                    } else {
                        getString(Res.string.a11y_stopped)
                    }
                } else {
                    getString(Res.string.a11y_unauthorized)
                }
                Triple(title, text, defaultStatusNotification.uri)
            } else {
                val text =
                    if (store.enableAutomator && store.enableBlockA11yAppList && a11yPartDisabledFlow.value) {
                        val name =
                            AppInfoRepository.snapshot?.apps?.get(topAppIdFlow.value)?.name
                                ?: topAppIdFlow.value
                        getString(Res.string.service_partially_disabled_detail, name)
                    } else {
                        getString(Res.string.automation_stopped)
                    }
                Triple(title, text, defaultStatusNotification.uri)
            }
        } else if (!store.enableMatch) {
            Triple(title, getString(Res.string.rule_matching_pause), "gkd://page?tab=1")
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
            name = Res.string.persistent_notification.getSync(),
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
                    settings,
                    SubscriptionState.ruleSummaryFlow,
                    privilegeServiceStatusFlow,
                    a11yServiceEnabledFlow,
                    PermissionStates.writeSecureSettings.stateFlow,
                    PermissionStates.appOpsRestrictedFlow,
                    topAppIdFlow,
                    actionCount.debounce(1000L.milliseconds),
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
            get() = settings.value.enableStatusService
                    && !isRunning.value
                    && PermissionStates.notification.updateAndGet()
                    && PermissionStates.foregroundServiceSpecialUse.updateAndGet()

        fun start() = IntentUtils.startForegroundService(StatusService::class)
        fun stop() = IntentUtils.stopService(StatusService::class)
        private var lastAutoStart = 0L
        fun autoStart() {
            if (System.currentTimeMillis() - lastAutoStart < 1000) return
            // 重启自动打开通知栏状态服务
            // 需要前台、已有服务或开机广播等系统豁免场景，否则系统可能拒绝启动前台服务
            if (needRestart) {
                start()
                lastAutoStart = System.currentTimeMillis()
            }
        }
    }
}

private val defaultStatusNotification by lazy { NotificationCatalog.status() }
