package li.songe.gkd.service

import android.app.Service
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.META
import li.songe.gkd.a11y.useA11yServiceEnabledFlow
import li.songe.gkd.app
import li.songe.gkd.notif.abNotif
import li.songe.gkd.permission.foregroundServiceSpecialUseState
import li.songe.gkd.permission.notificationState
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.store.actionCountFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.OnSimpleLife
import li.songe.gkd.util.RuleSummary
import li.songe.gkd.util.getSubsStatus
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.startForegroundServiceByClass
import li.songe.gkd.util.stopServiceByClass

class StatusService : Service(), OnSimpleLife {
    override fun onBind(intent: Intent?) = null
    override fun onCreate() = onCreated()
    override fun onDestroy() = onDestroyed()

    val scope = useScope()

    val shizukuWarnFlow = combine(
        shizukuOkState.stateFlow,
        storeFlow.map { it.enableShizuku },
    ) { a, b ->
        !a && b
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val a11yServiceEnabledFlow = useA11yServiceEnabledFlow()

    fun statusTriple(): Triple<String, String, String?> {
        val abRunning = A11yService.isRunning.value
        val store = storeFlow.value
        val ruleSummary = ruleSummaryFlow.value
        val count = actionCountFlow.value
        val shizukuWarn = shizukuWarnFlow.value
        val title = if (store.useCustomNotifText) {
            store.customNotifTitle.replaceTemplate(ruleSummary, count)
        } else {
            META.appName
        }
        return if (!abRunning) {
            val text = if (a11yServiceEnabledFlow.value) {
                "无障碍发生故障"
            } else if (writeSecureSettingsState.updateAndGet()) {
                if (store.enableService && a11yPartDisabledFlow.value) {
                    "无障碍已局部关闭"
                } else {
                    "无障碍已关闭"
                }
            } else {
                "无障碍未授权"
            }
            Triple(title, text, abNotif.uri)
        } else if (!store.enableMatch) {
            Triple(title, "暂停规则匹配", "gkd://page?tab=1")
        } else if (shizukuWarn) {
            Triple(title, "Shizuku 未连接，请授权或关闭优化", "gkd://page/1")
        } else if (store.useCustomNotifText) {
            Triple(
                title,
                store.customNotifText.replaceTemplate(ruleSummary, count),
                abNotif.uri
            )
        } else {
            Triple(title, getSubsStatus(ruleSummary, count), abNotif.uri)
        }
    }

    init {
        useAliveFlow(isRunning)
        useAliveToast(
            name = "常驻通知",
            onlyWhenVisible = true,
            delayMillis = if (app.justStarted) 1000 else 0
        )
        onCreated {
            abNotif.notifyService()
            scope.launch {
                combine(
                    A11yService.isRunning,
                    storeFlow,
                    ruleSummaryFlow,
                    shizukuWarnFlow,
                    a11yServiceEnabledFlow,
                    writeSecureSettingsState.stateFlow,
                    a11yPartDisabledFlow,
                    actionCountFlow.debounce(1000L),
                ) {
                    statusTriple()
                }
                    .stateIn(
                        scope,
                        SharingStarted.Eagerly,
                        Triple(abNotif.title, abNotif.text, abNotif.uri)
                    )
                    .collect {
                        abNotif.copy(
                            title = it.first,
                            text = it.second,
                            uri = it.third,
                        ).notifyService()
                    }
            }
        }
    }

    companion object {
        val isRunning = MutableStateFlow(false)
        fun start() = startForegroundServiceByClass(StatusService::class)
        fun stop() = stopServiceByClass(StatusService::class)

        private var lastAutoStart = 0L
        fun autoStart() {
            if (System.currentTimeMillis() - lastAutoStart < 1000) return
            // 重启自动打开通知栏状态服务
            // 需要已有服务或前台才能自主启动，否则报错 startForegroundService() not allowed due to mAllowStartForeground false
            if (storeFlow.value.enableStatusService
                && !isRunning.value
                && notificationState.updateAndGet()
                && foregroundServiceSpecialUseState.updateAndGet()
            ) {
                start()
                lastAutoStart = System.currentTimeMillis()
            }
        }
    }
}

private fun String.replaceTemplate(ruleSummary: RuleSummary, count: Long): String {
    return replace("\${i}", ruleSummary.globalGroups.size.toString())
        .replace("\${k}", ruleSummary.appSize.toString())
        .replace("\${u}", ruleSummary.appGroupSize.toString())
        .replace("\${n}", count.toString())
}
