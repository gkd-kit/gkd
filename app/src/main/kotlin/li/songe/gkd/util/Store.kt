package li.songe.gkd.util

import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import li.songe.gkd.appScope

private inline fun <reified T> createStorageFlow(
    key: String,
    crossinline init: () -> T,
): StateFlow<T> {
    val str = kv.getString(key, null)
    val initValue = if (str != null) {
        try {
            json.decodeFromString<T>(str)
        } catch (e: Exception) {
            e.printStackTrace()
            LogUtils.d(e)
            null
        }
    } else {
        null
    }
    val stateFlow = MutableStateFlow(initValue ?: init())
    appScope.launch {
        stateFlow.drop(1).collect {
            withContext(Dispatchers.IO) {
                kv.encode(key, json.encodeToString(it))
            }
        }
    }
    return stateFlow
}

fun <T> updateStorage(stateFlow: StateFlow<T>, newState: T) {
    (stateFlow as MutableStateFlow).value = newState
}

@Serializable
data class Store(
    val enableService: Boolean = true,
    val enableStatusService: Boolean = true,
    val excludeFromRecents: Boolean = false,
    val captureScreenshot: Boolean = false,
    val httpServerPort: Int = 8888,
    val updateSubsInterval: Long = 6 * 60 * 60_000L,
    val captureVolumeChange: Boolean = false,
    val autoCheckAppUpdate: Boolean = true,
    val toastWhenClick: Boolean = true,
    val clickToast: String = "GKD",
    val autoClearMemorySubs: Boolean = true,
    val hideSnapshotStatusBar: Boolean = false,
    val enableShizuku: Boolean = false,
    val log2FileSwitch: Boolean = true,
    val enableDarkTheme: Boolean? = null,
    val enableAbFloatWindow: Boolean = true,
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

private val log2FileSwitchFlow by lazy { storeFlow.map(appScope) { s -> s.log2FileSwitch } }

fun increaseClickCount(n: Int = 1) {
    updateStorage(
        recordStoreFlow,
        recordStoreFlow.value.copy(clickCount = recordStoreFlow.value.clickCount + n)
    )
}

fun initStore() {
    storeFlow.value
    recordStoreFlow.value
    appScope.launchTry(Dispatchers.IO) {
        log2FileSwitchFlow.collect {
            LogUtils.getConfig().isLog2FileSwitch = it
        }
    }
}

