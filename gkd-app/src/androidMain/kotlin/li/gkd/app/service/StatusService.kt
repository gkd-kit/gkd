package li.gkd.app.service

import androidx.lifecycle.lifecycleScope
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import li.gkd.app.META
import li.gkd.app.a11y.useA11yServiceEnabledFlow
import li.gkd.app.app
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.matching.input.topTaskAppId
import li.gkd.app.matching.input.topTaskAppIdFlow
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.notif.replaceNotificationTemplate
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.PrivilegeServiceStatus
import li.gkd.app.priv.privilegeServiceStatusFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.resources.*
import li.gkd.app.rule.RuleSummary
import li.gkd.app.settings.AppLanguage
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SettingsRepository.actionCount
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.util.IntentUtils
import org.jetbrains.compose.resources.getString

class StatusService : LifecycleHookService() {

    private val a11yServiceEnabledFlow by lazy { useA11yServiceEnabledFlow(lifecycleScope) }

    private suspend fun statusTriple(): Triple<String, String, String?> {
        val abRunning = A11yService.isRunning.value
        val automationRunning = uiAutomationFlow.value != null
        val store = settings.value
        val appId = topTaskAppId
        val partiallyDisabled =
            store.enableAutomator &&
                store.enableBlockA11yAppList &&
                appId in SettingsRepository.actualBlockA11yAppList
        val ruleSummary = SubscriptionState.ruleSummaryFlow.value
        val count = actionCount.value
        val privilegeServiceStatus = privilegeServiceStatusFlow.value
        val title =
            if (store.useCustomNotifText) {
                store.customNotifTitle.replaceNotificationTemplate(ruleSummary, count)
            } else {
                META.appName
            }
        return if (PermissionStates.appOpsRestrictedFlow.value) {
            Triple(title, getString(Res.string.permission_restricted_reauthorize), "gkd://page/3")
        } else if (privilegeServiceStatus == PrivilegeServiceStatus.DisconnectedDesired) {
            Triple(title, getString(Res.string.privilege_service_connection_lost), "gkd://page/4")
        } else if (!automationRunning && !abRunning) {
            val text =
                when {
                    store.useA11y && a11yServiceEnabledFlow.value ->
                        getString(Res.string.a11y_fault)
                    store.useA11y && !PermissionStates.writeSecureSettings.updateAndGet() ->
                        getString(Res.string.a11y_unauthorized)
                    partiallyDisabled -> {
                        val name = AppInfoRepository.snapshot?.apps?.get(appId)?.name ?: appId
                        getString(Res.string.service_partially_disabled_detail, name)
                    }
                    store.enableAutomator ->
                        getString(
                            if (store.useA11y) Res.string.a11y_not_running
                            else Res.string.automation_not_running,
                        )
                    store.useA11y -> getString(Res.string.a11y_stopped)
                    else -> getString(Res.string.automation_stopped)
                }
            Triple(title, text, null)
        } else if (!store.enableMatch) {
            Triple(title, getString(Res.string.rule_matching_pause), "gkd://page?tab=1")
        } else if (store.useCustomNotifSubtitle) {
            Triple(
                title,
                store.customNotifText.replaceNotificationTemplate(ruleSummary, count),
                null,
            )
        } else {
            Triple(title, RuleSummary.statusText(ruleSummary, count), null)
        }
    }

    init {
        useServicePresence(
            stateFlow = isRunning,
            name = Res.string.persistent_notification,
            showStartToast = !app.justStarted,
        )
        onCreated {
            if (!NotificationCatalog.status().startForeground()) return@onCreated
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
                        topTaskAppIdFlow,
                        actionCount.debounce(1000L.milliseconds),
                        AppLanguage.locale,
                    ) {
                        statusTriple()
                    }
                    .collect {
                        NotificationCatalog.status(
                                title = it.first,
                                text = it.second,
                                uri = it.third,
                            )
                            .startForeground()
                    }
            }
        }
    }

    companion object {
        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)

        val needRestart
            get() =
                settings.value.enableStatusService &&
                    !isRunning.value &&
                    PermissionStates.notification.updateAndGet() &&
                    PermissionStates.foregroundServiceSpecialUse.updateAndGet()

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
