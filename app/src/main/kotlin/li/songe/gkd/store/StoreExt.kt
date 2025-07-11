package li.songe.gkd.store

import kotlinx.coroutines.flow.update
import li.songe.gkd.META
import li.songe.gkd.util.toast

val storeFlow by lazy {
    createAnyFlow(
        key = "store",
        default = { SettingsStore() }
    )
}

val shizukuStoreFlow by lazy {
    createAnyFlow(
        key = "shizuku",
        default = { ShizukuStore() },
        initialize = {
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
        key = "action_count",
        decode = { it?.toLongOrNull() ?: 0L },
        encode = { it.toString() },
    )
}

fun initStore() {
    // preload
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
