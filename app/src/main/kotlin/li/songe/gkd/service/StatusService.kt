package li.songe.gkd.service

import android.app.Service
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.notif.abNotif
import li.songe.gkd.permission.foregroundServiceSpecialUseState
import li.songe.gkd.permission.notificationState
import li.songe.gkd.store.actionCountFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.OnCreateToDestroy
import li.songe.gkd.util.getSubsStatus
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.startForegroundServiceByClass
import li.songe.gkd.util.stopServiceByClass

class StatusService : Service(), OnCreateToDestroy {
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
                    if (!abRunning) return@combine "无障碍未授权"
                    if (!store.enableMatch) return@combine "暂停规则匹配"
                    if (store.useCustomNotifText) {
                        return@combine store.customNotifText
                            .replace("\${i}", ruleSummary.globalGroups.size.toString())
                            .replace("\${k}", ruleSummary.appSize.toString())
                            .replace("\${u}", ruleSummary.appGroupSize.toString())
                            .replace("\${n}", count.toString())
                    }
                    return@combine getSubsStatus(ruleSummary, count)
                }.debounce(500L).stateIn(scope, SharingStarted.Eagerly, "").collect { text ->
                    abNotif.copy(text = text).notifyService()
                }
            }
        }
    }

    companion object {
        val isRunning = MutableStateFlow(false)
        fun start() = startForegroundServiceByClass(StatusService::class)
        fun stop() = stopServiceByClass(StatusService::class)

        fun autoStart() {
            // 重启自动打开通知栏状态服务
            if (storeFlow.value.enableStatusService
                && !isRunning.value
                && notificationState.updateAndGet()
                && foregroundServiceSpecialUseState.updateAndGet()
            ) {
                start()
            }
        }
    }
}