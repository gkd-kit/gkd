package li.songe.gkd.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.songe.gkd.META
import li.songe.gkd.appScope

private fun readStoreText(name: String, private: Boolean): String? {
    return if (private) {
        privateStoreFolder
    } else {
        storeFolder
    }.resolve(name).run {
        if (exists()) {
            readText()
        } else {
            null
        }
    }
}

private fun writeStoreText(name: String, text: String, private: Boolean) {
    if (private) {
        privateStoreFolder
    } else {
        storeFolder
    }.resolve(name).writeText(text)
}

private fun <T> createTextFlow(
    key: String,
    decode: (String?) -> T,
    encode: (T) -> String,
    private: Boolean = false,
): MutableStateFlow<T> {
    val name = if (key.contains('.')) {
        key
    } else {
        "$key.txt"
    }
    val initText = readStoreText(name, private)
    val initValue = decode(initText)
    val stateFlow = MutableStateFlow(initValue)
    appScope.launch {
        stateFlow.drop(1).collect {
            withContext(Dispatchers.IO) {
                writeStoreText(name, encode(it), private)
            }
        }
    }
    return stateFlow
}

private inline fun <reified T : Any> createAnyFlow(
    key: String,
    crossinline default: () -> T,
    crossinline transform: (T) -> T = { it },
    private: Boolean = false,
): MutableStateFlow<T> {
    return createTextFlow(
        key = "$key.json",
        decode = {
            val initValue = it?.let {
                runCatching { json.decodeFromString<T>(it) }.getOrNull()
            }
            transform(initValue ?: default())
        },
        encode = {
            json.encodeToString(it)
        },
        private = private,
    )
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
)

val storeFlow by lazy {
    createAnyFlow(
        key = "store",
        default = { Store() }
    )
}

@Serializable
data class ShizukuStore(
    val versionCode: Int = META.versionCode,
    val enableActivity: Boolean = false,
    val enableTapClick: Boolean = false,
    val enableWorkProfile: Boolean = false,
) {
    val enableShizukuAnyFeat: Boolean
        get() = enableActivity || enableTapClick || enableWorkProfile
}

val shizukuStoreFlow by lazy {
    createAnyFlow(
        key = "shizuku",
        default = { ShizukuStore() },
        transform = {
            if (it.versionCode != META.versionCode) {
                ShizukuStore()
            } else {
                it
            }
        }
    )
}

val actionCountFlow by lazy {
    createTextFlow(
        "action_count",
        decode = {
            it?.toLongOrNull() ?: 0L
        },
        encode = {
            it.toString()
        },
    )
}

@Serializable
data class PrivacyStore(
    val githubCookie: String = "",
)

val privacyStoreFlow by lazy {
    createAnyFlow(
        key = "privacy_store",
        default = { PrivacyStore() },
        private = true,
    )
}

val termsAcceptedFlow by lazy {
    createTextFlow(
        "terms_accepted",
        decode = {
            it == "true"
        },
        encode = {
            it.toString()
        },
    )
}

fun initStore() {
    storeFlow.value
    shizukuStoreFlow.value
    actionCountFlow.value
}

fun switchStoreEnableMatch() {
    if (storeFlow.value.enableMatch) {
        toast("暂停规则匹配")
    } else {
        toast("开启规则匹配")
    }
    storeFlow.update { it.copy(enableMatch = !it.enableMatch) }
}
