package li.songe.gkd.service

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.app
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionExt.useScope
import li.songe.gkd.composition.CompositionService
import li.songe.gkd.notif.abNotif
import li.songe.gkd.notif.createNotif
import li.songe.gkd.notif.defaultChannel
import li.songe.gkd.util.actionCountFlow
import li.songe.gkd.util.getSubsStatus
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.storeFlow

class ManageService : CompositionService({
    useLifeCycleLog()
    val context = this
    createNotif(context, defaultChannel.id, abNotif)
    val scope = useScope()
    scope.launch {
        combine(
            GkdAbService.isRunning,
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
            createNotif(
                context, defaultChannel.id, abNotif.copy(
                    text = text
                )
            )
        }
    }
    isRunning.value = true
    onDestroy {
        isRunning.value = false
    }
}) {
    companion object {
        fun start(context: Context = app) {
            context.startForegroundService(Intent(context, ManageService::class.java))
        }

        val isRunning = MutableStateFlow(false)

        fun stop(context: Context = app) {
            context.stopService(Intent(context, ManageService::class.java))
        }

        fun autoStart(context: Context) {
            // 在[系统重启]/[被其它高权限应用重启]时自动打开通知栏状态服务
            if (storeFlow.value.enableStatusService &&
                NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                !isRunning.value
            ) {
                start(context)
            }
        }
    }
}

