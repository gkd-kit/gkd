package li.songe.gkd.service

import android.content.Context
import android.content.Intent
import li.songe.gkd.app
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionService
import li.songe.gkd.notif.abNotif
import li.songe.gkd.notif.createNotif
import li.songe.gkd.notif.defaultChannel

class ManageService : CompositionService({
    useLifeCycleLog()
    val context = this
    createNotif(context, defaultChannel, abNotif)
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