package li.songe.gkd.composition

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import li.songe.gkd.util.Singleton
import kotlin.coroutines.CoroutineContext

object CompositionExt {
    fun CanOnDestroy.useScope(context: CoroutineContext = Dispatchers.Default): CoroutineScope {
        val scope = CoroutineScope(context)
        onDestroy { scope.cancel() }
        return scope
    }

    fun Context.useMessage(name: String? = null): Pair<(f: ((InvokeMessage) -> Unit)) -> Unit, (InvokeMessage) -> Unit> {
        this as CanOnDestroy

        var onMessage: (InvokeMessage) -> Unit = {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val s = intent?.extras?.getString("__invoke") ?: return
                val message = Singleton.json.decodeFromString<InvokeMessage>(s)!!

                if (name != null && message.name != name) {
                    return
                }
                onMessage(message)
            }
        }
        val filter = IntentFilter(packageName)

        val broadcastManager = LocalBroadcastManager.getInstance(this)
        broadcastManager.registerReceiver(receiver, filter)
        val sendMessage: (InvokeMessage) -> Unit = { message ->
            broadcastManager.sendBroadcast(Intent(packageName).apply {
                putExtra("__invoke", Singleton.json.encodeToString(message))
            })
        }
        onDestroy {
            broadcastManager.unregisterReceiver(receiver)
        }
        val setter: ((InvokeMessage) -> Unit) -> Unit = { onMessage = it }
        return (setter to sendMessage)
    }


    fun Context.useLifeCycleLog() {
        val simpleName = this::class.simpleName
        when (this) {
            is Activity, is Service -> {
                LogUtils.d(simpleName, "onCreate")
            }

            else -> {
                LogUtils.w("current context is not the one of Activity, Service", this)
            }
        }

        if (this is CanOnDestroy) {
            onDestroy {
                LogUtils.d(simpleName, "onDestroy")
            }
        }
        if (this is CanOnInterrupt) {
            onInterrupt {
                LogUtils.d(simpleName, "onInterrupt")
            }
        }

        if (this is CanOnServiceConnected) {
            onServiceConnected {
                LogUtils.d(simpleName, "onServiceConnected")
            }
        }

        if (this is CanOnConfigurationChanged) {
            onConfigurationChanged {
                LogUtils.d(simpleName, "onConfigurationChanged", it)
            }
        }

    }
}