package li.songe.gkd.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Parcelable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.parcelize.Parcelize
import li.songe.gkd.app
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

private val stateFlowToKey by lazy { WeakHashMap<StateFlow<*>, String>() }

private inline fun <reified T : Parcelable> createStorageFlow(
    key: String,
    crossinline init: () -> T,
): StateFlow<T> {
    receiver
    val stateFlow = MutableStateFlow(kv.decodeParcelable(key, T::class.java) ?: init())
    onReceives.add { _, intent ->
        val extras = intent?.extras ?: return@add
        val type = extras.getString("type") ?: return@add
        val itKey = extras.getString("key") ?: return@add
        if (type == "update_storage" && itKey == key) {
            stateFlow.value = kv.decodeParcelable(key, T::class.java) ?: init()
        }
    }
    stateFlowToKey[stateFlow] = key
    return stateFlow
}


fun <T : Parcelable> updateStorage(stateFlow: StateFlow<T>, newState: T) {
    val key = stateFlowToKey[stateFlow] ?: error("not found stateFlow key")
    kv.encode(key, newState)
    app.sendBroadcast(Intent(app.packageName).apply {
        `package` = app.packageName
        putExtra("type", "update_storage")
        putExtra("key", key)
    })
}


/**
 * 属性不可删除,注释弃用即可
 * 属性声明顺序不可变动
 * 新增属性必须在尾部声明
 * 否则导致序列化错误
 */
@Parcelize
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
) : Parcelable

val storeFlow by lazy {
    createStorageFlow("store") { Store() }
}

@Parcelize
data class RecordStore(
    val clickCount: Int = 0,
    val snapshotIdMap: Map<Long, Int> = emptyMap(),
) : Parcelable

val recordStoreFlow by lazy {
    createStorageFlow("record_store") { RecordStore() }
}

val clickCountFlow by lazy {
    recordStoreFlow.map { r -> r.clickCount }
}

fun increaseClickCount(n: Int = 1) {
    updateStorage(
        recordStoreFlow,
        recordStoreFlow.value.copy(clickCount = recordStoreFlow.value.clickCount + n)
    )
}

fun initStore() {
    storeFlow.value
    recordStoreFlow.value
}

