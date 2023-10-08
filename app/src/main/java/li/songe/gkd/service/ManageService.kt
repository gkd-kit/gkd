package li.songe.gkd.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import li.songe.gkd.app
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionExt.useScope
import li.songe.gkd.composition.CompositionService
import li.songe.gkd.debug.SnapshotExt.captureSnapshot
import li.songe.gkd.notif.abNotif
import li.songe.gkd.notif.createNotif
import li.songe.gkd.notif.defaultChannel
import li.songe.gkd.util.VOLUME_CHANGED_ACTION
import li.songe.gkd.util.appIdToRulesFlow
import li.songe.gkd.util.clickCountFlow
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.storeFlow

class ManageService : CompositionService({
    useLifeCycleLog()
    val context = this
    createNotif(context, defaultChannel.id, abNotif)
    val scope = useScope()

    val receiver = object : BroadcastReceiver() {
        var lastTriggerTime = -1L
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VOLUME_CHANGED_ACTION) {
                val t = System.currentTimeMillis()
                if (t - lastTriggerTime > 3000 && !ScreenUtils.isScreenLock()) {
                    lastTriggerTime = t
                    scope.launchTry(Dispatchers.IO) {
                        captureSnapshot()
                        ToastUtils.showShort("快照成功")
                    }
                }
            }
        }
    }
    var registered = false
    scope.launchTry(Dispatchers.IO) {
        storeFlow.map { s -> s.captureVolumeChange }.collect {
            registered = if (it) {
                if (!registered) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(
                            receiver, IntentFilter(VOLUME_CHANGED_ACTION), Context.RECEIVER_EXPORTED
                        )
                    } else {
                        context.registerReceiver(receiver, IntentFilter(VOLUME_CHANGED_ACTION))
                    }
                }
                true
            } else {
                if (registered) {
                    context.unregisterReceiver(receiver)
                }
                false
            }
        }
    }
    onDestroy {
        if (storeFlow.value.captureVolumeChange && registered) {
            context.unregisterReceiver(receiver)
        }
    }

    scope.launch {
        combine(appIdToRulesFlow, clickCountFlow, storeFlow) { appIdToRules, clickCount, store ->
            if (!store.enableService) return@combine "服务已暂停"
            val appSize = appIdToRules.keys.size
            val groupSize = appIdToRules.values.sumOf { r -> r.size }
            (if (groupSize > 0) {
                "${appSize}应用/${groupSize}规则组"
            } else {
                "暂无规则"
            }) + if (clickCount > 0) "/${clickCount}点击" else ""
        }.collect { text ->
            createNotif(
                context, defaultChannel.id, abNotif.copy(
                    text = text
                )
            )
        }
    }
}) {
    companion object {
        fun start(context: Context = app) {
            context.startForegroundService(Intent(context, ManageService::class.java))
        }

        fun stop(context: Context = app) {
            context.stopService(Intent(context, ManageService::class.java))
        }
    }
}