package li.songe.gkd.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import li.songe.gkd.appScope
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.toast

val storeFlow by lazy {
    createAnyFlow(
        key = "store",
        default = { SettingsStore() }
    )
}

val actionCountFlow by lazy {
    createTextFlow(
        key = "action_count",
        decode = { it?.toLongOrNull() ?: 0L },
        encode = { it.toString() },
    )
}

fun initStore() = appScope.launchTry(Dispatchers.IO) {
    // preload
    storeFlow.value
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
