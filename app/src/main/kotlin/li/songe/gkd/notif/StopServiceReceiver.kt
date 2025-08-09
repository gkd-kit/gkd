package li.songe.gkd.notif

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import li.songe.gkd.META
import li.songe.gkd.util.OnCreateToDestroy
import li.songe.gkd.util.componentName
import kotlin.reflect.KClass

class StopServiceReceiver(private val service: Service) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        if (intent.action == STOP_ACTION && intent.getStringExtra(STOP_ACTION) == service::class.componentName.className) {
            service.stopSelf()
        }
    }

    companion object {
        private val STOP_ACTION by lazy { META.appId + ".STOP_SERVICE" }

        fun getIntent(clazz: KClass<out Service>): Intent {
            return Intent().apply {
                action = STOP_ACTION
                putExtra(STOP_ACTION, clazz.componentName.className)
                setPackage(META.appId)
            }
        }

        context(service: T)
        fun <T> autoRegister() where T : Service, T : OnCreateToDestroy {
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
