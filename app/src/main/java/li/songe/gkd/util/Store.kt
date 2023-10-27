package li.songe.gkd.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import li.songe.gkd.app
import li.songe.gkd.appScope
import java.util.WeakHashMap


private val onReceives by lazy {
    mutableListOf<(
        context: Context?,
        intent: Intent?,
    ) -> Unit>()
}

private val receiver by lazy {
    object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onReceives.forEach { fc -> fc(context, intent) }
        }
    }.apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(this, IntentFilter(app.packageName), Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(this, IntentFilter(app.packageName))
        }
    }
}

val stateFlowToKey by lazy { WeakHashMap<StateFlow<*>, String>() }

private inline fun <reified T> createStorageFlow(
    key: String,
    crossinline init: () -> T,
): StateFlow<T> {
    val stateFlow =
        MutableStateFlow(kv.getString(key, null)?.let { Singleton.json.decodeFromString<T>(it) }
            ?: init())
    onReceives.add { _, intent ->
        val extras = intent?.extras ?: return@add
        val type = extras.getString("type") ?: return@add
        val itKey = extras.getString("key") ?: return@add
        if (type == "update_storage" && itKey == key) {
            stateFlow.value =
                kv.getString(key, null)?.let { Singleton.json.decodeFromString<T>(it) } ?: init()
        }
    }
    stateFlowToKey[stateFlow] = key
    return stateFlow
}


fun sendStorageBroadcast(key: String) {
    app.sendBroadcast(Intent(app.packageName).apply {
        `package` = app.packageName
        putExtra("type", "update_storage")
        putExtra("key", key)
    })
}

inline fun <reified T> updateStorage(stateFlow: StateFlow<T>, newState: T) {
    if (stateFlow.value == newState) return
    val key = stateFlowToKey[stateFlow] ?: error("not found stateFlow key")
    kv.encode(key, Singleton.json.encodeToString(newState))
    sendStorageBroadcast(key)
}


@Serializable
data class Store(
    val enableService: Boolean = true,
    val excludeFromRecents: Boolean = false,
    val captureScreenshot: Boolean = false,
    val httpServerPort: Int = 8888,
    val updateSubsInterval: Long = 6 * 60 * 60_000L,
    val captureVolumeChange: Boolean = false,
    val autoCheckAppUpdate: Boolean = true,
    val toastWhenClick: Boolean = true,
    val clickToast: String = "跳过",
    val autoClearMemorySubs: Boolean = true,
    val hideSnapshotStatusBar: Boolean = false,
    val enableShizuku: Boolean = false,
    val log2FileSwitch: Boolean = true,
    val enableDarkTheme: Boolean? = null,
    val enableAbFloatWindow: Boolean = true,
    val enableGroup: Boolean? = null,
)

val storeFlow by lazy {
    createStorageFlow("store-v2") { Store() }
}

@Serializable
data class RecordStore(
    val clickCount: Int = 0,
)

val recordStoreFlow by lazy {
    createStorageFlow("record_store-v2") { RecordStore() }
}

val clickCountFlow by lazy {
    recordStoreFlow.map(appScope) { r -> r.clickCount }
}

fun increaseClickCount(n: Int = 1) {
    updateStorage(
        recordStoreFlow,
        recordStoreFlow.value.copy(clickCount = recordStoreFlow.value.clickCount + n)
    )
}

fun initStore() {
    receiver
    storeFlow.value
    recordStoreFlow.value
    appScope.launchTry(Dispatchers.IO) {
        storeFlow.map(appScope) { s -> s.log2FileSwitch }.collect {
            LogUtils.getConfig().isLog2FileSwitch = it
        }
    }
}

