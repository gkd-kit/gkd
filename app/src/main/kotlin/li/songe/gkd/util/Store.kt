package li.songe.gkd.util

import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.songe.gkd.META
import li.songe.gkd.appScope

private inline fun <reified T> createJsonFlow(
    key: String,
    crossinline default: () -> T,
    crossinline transform: (T) -> T = { it }
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
    val stateFlow = MutableStateFlow(transform(initValue ?: default()))
    appScope.launch {
        stateFlow.drop(1).collect {
            withContext(Dispatchers.IO) {
                kv.encode(key, json.encodeToString(it))
            }
        }
    }
    return stateFlow
}

private fun createLongFlow(
    key: String,
    default: Long = 0,
    transform: (Long) -> Long = { it }
): MutableStateFlow<Long> {
    val stateFlow = MutableStateFlow(transform(kv.getLong(key, default)))
    appScope.launch {
        stateFlow.drop(1).collect {
            withContext(Dispatchers.IO) { kv.encode(key, it) }
        }
    }
    return stateFlow
}

@Serializable
data class Store(
    val enableService: Boolean = true,
    val enableMatch: Boolean = true,
    val enableStatusService: Boolean = true,
    val excludeFromRecents: Boolean = false,
    val captureScreenshot: Boolean = false,
    val httpServerPort: Int = 8888,
    val updateSubsInterval: Long = UpdateTimeOption.Everyday.value,
    val captureVolumeChange: Boolean = false,
    val autoCheckAppUpdate: Boolean = META.updateEnabled,
    val toastWhenClick: Boolean = true,
    val clickToast: String = "GKD",
    val autoClearMemorySubs: Boolean = true,
    val hideSnapshotStatusBar: Boolean = false,
    val enableShizukuActivity: Boolean = false,
    val enableShizukuClick: Boolean = false,
    val enableShizukuWorkProfile: Boolean = false,
    val log2FileSwitch: Boolean = true,
    val enableDarkTheme: Boolean? = null,
    val enableDynamicColor: Boolean = true,
    val enableAbFloatWindow: Boolean = true,
    val showSaveSnapshotToast: Boolean = true,
    val useSystemToast: Boolean = false,
    val useCustomNotifText: Boolean = false,
    val customNotifText: String = "\${i}全局/\${k}应用/\${u}规则组/\${n}触发",
    val enableActivityLog: Boolean = false,
    val updateChannel: Int = if (META.versionName.contains("beta")) UpdateChannelOption.Beta.value else UpdateChannelOption.Stable.value,
    val sortType: Int = SortTypeOption.SortByName.value,
    val showSystemApp: Boolean = true,
    val showHiddenApp: Boolean = false,
    val appRuleSortType: Int = RuleSortOption.Default.value,
    val appShowInnerDisable: Boolean = false,
    val subsAppSortType: Int = SortTypeOption.SortByName.value,
    val subsAppShowUninstallApp: Boolean = false,
    val subsExcludeSortType: Int = SortTypeOption.SortByName.value,
    val subsExcludeShowSystemApp: Boolean = true,
    val subsExcludeShowHiddenApp: Boolean = false,
    val subsExcludeShowDisabledApp: Boolean = false,
    val subsPowerWarn: Boolean = true,
) {
    val enableShizukuAnyFeat by lazy {
        enableShizukuActivity || enableShizukuClick || enableShizukuWorkProfile
    }
}

val storeFlow by lazy {
    createJsonFlow(
        key = "store-v2",
        default = { Store() },
        transform = {
            if (UpdateTimeOption.allSubObject.all { e -> e.value != it.updateSubsInterval }) {
                it.copy(
                    updateSubsInterval = UpdateTimeOption.Everyday.value
                )
            } else {
                it
            }
        }
    )
}

//@Deprecated("use actionCountFlow instead")
@Serializable
private data class RecordStore(
    val clickCount: Int = 0,
)

//@Deprecated("use actionCountFlow instead")
private val recordStoreFlow by lazy {
    createJsonFlow(
        key = "record_store-v2",
        default = { RecordStore() }
    )
}

val actionCountFlow by lazy {
    createLongFlow(
        key = "action_count",
        transform = {
            if (it == 0L) {
                recordStoreFlow.value.clickCount.toLong()
            } else {
                it
            }
        }
    )
}

@Serializable
data class PrivacyStore(
    val githubCookie: String? = null,
)

val privacyStoreFlow by lazy {
    createJsonFlow(
        key = "privacy_store",
        default = { PrivacyStore() }
    )
}

fun initStore() {
    storeFlow.value
    actionCountFlow.value
}
