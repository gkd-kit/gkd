package li.songe.gkd.service

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import li.songe.gkd.app
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionExt.useScope
import li.songe.gkd.composition.CompositionService
import li.songe.gkd.notif.abNotif
import li.songe.gkd.notif.createNotif
import li.songe.gkd.notif.defaultChannel
import li.songe.gkd.util.appIdToRulesFlow
import li.songe.gkd.util.clickCountFlow
import li.songe.gkd.util.storeFlow
import rikka.shizuku.ShizukuProvider

class ManageService : CompositionService({
    useLifeCycleLog()
    val context = this
    createNotif(context, defaultChannel.id, abNotif)
    val scope = useScope()


    ShizukuProvider.requestBinderForNonProviderProcess(this)

    scope.launch {
        combine(appIdToRulesFlow, clickCountFlow, storeFlow) { appIdToRules, clickCount, store ->
            if (!store.enableService) return@combine "服务已暂停"
            val appSize = appIdToRules.keys.size
            val groupSize =
                appIdToRules.values.flatten().map { r -> r.group.hashCode() }.toSet().size
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