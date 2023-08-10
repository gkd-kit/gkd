package li.songe.gkd.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcelable
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import li.songe.gkd.app
import li.songe.gkd.appScope

private const val STORE_KEY = "store-v1"
private const val EVENT_KEY = "updateStore"

/**
 * 属性不可删除,注释弃用即可
 * 属性声明顺序不可变动
 * 新增属性必须在尾部声明
 * 否则导致序列化错误
 */
@Parcelize
data class Store(
    val enableService: Boolean = true,
    val excludeFromRecents: Boolean = true,
    val enableConsoleLogOut: Boolean = true,
    val enableCaptureScreenshot: Boolean = true,
    val httpServerPort: Int = 8888,
) : Parcelable

private fun getStore(): Store {
    return kv.decodeParcelable(STORE_KEY, Store::class.java) ?: Store()
}

val storeFlow by lazy<StateFlow<Store>> {
    val state = MutableStateFlow(getStore())
    val receiver=object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.extras?.getString(EVENT_KEY) ?: return
            state.value = getStore()
        }
    }
    app.registerReceiver(receiver, IntentFilter(app.packageName))
//    app.unregisterReceiver(receiver)
    appScope.launch {
        LogUtils.getConfig().setConsoleSwitch(state.value.enableConsoleLogOut)
        state.collect {
            LogUtils.getConfig().setConsoleSwitch(state.value.enableConsoleLogOut)
        }
    }
    state
}

fun updateStore(newStore: Store) {
    if (storeFlow.value == newStore) return
    kv.encode(STORE_KEY, newStore)
    app.sendBroadcast(Intent(app.packageName).apply {
        putExtra(EVENT_KEY, EVENT_KEY)
    })
}

