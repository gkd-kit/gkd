package li.gkd.app.notif

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlin.reflect.KClass
import li.gkd.app.META

class StopServiceReceiver(
    private val service: Service,
    private val onStop: () -> Unit = { service.stopSelf() },
) : BroadcastReceiver(), AutoCloseable {
    private var registered = false

    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action == STOP_ACTION &&
                intent.getStringExtra(STOP_ACTION) == service.javaClass.name
        ) {
            onStop()
        }
    }

    fun register(): StopServiceReceiver {
        if (registered) return this
        ContextCompat.registerReceiver(
            service,
            this,
            IntentFilter(STOP_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
        return this
    }

    override fun close() {
        if (!registered) return
        service.unregisterReceiver(this)
        registered = false
    }

    companion object {
        private val STOP_ACTION by lazy { META.appId + ".STOP_SERVICE" }

        fun getIntent(clazz: KClass<out Service>) =
            Intent().apply {
                action = STOP_ACTION
                putExtra(STOP_ACTION, clazz.java.name)
                setPackage(META.appId)
            }
    }
}
