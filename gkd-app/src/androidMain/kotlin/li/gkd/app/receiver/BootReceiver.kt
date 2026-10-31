package li.gkd.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import li.gkd.app.service.StatusService
import li.gkd.app.service.fixRestartAutomatorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            fixRestartAutomatorService()
            StatusService.autoStart()
        }
    }
}
