package li.songe.gkd.notif

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import li.songe.gkd.META
import li.songe.gkd.util.OnCreate
import li.songe.gkd.util.OnDestroy
import li.songe.gkd.util.componentName

class StopServiceReceiver(private val service: Service) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        if (intent.action == STOP_ACTION && intent.getStringExtra(STOP_ACTION) == service::class.componentName.className) {
            service.stopSelf()
        }
    }

    companion object {
        val STOP_ACTION by lazy { META.appId + ".STOP_SERVICE" }

        fun autoRegister(service: Service) {
            if (service !is OnCreate) {
                error("StopServiceReceiver cannot be auto-registered in OnCreate")
            }
            if (service !is OnDestroy) {
                error("StopServiceReceiver cannot be auto-registered in OnDestroy")
            }
            val receiver = StopServiceReceiver(service)
            service.onCreated {
                ContextCompat.registerReceiver(
                    service,
                    receiver,
                    IntentFilter(STOP_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
            service.onDestroyed {
                service.unregisterReceiver(receiver)
            }
        }
    }
}
