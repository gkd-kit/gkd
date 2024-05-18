package li.songe.gkd.util

import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import li.songe.gkd.appScope

private inline fun <reified T> createStorageFlow(
    key: String,
    crossinline init: () -> T,
): MutableStateFlow<T> {
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

@Serializable
data class Store(
    val enableService: Boolean = true,
    val enableStatusService: Boolean = true,
    val excludeFromRecents: Boolean = false,
    val captureScreenshot: Boolean = false,
    val httpServerPort: Int = 8888,
    val updateSubsInterval: Long = UpdateTimeOption.Everyday.value,
    val captureVolumeChange: Boolean = false,
    val autoCheckAppUpdate: Boolean = true,
    val toastWhenClick: Boolean = true,
    val clickToast: String = "GKD",
    val autoClearMemorySubs: Boolean = true,
    val hideSnapshotStatusBar: Boolean = false,
    val enableShizukuActivity: Boolean = false,
    val enableShizukuClick: Boolean = false,
    val log2FileSwitch: Boolean = true,
    val enableDarkTheme: Boolean? = null,
    val enableDynamicColor: Boolean = true,
    val enableAbFloatWindow: Boolean = true,
    val sortType: Int = SortTypeOption.SortByName.value,
    val showSystemApp: Boolean = true,
    val showHiddenApp: Boolean = false,
)

val storeFlow by lazy {
    createStorageFlow("store-v2") { Store() }.apply {
        if (UpdateTimeOption.allSubObject.all { it.value != value.updateSubsInterval }) {
            update {
                it.copy(
                    updateSubsInterval = UpdateTimeOption.Everyday.value
                )
            }
        }
    }
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
    recordStoreFlow.update {
        it.copy(
            clickCount = it.clickCount + n
        )
    }
}

fun initStore() {
    storeFlow.value
    recordStoreFlow.value
}

