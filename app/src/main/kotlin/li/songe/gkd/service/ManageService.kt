package li.songe.gkd.service

import android.app.Service
import android.content.Intent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.app
import li.songe.gkd.notif.abNotif
import li.songe.gkd.notif.notifyService
import li.songe.gkd.permission.notificationState
import li.songe.gkd.util.OnCreate
import li.songe.gkd.util.OnDestroy
import li.songe.gkd.util.actionCountFlow
import li.songe.gkd.util.getSubsStatus
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.useAliveFlow

class ManageService : Service(), OnCreate, OnDestroy {
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        onCreated()
    }

    override fun onDestroy() {
        super.onDestroy()
        onDestroyed()
    }

    val scope = MainScope().apply { onDestroyed { cancel() } }

    init {
        useAliveFlow(isRunning)
        useNotif()
    }

    companion object {
        val isRunning = MutableStateFlow(false)

        fun start() {
            if (!notificationState.checkOrToast()) return
            app.startForegroundService(Intent(app, ManageService::class.java))
        }

        fun stop() {
            app.stopService(Intent(app, ManageService::class.java))
        }

        fun autoStart() {
            // 在[系统重启]/[被其它高权限应用重启]时自动打开通知栏状态服务
            if (storeFlow.value.enableStatusService
                && !isRunning.value
                && notificationState.updateAndGet()
            ) {
                start()
            }
        }
    }
}

private fun ManageService.useNotif() {
    onCreated {
        abNotif.notifyService(this)
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
                abNotif.copy(text = text).notifyService(this@useNotif)
            }
        }
    }
}
