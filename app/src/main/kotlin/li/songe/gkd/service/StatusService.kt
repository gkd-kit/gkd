package li.songe.gkd.service

import android.app.Service
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.META
import li.songe.gkd.notif.abNotif
import li.songe.gkd.permission.foregroundServiceSpecialUseState
import li.songe.gkd.permission.notificationState
import li.songe.gkd.store.actionCountFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.OnSimpleLife
import li.songe.gkd.util.getSubsStatus
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.startForegroundServiceByClass
import li.songe.gkd.util.stopServiceByClass

class StatusService : Service(), OnSimpleLife {
    override fun onBind(intent: Intent?) = null
    override fun onCreate() = onCreated()
    override fun onDestroy() = onDestroyed()

    val scope = useScope()

    init {
        useAliveFlow(isRunning)
        useAliveToast("常驻通知", onlyWhenVisible = true)
        onCreated {
            abNotif.notifyService()
            scope.launch {
                combine(
                    A11yService.isRunning,
                    storeFlow,
                    ruleSummaryFlow,
                    actionCountFlow,
                ) { abRunning, store, ruleSummary, count ->
                    if (!abRunning) {
                        META.appName to "无障碍未授权"
                    } else if (!store.enableMatch) {
                        META.appName to "暂停规则匹配"
                    } else if (store.useCustomNotifText) {
                        listOf(store.customNotifTitle, store.customNotifText).map {
                            it.replace("\${i}", ruleSummary.globalGroups.size.toString())
                                .replace("\${k}", ruleSummary.appSize.toString())
                                .replace("\${u}", ruleSummary.appGroupSize.toString())
                                .replace("\${n}", count.toString())
                        }.run {
                            first() to last()
                        }
                    } else {
                        META.appName to getSubsStatus(ruleSummary, count)
                    }
                }.debounce(1000L)
                    .stateIn(scope, SharingStarted.Eagerly, "" to "")
                    .collect { (title, text) ->
                        abNotif.copy(
                            title = title,
                            text = text
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
