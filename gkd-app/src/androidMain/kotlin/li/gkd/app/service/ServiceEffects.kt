package li.gkd.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.notif.StopServiceReceiver
import li.gkd.app.resources.Res
import li.gkd.app.resources.service_started
import li.gkd.app.resources.service_stopped
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.ToastUtils
import li.songe.codeorigin.CallSite

fun LifecycleHookService.useServicePresence(
    stateFlow: MutableStateFlow<Boolean>,
    name: String,
    startToastDelayMillis: Long = 0L,
    @CallSite loc: String = "",
) {
    onCreated {
        stateFlow.value = true
        ToastUtils.show(
            Res.string.service_started.getSync(name),
            delayMillis = startToastDelayMillis,
            loc = loc
        )
    }
    onDestroyed(loc = loc) {
        stateFlow.value = false
        ToastUtils.show(Res.string.service_stopped.getSync(name), loc = loc)
    }
}

fun LifecycleHookService.useStopServiceReceiver(
    @CallSite loc: String = "",
) {
    val receiver = StopServiceReceiver(this)
    onCreated { receiver.register() }
    onDestroyed(loc = loc) { receiver.close() }
}
